package pe.foodly.messaging.application.queryhandlers;

import org.springframework.stereotype.Component;
import pe.foodly.messaging.application.queries.CheckConversationOwnershipQuery;
import pe.foodly.messaging.domain.repositories.IConversationRepository;

/** Verdadero solo si la conversación existe y es del explorador: no distingue «no existe» de «es de otro». */
@Component
public class CheckConversationOwnershipQueryHandler {

    private final IConversationRepository conversations;

    public CheckConversationOwnershipQueryHandler(IConversationRepository conversations) {
        this.conversations = conversations;
    }

    public boolean handle(CheckConversationOwnershipQuery query) {
        return conversations.findById(query.conversationId())
                .filter(conversation -> conversation.getExplorerId().equals(query.explorerId()))
                .isPresent();
    }
}
