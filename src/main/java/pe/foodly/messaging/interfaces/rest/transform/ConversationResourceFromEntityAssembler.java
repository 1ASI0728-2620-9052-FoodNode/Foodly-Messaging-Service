package pe.foodly.messaging.interfaces.rest.transform;

import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.interfaces.rest.resources.ConversationResource;

public final class ConversationResourceFromEntityAssembler {

    private ConversationResourceFromEntityAssembler() {
    }

    public static ConversationResource toResource(Conversation conversation) {
        return new ConversationResource(
                conversation.getId().value(),
                conversation.getStatus().name(),
                conversation.getStartedAt(),
                conversation.getLastMessageAt());
    }
}
