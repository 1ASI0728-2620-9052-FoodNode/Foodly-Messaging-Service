package pe.foodly.messaging.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/** Identificador del explorador emitido por IAM. Messaging solo lo referencia (5.6.1). */
public record ExplorerId(UUID value) {

    public ExplorerId {
        Objects.requireNonNull(value, "El identificador es obligatorio.");
    }

    public static ExplorerId of(String value) {
        Objects.requireNonNull(value, "El identificador es obligatorio.");
        try {
            return new ExplorerId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID válido: " + value, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
