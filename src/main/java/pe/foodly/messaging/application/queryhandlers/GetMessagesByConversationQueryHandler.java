package pe.foodly.messaging.application.queryhandlers;

import java.util.List;
import org.springframework.stereotype.Component;
import pe.foodly.messaging.application.exceptions.ConversationNotFoundException;
import pe.foodly.messaging.application.queries.GetMessagesByConversationQuery;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.repositories.IConversationRepository;

/**
 * Devuelve los mensajes de una conversación en orden cronológico, verificando que pertenezca al
 * explorador que consulta: si no existe o es de otro explorador, es el mismo error (404).
 */
@Component
public class GetMessagesByConversationQueryHandler {

    private final IConversationRepository conversations;

    public GetMessagesByConversationQueryHandler(IConversationRepository conversations) {
        this.conversations = conversations;
    }

    public List<Message> handle(GetMessagesByConversationQuery query) {
        return conversations.findById(query.conversationId())
                .filter(conversation -> conversation.getExplorerId().equals(query.explorerId()))
                .orElseThrow(() -> new ConversationNotFoundException(query.conversationId()))
                .getMessages();
    }
}
