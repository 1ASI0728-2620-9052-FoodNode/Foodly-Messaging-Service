package pe.foodly.messaging.domain.model.valueobjects;

import java.util.Objects;
import java.util.UUID;

/** Identificador propio del contexto Messaging (5.6.1). */
public record ConversationId(UUID value) {

    public ConversationId {
        Objects.requireNonNull(value, "El identificador es obligatorio.");
    }

    public static ConversationId of(String value) {
        Objects.requireNonNull(value, "El identificador es obligatorio.");
        try {
            return new ConversationId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID válido: " + value, e);
        }
    }

    public static ConversationId newId() {
        return new ConversationId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
