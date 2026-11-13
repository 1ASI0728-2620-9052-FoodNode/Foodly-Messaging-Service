package pe.foodly.messaging.application.events;

import pe.foodly.messaging.domain.model.valueobjects.ConversationContext;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;

/**
 * Un mensaje del explorador quedó registrado. Se publica dentro de la transacción de
 * {@code SendMessageCommandHandler}; el listener lo atiende solo después del commit.
 */
public record MessageAcceptedEvent(MessageId messageId, MessageContent content, ConversationContext context) {
}
