package pe.foodly.messaging.interfaces.rest.resources;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

/** Mensaje que el explorador le escribe a Lía (5.6.2). */
@Schema(description = "Mensaje del explorador a Lía.")
public record SendMessageResource(
        @Schema(description = "Texto del mensaje. No puede estar vacío ni exceder la longitud máxima del chat "
                + "(MESSAGING_MAX_LENGTH, por defecto 1000 caracteres).",
                example = "Quiero un ceviche barato cerca", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "El mensaje no puede estar vacío.")
        String content,

        @Schema(description = "Conversación a la que escribe. Si se omite, se usa la conversación activa del "
                + "explorador o se inicia una nueva.", example = "5b0d3e8a-6f43-4a0e-9d38-2d7b1c4f9a10")
        UUID conversationId,

        @Schema(description = "Latitud de la ubicación que comparte el explorador (-90 a 90). "
                + "Debe enviarse junto con la longitud.", example = "-12.1219")
        @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90.")
        @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90.")
        Double latitude,

        @Schema(description = "Longitud de la ubicación que comparte el explorador (-180 a 180). "
                + "Debe enviarse junto con la latitud.", example = "-77.0297")
        @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180.")
        @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180.")
        Double longitude) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "La latitud y la longitud deben enviarse juntas.")
    public boolean isLocationComplete() {
        return (latitude == null) == (longitude == null);
    }
}
