package pe.foodly.messaging.application;

/**
 * Parámetros de configuración que usan los casos de uso.
 *
 * @param maxMessageLength {@code MESSAGING_MAX_LENGTH}: longitud máxima de un mensaje (también de las respuestas de Lía)
 * @param radarKRing {@code MESSAGING_RADAR_K_RING}: k-ring con el que se consulta a Radar
 */
public record MessagingSettings(int maxMessageLength, int radarKRing) {

    public MessagingSettings {
        if (maxMessageLength < 1) {
            throw new IllegalArgumentException("La longitud máxima del mensaje debe ser al menos 1.");
        }
        if (radarKRing < 0) {
            throw new IllegalArgumentException("El k-ring no puede ser negativo.");
        }
    }
}
