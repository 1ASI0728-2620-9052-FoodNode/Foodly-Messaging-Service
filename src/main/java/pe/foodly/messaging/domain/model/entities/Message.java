package pe.foodly.messaging.domain.model.entities;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import pe.foodly.messaging.domain.model.enums.SenderType;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;

/**
 * Mensaje entrante (del explorador) o saliente (de Lía) (5.6.1).
 *
 * <p>Es inmutable. Los mensajes se agregan solo a través de {@code Conversation}; el constructor
 * público existe para reconstruirlos desde la persistencia.
 *
 * <p>{@code huariqueId} es opcional (ver DEVIATIONS.md): la tabla {@code messages} tiene
 * {@code huarique_id}.
 */
public final class Message {

    private final MessageId id;
    private final SenderType sender;
    private final MessageContent content;
    private final UserIntent intent;
    private final HuariqueId huariqueId;
    private final ReservationId reservationId;
    private final LocalDateTime sentAt;
    private final MessageId inReplyToMessageId;

    public Message(
            MessageId id,
            SenderType sender,
            MessageContent content,
            UserIntent intent,
            HuariqueId huariqueId,
            ReservationId reservationId,
            LocalDateTime sentAt) {
        this(id, sender, content, intent, huariqueId, reservationId, sentAt, null);
    }

    /**
     * @param inReplyToMessageId en una respuesta de Lía, el mensaje del explorador al que responde
     *     (correlación del callback, ver DEVIATIONS.md); {@code null} en el resto
     */
    public Message(
            MessageId id,
            SenderType sender,
            MessageContent content,
            UserIntent intent,
            HuariqueId huariqueId,
            ReservationId reservationId,
            LocalDateTime sentAt,
            MessageId inReplyToMessageId) {
        this.inReplyToMessageId = inReplyToMessageId;
        this.id = Objects.requireNonNull(id, "El id del mensaje es obligatorio.");
        this.sender = Objects.requireNonNull(sender, "El emisor es obligatorio.");
        this.content = Objects.requireNonNull(content, "El contenido es obligatorio.");
        this.sentAt = Objects.requireNonNull(sentAt, "La fecha de envío es obligatoria.");
        this.intent = intent;
        this.huariqueId = huariqueId;
        this.reservationId = reservationId;
    }

    public boolean isFromLia() {
        return sender == SenderType.LIA;
    }

    public boolean hasReservation() {
        return reservationId != null;
    }

    /** Copia de este mensaje vinculada a la reserva. Solo la usa {@code Conversation.linkReservation}. */
    public Message withReservation(ReservationId reservation) {
        Objects.requireNonNull(reservation, "La reserva es obligatoria.");
        return new Message(id, sender, content, intent, huariqueId, reservation, sentAt, inReplyToMessageId);
    }

    public MessageId getId() {
        return id;
    }

    public SenderType getSender() {
        return sender;
    }

    public MessageContent getContent() {
        return content;
    }

    /** Intención detectada; vacía en los mensajes del explorador y en las respuestas sin interpretación. */
    public Optional<UserIntent> getIntent() {
        return Optional.ofNullable(intent);
    }

    public Optional<HuariqueId> getHuariqueId() {
        return Optional.ofNullable(huariqueId);
    }

    public Optional<ReservationId> getReservationId() {
        return Optional.ofNullable(reservationId);
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    /** Mensaje del explorador al que responde esta respuesta de Lía, si se registró la correlación. */
    public Optional<MessageId> getInReplyToMessageId() {
        return Optional.ofNullable(inReplyToMessageId);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Message other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
