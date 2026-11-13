package pe.foodly.messaging.domain.exceptions;

import pe.foodly.messaging.domain.model.valueobjects.ConversationId;

/** Se intentó agregar un mensaje a una conversación cerrada: solo una conversación activa los acepta. */
public class ConversationClosedException extends DomainException {

    public ConversationClosedException(ConversationId id) {
        super("La conversación " + id + " está cerrada y no acepta mensajes.");
    }
}
