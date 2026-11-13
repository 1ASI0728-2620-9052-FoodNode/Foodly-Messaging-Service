package pe.foodly.messaging.domain.model.valueobjects;

import java.math.BigDecimal;
import java.util.Objects;

/** Rango de precio en soles (5.6.7.1, {@code budgetRange}). Ambos extremos {@code >= 0} y {@code min <= max}. */
public record PriceRange(BigDecimal min, BigDecimal max) {

    public PriceRange {
        Objects.requireNonNull(min, "El precio mínimo es obligatorio.");
        Objects.requireNonNull(max, "El precio máximo es obligatorio.");
        if (min.signum() < 0 || max.signum() < 0) {
            throw new IllegalArgumentException("El rango de precio no puede ser negativo.");
        }
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("El precio mínimo no puede ser mayor que el máximo.");
        }
    }
}
