package pe.foodly.messaging.domain.model.valueobjects;

/**
 * Texto de un mensaje (5.6.1). No puede estar vacío ni exceder la longitud máxima del chat.
 *
 * <p>La longitud máxima configurable ({@code MESSAGING_MAX_LENGTH}, por defecto
 * {@value #DEFAULT_MAX_LENGTH}) la lee la capa de aplicación y la pasa a {@link #of(String, int)}:
 * el dominio no conoce la configuración.
 */
public final class MessageContent {

    public static final int DEFAULT_MAX_LENGTH = 1000;

    private final String text;

    private MessageContent(String text) {
        this.text = text;
    }

    public static MessageContent of(String text) {
        return of(text, DEFAULT_MAX_LENGTH);
    }

    public static MessageContent of(String text, int maxLength) {
        if (maxLength < 1) {
            throw new IllegalArgumentException("La longitud máxima del mensaje debe ser al menos 1.");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("El mensaje no puede estar vacío.");
        }
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(
                    "El mensaje no puede exceder los " + maxLength + " caracteres.");
        }
        return new MessageContent(text);
    }

    public String text() {
        return text;
    }

    /** Siempre {@code false} en una instancia válida: el contenido vacío se rechaza al crearla. */
    public boolean isBlank() {
        return text.isBlank();
    }

    public int length() {
        return text.length();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof MessageContent other && text.equals(other.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public String toString() {
        return text;
    }
}
