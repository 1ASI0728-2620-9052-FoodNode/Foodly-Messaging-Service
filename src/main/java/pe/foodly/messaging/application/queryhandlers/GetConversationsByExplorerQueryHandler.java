package pe.foodly.messaging.application.queryhandlers;

import java.util.List;
import org.springframework.stereotype.Component;
import pe.foodly.messaging.application.queries.GetConversationsByExplorerQuery;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.repositories.IConversationRepository;

/** Devuelve las conversaciones del explorador autenticado, la de actividad más reciente primero. */
@Component
public class GetConversationsByExplorerQueryHandler {

    private final IConversationRepository conversations;

    public GetConversationsByExplorerQueryHandler(IConversationRepository conversations) {
        this.conversations = conversations;
    }

    public List<Conversation> handle(GetConversationsByExplorerQuery query) {
        return conversations.findAllByExplorerId(query.explorerId());
    }
}
