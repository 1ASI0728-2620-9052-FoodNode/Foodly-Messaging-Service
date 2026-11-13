package pe.foodly.messaging.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/** Identificador de la reserva emitido por Reservation. Messaging solo lo referencia (5.6.1). */
public record ReservationId(UUID value) {

    public ReservationId {
        Objects.requireNonNull(value, "El identificador es obligatorio.");
    }

    public static ReservationId of(String value) {
        Objects.requireNonNull(value, "El identificador es obligatorio.");
        try {
            return new ReservationId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID válido: " + value, e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
