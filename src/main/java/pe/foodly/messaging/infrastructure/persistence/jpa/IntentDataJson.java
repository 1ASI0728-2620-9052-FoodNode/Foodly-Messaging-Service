package pe.foodly.messaging.infrastructure.persistence.jpa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import pe.foodly.messaging.domain.model.enums.IntentType;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.model.valueobjects.PriceRange;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;

/**
 * Contenido de la columna {@code intent_data}: un solo objeto JSON plano con los campos de
 * {@link UserIntent} (menos {@code type}, que va en {@code intent_type}) y los datos de correlación.
 *
 * <pre>
 * { "inReplyToMessageId": "…", "cravings": "ceviche", "budgetRange": { "min": 0, "max": 25 },
 *   "location": { "latitude": -12.12, "longitude": -77.03 }, "partySize": 4,
 *   "requestedAt": "2026-11-20T20:00:00", "huariqueId": "…" }
 * </pre>
 *
 * <p>{@code inReplyToMessageId} está en la raíz porque {@code LiaTimeoutJob} lo busca con
 * {@code JSON_EXTRACT(intent_data, '$.inReplyToMessageId')}. Los campos desconocidos se ignoran al leer.
 */
final class IntentDataJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private IntentDataJson() {
    }

    /** JSON de la intención y la correlación, o {@code null} si no hay nada que guardar. */
    static String write(UserIntent intent, MessageId inReplyToMessageId) {
        if (intent == null && inReplyToMessageId == null) {
            return null;
        }
        Data data = new Data(
                inReplyToMessageId == null ? null : inReplyToMessageId.toString(),
                intent == null ? null : intent.cravings(),
                intent == null || intent.budgetRange() == null
                        ? null
                        : new Budget(intent.budgetRange().min(), intent.budgetRange().max()),
                intent == null || intent.location() == null
                        ? null
                        : new Location(intent.location().latitude(), intent.location().longitude()),
                intent == null ? null : intent.partySize(),
                intent == null ? null : intent.requestedAt(),
                intent == null || intent.huariqueId() == null ? null : intent.huariqueId().toString());
        try {
            return MAPPER.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar intent_data.", e);
        }
    }

    /** La intención guardada; vacía si el mensaje no tiene {@code intent_type}. */
    static Optional<UserIntent> readIntent(String intentType, String json) {
        if (intentType == null) {
            return Optional.empty();
        }
        Data data = read(json);
        return Optional.of(new UserIntent(
                IntentType.valueOf(intentType),
                data.cravings(),
                data.budgetRange() == null ? null : new PriceRange(data.budgetRange().min(), data.budgetRange().max()),
                data.location() == null
                        ? null
                        : new GeoCoordinates(data.location().latitude(), data.location().longitude()),
                data.partySize(),
                data.requestedAt(),
                data.huariqueId() == null ? null : HuariqueId.of(data.huariqueId())));
    }

    static Optional<MessageId> readInReplyTo(String json) {
        Data data = read(json);
        return data.inReplyToMessageId() == null
                ? Optional.empty()
                : Optional.of(MessageId.of(data.inReplyToMessageId()));
    }

    private static Data read(String json) {
        if (json == null) {
            return Data.EMPTY;
        }
        try {
            Data data = MAPPER.readValue(json, Data.class);
            return data == null ? Data.EMPTY : data;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("intent_data no es un JSON válido.", e);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Data(
            String inReplyToMessageId,
            String cravings,
            Budget budgetRange,
            Location location,
            Integer partySize,
            LocalDateTime requestedAt,
            String huariqueId) {

        static final Data EMPTY = new Data(null, null, null, null, null, null, null);
    }

    private record Budget(BigDecimal min, BigDecimal max) {
    }

    private record Location(double latitude, double longitude) {
    }
}
