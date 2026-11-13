package pe.foodly.messaging.application.queries;

import java.util.Objects;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;

/** Conversaciones del explorador autenticado. */
public record GetConversationsByExplorerQuery(ExplorerId explorerId) {

    public GetConversationsByExplorerQuery {
        Objects.requireNonNull(explorerId, "El explorador es obligatorio.");
    }
}
