package pe.foodly.messaging.application.exceptions;

import pe.foodly.messaging.domain.model.valueobjects.ConversationId;

/**
 * La conversación no existe o no pertenece al explorador que la consulta. Los dos casos son el mismo
 * error a propósito (404): no se revela si la conversación existe.
 */
public class ConversationNotFoundException extends RuntimeException {

    public ConversationNotFoundException(ConversationId id) {
        super("La conversación " + id + " no existe.");
    }
}
