package pe.foodly.messaging.domain.model.valueobjects;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import pe.foodly.messaging.domain.model.enums.IntentType;

/**
 * Significado extraído de un mensaje en lenguaje natural (5.6.1).
 *
 * <p>{@code huariqueId} es el huarique referido (opcional): lo necesitan {@code QUERY_BUSINESS} y
 * {@code FORWARD_TO_RESERVATION}. Ver DEVIATIONS.md.
 *
 * <p>Qué contexto exige cada tipo:
 * <ul>
 *   <li>{@code RECOMMENDATION_REQUEST}: {@code location}.</li>
 *   <li>{@code HUARIQUE_INQUIRY}: {@code huariqueId}.</li>
 *   <li>{@code RESERVATION_REQUEST}: {@code huariqueId}, {@code requestedAt} y {@code partySize}.</li>
 *   <li>{@code UNKNOWN}: nunca está completa; falta {@code type}.</li>
 * </ul>
 * Los nombres que devuelve {@link #missingContext()} son los de los componentes de este record.
 */
public record UserIntent(
        IntentType type,
        String cravings,
        PriceRange budgetRange,
        GeoCoordinates location,
        Integer partySize,
        LocalDateTime requestedAt,
        HuariqueId huariqueId) {

    public UserIntent {
        Objects.requireNonNull(type, "El tipo de intención es obligatorio.");
        if (partySize != null && partySize < 1) {
            throw new IllegalArgumentException("La cantidad de personas debe ser al menos 1.");
        }
    }

    /** {@code true} si no falta ningún dato que exige el tipo de intención. */
    public boolean isComplete() {
        return type != IntentType.UNKNOWN && missingContext().isEmpty();
    }

    /** Nombres de los campos que faltan para que la intención esté completa, en el orden del record. */
    public List<String> missingContext() {
        List<String> missing = new ArrayList<>();
        switch (type) {
            case RECOMMENDATION_REQUEST -> {
                if (location == null) {
                    missing.add("location");
                }
            }
            case HUARIQUE_INQUIRY -> {
                if (huariqueId == null) {
                    missing.add("huariqueId");
                }
            }
            case RESERVATION_REQUEST -> {
                if (huariqueId == null) {
                    missing.add("huariqueId");
                }
                if (requestedAt == null) {
                    missing.add("requestedAt");
                }
                if (partySize == null) {
                    missing.add("partySize");
                }
            }
            case UNKNOWN -> missing.add("type");
        }
        return List.copyOf(missing);
    }
}
