package pe.foodly.messaging.application.commandhandlers;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import pe.foodly.messaging.application.MessagingSettings;
import pe.foodly.messaging.application.commands.RegisterLiaTimeoutCommand;
import pe.foodly.messaging.application.events.LiaReplyRegisteredEvent;
import pe.foodly.messaging.application.outboundports.IExplorerTokenCache;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.repositories.IConversationRepository;

/**
 * Registra el error de «Lía no respondió a tiempo» y guarda una respuesta de Lía que ofrece al explorador
 * la reserva manual (RF-15), con {@code inReplyToMessageId}, para que la vea en el historial y por STOMP.
 *
 * <p>Es idempotente y segura frente al callback: no hace nada si el mensaje ya tiene respuesta, si la
 * conversación ya no está activa o si el callback lo está procesando en ese momento. Antes de insertar el
 * aviso vuelve a verificar que no exista respuesta para ese {@code inReplyToMessageId}.
 */
@Component
public class RegisterLiaTimeoutCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(RegisterLiaTimeoutCommandHandler.class);

    private final IConversationRepository conversations;
    private final IExplorerTokenCache tokenCache;
    private final TransactionOperations transactions;
    private final ApplicationEventPublisher events;
    private final ReplyInFlightRegistry inFlight;
    private final LiaReplyComposer composer;
    private final int maxLength;

    public RegisterLiaTimeoutCommandHandler(
            IConversationRepository conversations,
            IExplorerTokenCache tokenCache,
            TransactionOperations transactions,
            ApplicationEventPublisher events,
            ReplyInFlightRegistry inFlight,
            MessagingSettings settings) {
        this.conversations = conversations;
        this.tokenCache = tokenCache;
        this.transactions = transactions;
        this.events = events;
        this.inFlight = inFlight;
        this.composer = new LiaReplyComposer(settings.maxMessageLength());
        this.maxLength = settings.maxMessageLength();
    }

    /** @return el aviso registrado, o vacío si no hizo falta (ya respondido, cerrada o en proceso) */
    public Optional<Message> handle(RegisterLiaTimeoutCommand command) {
        MessageId replyTo = command.inReplyToMessageId();
        if (!inFlight.tryAcquire(replyTo)) {
            log.debug("Timeout de Lía omitido: el mensaje {} se está respondiendo.", replyTo);
            return Optional.empty();
        }
        try {
            Optional<Message> notice = Optional.ofNullable(transactions.execute(status -> register(command)));
            notice.ifPresent(message -> tokenCache.evict(replyTo));
            return notice;
        } finally {
            inFlight.release(replyTo);
        }
    }

    private Message register(RegisterLiaTimeoutCommand command) {
        MessageId replyTo = command.inReplyToMessageId();
        Optional<Conversation> found = conversations.findById(command.conversationId());
        if (found.isEmpty()) {
            log.warn("Timeout de Lía omitido: la conversación {} no existe.", command.conversationId());
            return null;
        }
        Conversation conversation = found.get();
        if (!conversation.isActive()) {
            log.debug("Timeout de Lía omitido: la conversación {} está cerrada.", conversation.getId());
            return null;
        }
        boolean isExplorerMessage = conversation.getMessages().stream()
                .anyMatch(m -> m.getId().equals(replyTo) && !m.isFromLia());
        boolean alreadyReplied = conversation.getMessages().stream()
                .anyMatch(m -> m.isFromLia() && m.getInReplyToMessageId().filter(replyTo::equals).isPresent());
        if (!isExplorerMessage || alreadyReplied) {
            return null;
        }

        log.error("Lía no respondió a tiempo al mensaje {} de la conversación {}: se ofrece la reserva manual.",
                replyTo, conversation.getId());
        Message notice = conversation.addLiaReply(MessageContent.of(composer.timeout(), maxLength), null, replyTo);
        conversations.save(conversation);
        events.publishEvent(new LiaReplyRegisteredEvent(conversation.getId(), notice));
        return notice;
    }
}
