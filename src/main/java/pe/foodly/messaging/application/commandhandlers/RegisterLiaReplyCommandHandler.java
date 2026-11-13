package pe.foodly.messaging.application.commandhandlers;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import pe.foodly.messaging.application.events.LiaReplyRegisteredEvent;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.MessagingSettings;
import pe.foodly.messaging.application.commandhandlers.LiaReplyComposer.Recommended;
import pe.foodly.messaging.application.commands.RegisterLiaReplyCommand;
import pe.foodly.messaging.application.commands.RequestedReservation;
import pe.foodly.messaging.application.exceptions.ConversationNotFoundException;
import pe.foodly.messaging.application.exceptions.ProviderUnavailableException;
import pe.foodly.messaging.application.exceptions.ReservationRejectedException;
import pe.foodly.messaging.application.outboundports.HuariqueDetails;
import pe.foodly.messaging.application.outboundports.IBusinessClient;
import pe.foodly.messaging.application.outboundports.IExplorerTokenCache;
import pe.foodly.messaging.application.outboundports.IRadarClient;
import pe.foodly.messaging.application.outboundports.IReservationClient;
import pe.foodly.messaging.application.outboundports.MenuItem;
import pe.foodly.messaging.application.outboundports.NearbyHuarique;
import pe.foodly.messaging.application.outboundports.ReservationConfirmation;
import pe.foodly.messaging.domain.exceptions.ConversationClosedException;
import pe.foodly.messaging.domain.exceptions.MessageNotFoundException;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.enums.IntentType;
import pe.foodly.messaging.domain.model.enums.RoutingDecision;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;
import pe.foodly.messaging.domain.repositories.IConversationRepository;
import pe.foodly.messaging.domain.services.IntentRoutingService;

/**
 * Registra la respuesta de Lía que llega por el callback (5.6.3). Decide con {@link IntentRoutingService}
 * y, según la decisión, consulta a Radar, consulta a Business, reenvía la reserva a Reservation o pide
 * una aclaración; luego agrega la respuesta a la conversación.
 *
 * <ul>
 *   <li><b>Idempotente</b> por {@code inReplyToMessageId}: si ya existe una respuesta de Lía para ese
 *       mensaje (incluido el aviso de timeout) se ignora con un {@code warn} y no se llama a nadie.</li>
 *   <li><b>Sin transacción larga</b>: las llamadas HTTP se hacen fuera de toda transacción; la respuesta
 *       se persiste al final en una transacción corta que vuelve a leer la conversación.</li>
 *   <li><b>Degrada con amabilidad</b>: si un proveedor falla, se cae por timeout o el token del explorador
 *       ya no está en el cache, Lía responde que no pudo completar la acción y, si era una reserva, ofrece
 *       la reserva manual (RF-15).</li>
 * </ul>
 *
 * <p>Se asume una sola instancia (DD-10): la protección contra callbacks simultáneos del mismo mensaje
 * (y contra el aviso de timeout, que usa el mismo {@link ReplyInFlightRegistry}) es local al proceso.
 */
