package pe.foodly.messaging.interfaces.rest.resources;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import pe.foodly.messaging.domain.model.enums.IntentType;

/**
 * Respuesta de Lía que el Integration System reenvía a Messaging (5.6.2; formato de la sección 3.5 de la
 * spec). Messaging interpreta {@code intent} con {@code IntentRoutingService} y hace las consultas a
 * Radar, Business y Reservation; n8n solo interpreta y redacta.
 */
@Schema(description = "Respuesta de Lía enviada por el Integration System.")
public record LiaCallbackResource(
        @Schema(description = "Conversación a la que pertenece la respuesta.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "La conversación es obligatoria.")
        UUID conversationId,

        @Schema(description = "Mensaje del explorador al que responde Lía (el messageId de la solicitud).",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "El mensaje al que responde Lía es obligatorio.")
        UUID inReplyToMessageId,

        @Schema(description = "Texto que redactó n8n. A este texto se le agregan los datos obtenidos de Radar, "
                + "Business o Reservation.", example = "¡Claro! Te busco opciones cerca…")
        String replyText,

        @Schema(description = "Intención interpretada. Si se omite, se trata como desconocida.")
        @Valid
        Intent intent,

        @Schema(description = "Huariques sugeridos por Lía (ids). Se ignoran: mandan los datos de Radar y Business.",
                example = "[]")
        List<Object> suggestedHuariques,

        @Schema(description = "Reserva solicitada; completa los datos que la intención no trae.")
        @Valid
        RequestedReservation requestedReservation) {

    @Schema(description = "Significado extraído del mensaje.")
    public record Intent(
            @Schema(description = "Tipo de intención.", requiredMode = Schema.RequiredMode.REQUIRED,
                    example = "RECOMMENDATION_REQUEST")
            @NotNull(message = "El tipo de intención es obligatorio.")
            IntentType type,
            @Schema(description = "Antojo del explorador.", example = "ceviche") String cravings,
            @Schema(description = "Presupuesto en soles.") @Valid BudgetRange budgetRange,
            @Schema(description = "Ubicación del explorador.") @Valid Location location,
            @Schema(description = "Cantidad de personas.", example = "4")
            @Min(value = 1, message = "La cantidad de personas debe ser al menos 1.")
            Integer partySize,
            @Schema(description = "Fecha y hora solicitadas para la reserva.", example = "2026-11-20T20:00:00")
            LocalDateTime requestedAt,
            @Schema(description = "Huarique referido, si el mensaje menciona uno.") UUID huariqueId,
            @Schema(description = "Informativo: Messaging calcula el contexto faltante por su cuenta.")
            List<String> missingContext) {
    }

    @Schema(description = "Rango de precio en soles (mínimo menor o igual al máximo).")
    public record BudgetRange(
            @NotNull(message = "El presupuesto mínimo es obligatorio.")
            @DecimalMin(value = "0", message = "El presupuesto no puede ser negativo.")
            @Schema(example = "0") BigDecimal min,
            @NotNull(message = "El presupuesto máximo es obligatorio.")
            @DecimalMin(value = "0", message = "El presupuesto no puede ser negativo.")
            @Schema(example = "25") BigDecimal max) {
    }

    @Schema(description = "Coordenadas del explorador.")
    public record Location(
            @NotNull(message = "La latitud es obligatoria.")
            @DecimalMin(value = "-90", message = "La latitud debe estar entre -90 y 90.")
            @DecimalMax(value = "90", message = "La latitud debe estar entre -90 y 90.")
            @Schema(example = "-12.12") Double latitude,
            @NotNull(message = "La longitud es obligatoria.")
            @DecimalMin(value = "-180", message = "La longitud debe estar entre -180 y 180.")
            @DecimalMax(value = "180", message = "La longitud debe estar entre -180 y 180.")
            @Schema(example = "-77.03") Double longitude) {
    }

    @Schema(description = "Datos de la reserva que interpretó Lía; cualquiera puede faltar.")
    public record RequestedReservation(
            UUID huariqueId,
            @Schema(example = "2026-11-20T20:00:00") LocalDateTime requestedAt,
            @Min(value = 1, message = "La cantidad de personas debe ser al menos 1.") Integer partySize) {
    }
}
