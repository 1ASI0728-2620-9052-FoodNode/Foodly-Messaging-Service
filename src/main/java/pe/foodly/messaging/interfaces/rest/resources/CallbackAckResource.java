package pe.foodly.messaging.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;

/** Cuerpo del 202 del callback: la respuesta se recibió y se procesa de forma asíncrona. */
@Schema(description = "Callback recibido.")
public record CallbackAckResource(
        @Schema(description = "Siempre RECEIVED.", example = "RECEIVED") String status) {

    public static final String RECEIVED = "RECEIVED";
}
