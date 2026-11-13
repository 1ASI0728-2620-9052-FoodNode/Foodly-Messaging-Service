package pe.foodly.messaging.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Fila de {@code messages} (5.6.7.2). {@code intent_data} guarda la intención interpretada y los datos
 * de correlación (por ejemplo {@code inReplyToMessageId}) como JSON: no hay columnas adicionales.
 */
@Entity
@Table(name = "messages")
class MessageEntity {

    @Id
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ConversationEntity conversation;

    @Column(name = "sender", nullable = false, length = 10)
    private String sender;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "intent_type", length = 30)
    private String intentType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "intent_data", columnDefinition = "JSON")
    private String intentData;

    @Column(name = "huarique_id", columnDefinition = "CHAR(36)")
    private String huariqueId;

    @Column(name = "reservation_id", columnDefinition = "CHAR(36)")
    private String reservationId;

    @Column(name = "sent_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime sentAt;

    protected MessageEntity() {
        // JPA
    }

    MessageEntity(
            String id,
            String sender,
            String content,
            String intentType,
            String intentData,
            String huariqueId,
            String reservationId,
            LocalDateTime sentAt) {
        this.id = id;
        this.sender = sender;
        this.content = content;
        this.intentType = intentType;
        this.intentData = intentData;
        this.huariqueId = huariqueId;
        this.reservationId = reservationId;
        this.sentAt = sentAt;
    }

    void setConversation(ConversationEntity conversation) {
        this.conversation = conversation;
    }

    String getId() {
        return id;
    }

    String getSender() {
        return sender;
    }

    String getContent() {
        return content;
    }

    String getIntentType() {
        return intentType;
    }

    String getIntentData() {
        return intentData;
    }

    String getHuariqueId() {
        return huariqueId;
    }

    String getReservationId() {
        return reservationId;
    }

    LocalDateTime getSentAt() {
        return sentAt;
    }
}
