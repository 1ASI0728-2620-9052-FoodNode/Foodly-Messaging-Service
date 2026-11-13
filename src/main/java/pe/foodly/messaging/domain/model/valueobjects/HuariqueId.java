package pe.foodly.messaging.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/** Identificador del huarique emitido por Business. Messaging solo lo referencia (5.6.1). */
public record HuariqueId(UUID value) {

    public HuariqueId {
        Objects.requireNonNull(value, "El identificador es obligatorio.");
    }

    public static HuariqueId of(String value) {
        Objects.requireNonNull(value, "El identificador es obligatorio.");
        try {
            return new HuariqueId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID válido: " + value, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
