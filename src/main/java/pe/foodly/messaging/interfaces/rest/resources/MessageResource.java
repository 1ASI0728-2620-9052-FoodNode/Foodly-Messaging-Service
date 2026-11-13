package pe.foodly.messaging.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

/** Un mensaje de la conversación (5.6.2). También es el payload que se publica por STOMP. */
@Schema(description = "Mensaje del explorador o de Lía.")
public record MessageResource(
        @Schema(description = "Identificador del mensaje.") UUID id,
        @Schema(description = "Emisor: EXPLORER o LIA.", example = "LIA") String sender,
        @Schema(description = "Texto del mensaje.", example = "¡Claro! Esto es lo que encontré cerca de ti…")
        String content,
        @Schema(description = "Intención detectada (RECOMMENDATION_REQUEST, HUARIQUE_INQUIRY, "
                + "RESERVATION_REQUEST o UNKNOWN); nula si el mensaje no tiene intención.",
                example = "RECOMMENDATION_REQUEST", nullable = true)
        String intentType,
        @Schema(description = "Huarique al que se refiere el mensaje, si aplica.", nullable = true) UUID huariqueId,
        @Schema(description = "Reserva creada a partir de este mensaje, si existe.", nullable = true) UUID reservationId,
        @Schema(description = "Fecha de envío.", example = "2026-11-13T15:00:03.512") LocalDateTime sentAt) {
}
