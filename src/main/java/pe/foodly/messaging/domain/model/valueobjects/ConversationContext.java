package pe.foodly.messaging.domain.model.valueobjects;

import java.util.List;
import java.util.Objects;
import pe.foodly.messaging.domain.model.entities.Message;

/**
 * Contexto conversacional que acompaña a un mensaje cuando se pide su interpretación
 * ({@code ILiaAgentGateway.requestInterpretation}): la conversación, la ubicación del explorador
 * (opcional) y el historial previo. Ver DEVIATIONS.md.
 */
public record ConversationContext(ConversationId conversationId, GeoCoordinates location, List<Message> history) {

    public ConversationContext {
        Objects.requireNonNull(conversationId, "La conversación es obligatoria.");
        history = history == null ? List.of() : List.copyOf(history);
    }
}
