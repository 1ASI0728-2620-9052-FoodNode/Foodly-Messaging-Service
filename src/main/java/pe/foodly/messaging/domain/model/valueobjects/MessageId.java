package pe.foodly.messaging.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/** Identificador propio del contexto Messaging (5.6.1). */
public record MessageId(UUID value) {

    public MessageId {
        Objects.requireNonNull(value, "El identificador es obligatorio.");
    }

    public static MessageId of(String value) {
        Objects.requireNonNull(value, "El identificador es obligatorio.");
        try {
            return new MessageId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID válido: " + value, e);
        }
    }

    public static MessageId newId() {
        return new MessageId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