@Component
public class RegisterLiaReplyCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(RegisterLiaReplyCommandHandler.class);

    /** Cantidad de huariques de Radar de los que se pide el nombre a Business. */
    static final int MAX_RECOMMENDATIONS = 3;

    private final IConversationRepository conversations;
    private final IntentRoutingService routing;
    private final IRadarClient radar;
    private final IBusinessClient business;
    private final IReservationClient reservations;
    private final IExplorerTokenCache tokenCache;
    private final TransactionOperations transactions;
    private final MessagingSettings settings;
    private final ApplicationEventPublisher events;
    private final LiaReplyComposer composer;
    private final ReplyInFlightRegistry inFlight;

    public RegisterLiaReplyCommandHandler(
            IConversationRepository conversations,
            IntentRoutingService routing,
            IRadarClient radar,
            IBusinessClient business,
            IReservationClient reservations,
            IExplorerTokenCache tokenCache,
            TransactionOperations transactions,
            MessagingSettings settings,
            ApplicationEventPublisher events,
            ReplyInFlightRegistry inFlight) {
        this.events = events;
        this.inFlight = inFlight;
        this.conversations = conversations;
        this.routing = routing;
        this.radar = radar;
        this.business = business;
        this.reservations = reservations;
        this.tokenCache = tokenCache;
        this.transactions = transactions;
        this.settings = settings;
        this.composer = new LiaReplyComposer(settings.maxMessageLength());
    }

    /**
     * @return la respuesta registrada, o vacío si el callback se ignoró por ya estar respondido
     * @throws ConversationNotFoundException si la conversación no existe
     * @throws MessageNotFoundException si el mensaje al que responde no es un mensaje del explorador de esa conversación
     * @throws ConversationClosedException si la conversación está cerrada (no se llama a ningún proveedor)
     */
    public Optional<Message> handle(RegisterLiaReplyCommand command) {
        MessageId replyTo = command.inReplyToMessageId();
        if (!inFlight.tryAcquire(replyTo)) {
            log.warn("Callback de Lía ignorado: el mensaje {} ya se está procesando.", replyTo);
            return Optional.empty();
        }
        try {
            Conversation conversation = conversations.findById(command.conversationId())
                    .orElseThrow(() -> new ConversationNotFoundException(command.conversationId()));
            if (hasReplyTo(conversation, replyTo)) {
                log.warn("Callback de Lía ignorado: el mensaje {} ya tiene una respuesta.", replyTo);
                return Optional.empty();
            }
            if (conversation.getMessages().stream().noneMatch(m -> m.getId().equals(replyTo) && !m.isFromLia())) {
                throw new MessageNotFoundException(conversation.getId(), replyTo);
            }
            if (!conversation.isActive()) {
                throw new ConversationClosedException(conversation.getId());
            }

            UserIntent intent = effectiveIntent(command);
            Reply reply = resolve(command, intent);
            Optional<Message> registered = persist(command.conversationId(), replyTo, intent, reply);
            tokenCache.evict(replyTo);
            return registered;
        } finally {
            inFlight.release(replyTo);
        }
    }

    /** Qué responde Lía y, si se creó una reserva, su id para vincularla al mensaje del explorador. */
    private record Reply(String text, ReservationId reservationId) {
        static Reply of(String text) {
            return new Reply(text, null);
        }
    }

    private Reply resolve(RegisterLiaReplyCommand command, UserIntent intent) {
        RoutingDecision decision = routing.route(intent);
        if (decision == RoutingDecision.ASK_CLARIFICATION) {
            return Reply.of(composer.clarification(command.replyContent(), intent.missingContext()));
        }

        boolean wasReservation = intent.type() == IntentType.RESERVATION_REQUEST;
        Optional<ExplorerToken> token = tokenCache.find(command.inReplyToMessageId());
        if (token.isEmpty()) {
            log.warn("El token del explorador ya no está disponible para el mensaje {}.", command.inReplyToMessageId());
            return Reply.of(composer.failure(wasReservation));
        }

        try {
            return switch (decision) {
                case RECOMMEND_VIA_RADAR -> recommend(command.replyContent(), intent, token.get());
                case QUERY_BUSINESS -> queryBusiness(command.replyContent(), intent, token.get());
                case FORWARD_TO_RESERVATION -> reserve(command.replyContent(), intent, token.get());
                case ASK_CLARIFICATION -> throw new IllegalStateException("Resuelto antes.");
            };
        } catch (ProviderUnavailableException e) {
            log.warn("No se pudo atender el mensaje {}: {}", command.inReplyToMessageId(), e.getMessage());
            return Reply.of(composer.failure(wasReservation));
        } catch (ReservationRejectedException e) {
            log.warn("Reservation rechazó la solicitud del mensaje {}: {}", command.inReplyToMessageId(), e.getReason());
            return Reply.of(composer.reservationRejected(e.getReason()));
        }
    }

    private Reply recommend(String replyText, UserIntent intent, ExplorerToken token) {
        List<NearbyHuarique> nearby = radar.search(token, intent.location(), settings.radarKRing());
        List<Recommended> recommended = nearby.stream()
                .limit(MAX_RECOMMENDATIONS)
                .map(huarique -> business.findHuarique(token, huarique.huariqueId())
                        .map(details -> new Recommended(details.name(), huarique.distanceMeters())))
                .flatMap(Optional::stream)
                .toList();
        return Reply.of(composer.recommendations(replyText, recommended));
    }

    private Reply queryBusiness(String replyText, UserIntent intent, ExplorerToken token) {
        Optional<HuariqueDetails> details = business.findHuarique(token, intent.huariqueId());
        if (details.isEmpty()) {
            return Reply.of(composer.huariqueNotFound(replyText));
        }
        List<MenuItem> menu = business.findMenu(token, intent.huariqueId());
        return Reply.of(composer.huariqueInfo(replyText, details.get(), menu));
    }

    /** Messaging no valida horario ni capacidad: reenvía y deja que Reservation decida. */
    private Reply reserve(String replyText, UserIntent intent, ExplorerToken token) {
        ReservationConfirmation confirmation = reservations.request(
                token,
                intent.huariqueId(),
                intent.requestedAt().toLocalDate(),
                intent.requestedAt().toLocalTime(),
                intent.partySize());
        return new Reply(
                composer.reservationRequested(replyText, confirmation, intent.requestedAt(), intent.partySize()),
                confirmation.reservationId());
    }

    private Optional<Message> persist(ConversationId conversationId, MessageId replyTo, UserIntent intent, Reply reply) {
        try {
            return Optional.ofNullable(transactions.execute(status -> {
                Conversation conversation = conversations.findById(conversationId)
                        .orElseThrow(() -> new ConversationNotFoundException(conversationId));
                if (hasReplyTo(conversation, replyTo)) {
                    log.warn("Callback de Lía ignorado: el mensaje {} ya tiene una respuesta.", replyTo);
                    return null;
                }
                if (reply.reservationId() != null) {
                    conversation.linkReservation(replyTo, reply.reservationId());
                }
                String text = reply.text().isBlank() ? composer.failure(false) : reply.text();
                Message message = conversation.addLiaReply(
                        MessageContent.of(text, settings.maxMessageLength()), intent, replyTo);
                conversations.save(conversation);
                // Se atiende después del commit: así el cliente ya puede leer el mensaje con GET .../messages.
                events.publishEvent(new LiaReplyRegisteredEvent(conversationId, message));
                return message;
            }));
        } catch (RuntimeException e) {
            if (reply.reservationId() != null) {
                log.error("La reserva {} se creó en Reservation pero no se pudo registrar en la conversación {}.",
                        reply.reservationId(), conversationId, e);
            }
            throw e;
        }
    }

    private static boolean hasReplyTo(Conversation conversation, MessageId replyTo) {
        return conversation.getMessages().stream()
                .anyMatch(m -> m.isFromLia() && m.getInReplyToMessageId().filter(replyTo::equals).isPresent());
    }

    /** La intención del callback (o desconocida si no vino), completada con la reserva solicitada. */
    private static UserIntent effectiveIntent(RegisterLiaReplyCommand command) {
        UserIntent intent = command.intent() == null
                ? new UserIntent(IntentType.UNKNOWN, null, null, null, null, null, null)
                : command.intent();
        RequestedReservation requested = command.requestedReservation();
        if (requested == null || intent.type() != IntentType.RESERVATION_REQUEST) {
            return intent;
        }
        return new UserIntent(
                intent.type(),
                intent.cravings(),
                intent.budgetRange(),
                intent.location(),
                intent.partySize() != null ? intent.partySize() : requested.partySize(),
                intent.requestedAt() != null ? intent.requestedAt() : requested.requestedAt(),
                intent.huariqueId() != null ? intent.huariqueId() : requested.huariqueId());
    }
}
