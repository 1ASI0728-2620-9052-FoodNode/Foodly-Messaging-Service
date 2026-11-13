package pe.foodly.messaging.application.queries;

import java.util.Objects;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;

/** ¿La conversación existe y pertenece al explorador? Se usa para autorizar la suscripción STOMP. */
public record CheckConversationOwnershipQuery(ConversationId conversationId, ExplorerId explorerId) {

    public CheckConversationOwnershipQuery {
        Objects.requireNonNull(conversationId, "La conversación es obligatoria.");
        Objects.requireNonNull(explorerId, "El explorador es obligatorio.");
    }
}
