package pe.foodly.messaging.domain.exceptions;

import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;

/** El mensaje no pertenece a la conversación. */
public class MessageNotFoundException extends DomainException {

    public MessageNotFoundException(ConversationId conversationId, MessageId messageId) {
        super("El mensaje " + messageId + " no existe en la conversación " + conversationId + ".");
    }
}
