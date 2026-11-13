package pe.foodly.messaging.domain.model.aggregates;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import pe.foodly.messaging.domain.exceptions.ConversationClosedException;
import pe.foodly.messaging.domain.exceptions.MessageNotFoundException;
import pe.foodly.messaging.domain.exceptions.ReservationAlreadyLinkedException;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.enums.ConversationStatus;
import pe.foodly.messaging.domain.model.enums.SenderType;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;

/**
 * Conversación de un explorador con Lía (Aggregate Root, 5.6.1; en el texto del informe, «Sesiones de chat»).
 *
 * <p>Es el único punto de entrada para agregar mensajes: solo una conversación {@code ACTIVE} los
 * acepta, y el historial queda en orden cronológico (la fecha de cada mensaje nunca es anterior a la
 * del anterior, aunque el reloj retroceda).
 */
public class Conversation {

    private final ConversationId id;
    private final ExplorerId explorerId;
    private ConversationStatus status;
    private final LocalDateTime startedAt;
    private LocalDateTime lastMessageAt;
    private final List<Message> messages;
    private final Clock clock;

    private Conversation(
            ConversationId id,
            ExplorerId explorerId,
            ConversationStatus status,
            LocalDateTime startedAt,
            LocalDateTime lastMessageAt,
            List<Message> messages,
            Clock clock) {
        this.id = Objects.requireNonNull(id, "El id de la conversación es obligatorio.");
        this.explorerId = Objects.requireNonNull(explorerId, "El explorador es obligatorio.");
        this.status = Objects.requireNonNull(status, "El estado es obligatorio.");
        this.startedAt = Objects.requireNonNull(startedAt, "La fecha de inicio es obligatoria.");
        this.lastMessageAt = Objects.requireNonNull(lastMessageAt, "La fecha del último mensaje es obligatoria.");
        this.messages = messages;
        this.clock = Objects.requireNonNull(clock, "El reloj es obligatorio.");
    }

    /** Inicia una conversación ACTIVE sin mensajes. */
    public static Conversation start(ExplorerId explorerId) {
        return start(explorerId, Clock.systemDefaultZone());
    }

    public static Conversation start(ExplorerId explorerId, Clock clock) {
        Objects.requireNonNull(clock, "El reloj es obligatorio.");
        LocalDateTime now = LocalDateTime.now(clock);
        return new Conversation(
                ConversationId.newId(), explorerId, ConversationStatus.ACTIVE, now, now, new ArrayList<>(), clock);
    }

    /** Reconstruye una conversación existente (para la persistencia). Ordena los mensajes por fecha de envío. */
    public static Conversation restore(
            ConversationId id,
            ExplorerId explorerId,
            ConversationStatus status,
            LocalDateTime startedAt,
            LocalDateTime lastMessageAt,
            List<Message> messages) {
        return restore(id, explorerId, status, startedAt, lastMessageAt, messages, Clock.systemDefaultZone());
    }

    public static Conversation restore(
            ConversationId id,
            ExplorerId explorerId,
            ConversationStatus status,
            LocalDateTime startedAt,
            LocalDateTime lastMessageAt,
            List<Message> messages,
            Clock clock) {
        Objects.requireNonNull(messages, "Los mensajes son obligatorios.");
        List<Message> ordered = new ArrayList<>(messages);
        ordered.sort(Comparator.comparing(Message::getSentAt)); // estable: respeta el orden de los empates
        return new Conversation(id, explorerId, status, startedAt, lastMessageAt, ordered, clock);
    }

    public Message addExplorerMessage(MessageContent content) {
        requireActive();
        Objects.requireNonNull(content, "El contenido es obligatorio.");
        Message message = new Message(
                MessageId.newId(), SenderType.EXPLORER, content, null, null, null, nextSentAt());
        append(message);
        return message;
    }

    /**
     * Agrega la respuesta de Lía. Si la intención referencia un huarique, el mensaje lo hereda como
     * {@code huariqueId}. La intención puede ser {@code null} (por ejemplo, el aviso de timeout).
     */
    public Message addLiaReply(MessageContent content, UserIntent intent) {
        return addLiaReply(content, intent, null);
    }

    /**
     * Igual que {@link #addLiaReply(MessageContent, UserIntent)} y registra a qué mensaje del explorador
     * responde Lía ({@code inReplyToMessageId}, la correlación del callback). Ver DEVIATIONS.md.
     */
    public Message addLiaReply(MessageContent content, UserIntent intent, MessageId inReplyToMessageId) {
        requireActive();
        Objects.requireNonNull(content, "El contenido es obligatorio.");
        Message message = new Message(
                MessageId.newId(),
                SenderType.LIA,
                content,
                intent,
                intent == null ? null : intent.huariqueId(),
                null,
                nextSentAt(),
                inReplyToMessageId);
        append(message);
        return message;
    }

    /**
     * Vincula la reserva creada por Reservation al mensaje que la originó. Es idempotente para la misma
     * reserva y no exige que la conversación esté activa: la reserva ya existe en Reservation y no
     * debe perderse la referencia.
     *
     * @throws MessageNotFoundException si el mensaje no pertenece a esta conversación
     * @throws ReservationAlreadyLinkedException si el mensaje ya tiene otra reserva
     */
    public void linkReservation(MessageId messageId, ReservationId reservationId) {
        Objects.requireNonNull(messageId, "El id del mensaje es obligatorio.");
        Objects.requireNonNull(reservationId, "La reserva es obligatoria.");
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (!message.getId().equals(messageId)) {
                continue;
            }
            if (message.getReservationId().isPresent()) {
                if (message.getReservationId().get().equals(reservationId)) {
                    return;
                }
                throw new ReservationAlreadyLinkedException(messageId, message.getReservationId().get());
            }
            messages.set(i, message.withReservation(reservationId));
            return;
        }
        throw new MessageNotFoundException(id, messageId);
    }

    /** Cierra la conversación. Es idempotente. */
    public void close() {
        status = ConversationStatus.CLOSED;
    }

    public boolean isActive() {
        return status == ConversationStatus.ACTIVE;
    }

    public ConversationId getId() {
        return id;
    }

    public ExplorerId getExplorerId() {
        return explorerId;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    /** Vista de solo lectura del historial, en orden cronológico. */
    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    private void requireActive() {
        if (!isActive()) {
            throw new ConversationClosedException(id);
        }
    }

    /** Hora actual, sin retroceder respecto del último mensaje, para mantener el orden cronológico. */
    private LocalDateTime nextSentAt() {
        LocalDateTime now = LocalDateTime.now(clock);
        return now.isBefore(lastMessageAt) ? lastMessageAt : now;
    }

    private void append(Message message) {
        messages.add(message);
        lastMessageAt = message.getSentAt();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Conversation other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
