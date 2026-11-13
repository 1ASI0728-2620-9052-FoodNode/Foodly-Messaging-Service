package pe.foodly.messaging.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Cuerpo del 202: el mensaje quedó registrado y Lía responde de forma asíncrona. */
@Schema(description = "Mensaje aceptado. La respuesta de Lía llega por el canal STOMP o con GET .../messages.")
public record SendMessageAcceptedResource(
        @Schema(description = "Conversación a la que pertenece el mensaje.") UUID conversationId,
        @Schema(description = "Identificador del mensaje registrado.") UUID messageId,
        @Schema(description = "Siempre PROCESSING: Lía todavía está interpretando el mensaje.", example = "PROCESSING")
        String status) {

    public static final String PROCESSING = "PROCESSING";
}
