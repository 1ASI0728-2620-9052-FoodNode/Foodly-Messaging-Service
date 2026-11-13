package pe.foodly.messaging.application.events;

import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;

/**
 * Se agregó una respuesta de Lía a una conversación. Se publica dentro de la transacción que la guarda;
 * quien la atienda (el canal STOMP) debe hacerlo después del commit, para que {@code GET .../messages}
 * ya devuelva el mensaje cuando el cliente lo recibe.
 */
public record LiaReplyRegisteredEvent(ConversationId conversationId, Message message) {
}
