package pe.foodly.messaging.application;

import java.time.Instant;
import java.util.Objects;

/**
 * JWT del explorador y el instante en que vence. Los clientes REST lo propagan a Business, Radar y
 * Reservation. Nunca se persiste ni se registra en logs: {@link #toString()} no lo muestra.
 */
public record ExplorerToken(String value, Instant expiresAt) {

    public ExplorerToken {
        Objects.requireNonNull(value, "El token es obligatorio.");
        if (value.isBlank()) {
            throw new IllegalArgumentException("El token no puede estar vacío.");
        }
        Objects.requireNonNull(expiresAt, "La expiración del token es obligatoria.");
    }

    @Override
    public String toString() {
        return "ExplorerToken[***]";
    }
}
