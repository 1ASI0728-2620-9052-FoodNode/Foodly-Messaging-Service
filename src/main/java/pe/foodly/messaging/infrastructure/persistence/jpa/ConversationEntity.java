package pe.foodly.messaging.infrastructure.persistence.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Fila de {@code conversations} (5.6.7.2). Modelo de persistencia, separado del agregado de dominio. */
@Entity
@Table(name = "conversations")
class ConversationEntity {

    @Id
    @Column(name = "id", columnDefinition = "CHAR(36)")
    private String id;

    @Column(name = "explorer_id", nullable = false, columnDefinition = "CHAR(36)")
    private String explorerId;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "started_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime startedAt;

    @Column(name = "last_message_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime lastMessageAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL)
    @OrderBy("sentAt ASC")
    private List<MessageEntity> messages = new ArrayList<>();

    protected ConversationEntity() {
        // JPA
    }

    ConversationEntity(
            String id, String explorerId, String status, LocalDateTime startedAt, LocalDateTime lastMessageAt) {
        this.id = id;
        this.explorerId = explorerId;
        this.status = status;
        this.startedAt = startedAt;
        this.lastMessageAt = lastMessageAt;
    }

    void addMessage(MessageEntity message) {
        message.setConversation(this);
        messages.add(message);
    }

    String getId() {
        return id;
    }

    String getExplorerId() {
        return explorerId;
    }

    String getStatus() {
        return status;
    }

    LocalDateTime getStartedAt() {
        return startedAt;
    }

    LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    List<MessageEntity> getMessages() {
        return messages;
    }
}
