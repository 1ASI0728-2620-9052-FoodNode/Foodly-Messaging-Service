package pe.foodly.messaging.application.commands;

import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;

/** Resultado de {@code SendMessageCommand}: base del cuerpo 202 {@code { conversationId, messageId, status }}. */
public record SendMessageResult(ConversationId conversationId, MessageId messageId) {
}
