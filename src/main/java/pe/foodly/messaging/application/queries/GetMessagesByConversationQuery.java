package pe.foodly.messaging.application.queries;

import java.util.Objects;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;

/** Mensajes de una conversación, que debe pertenecer al explorador que consulta. */
public record GetMessagesByConversationQuery(ConversationId conversationId, ExplorerId explorerId) {

    public GetMessagesByConversationQuery {
        Objects.requireNonNull(conversationId, "La conversación es obligatoria.");
        Objects.requireNonNull(explorerId, "El explorador es obligatorio.");
    }
}
