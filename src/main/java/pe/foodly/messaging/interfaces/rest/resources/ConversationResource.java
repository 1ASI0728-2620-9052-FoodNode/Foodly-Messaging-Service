package pe.foodly.messaging.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

/** Una conversación del explorador con Lía (5.6.2). */
@Schema(description = "Conversación del explorador con Lía.")
public record ConversationResource(
        @Schema(description = "Identificador de la conversación.") UUID id,
        @Schema(description = "Estado: ACTIVE (acepta mensajes) o CLOSED.", example = "ACTIVE") String status,
        @Schema(description = "Fecha de inicio.", example = "2026-11-13T15:00:00") LocalDateTime startedAt,
        @Schema(description = "Fecha del último mensaje.", example = "2026-11-13T15:02:31") LocalDateTime lastMessageAt) {
}
