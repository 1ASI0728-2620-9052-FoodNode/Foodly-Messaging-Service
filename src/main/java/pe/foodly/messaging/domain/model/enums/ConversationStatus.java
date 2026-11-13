package pe.foodly.messaging.domain.model.enums;

/** Estado de una conversación (5.6.1). Solo una conversación ACTIVE acepta mensajes. */
public enum ConversationStatus {
    ACTIVE,
    CLOSED
}
