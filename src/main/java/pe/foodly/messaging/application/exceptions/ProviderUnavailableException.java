package pe.foodly.messaging.application.exceptions;

/**
 * Un contexto proveedor (Business, Radar o Reservation) no pudo atender la llamada: timeout, conexión
 * rechazada, error 5xx, token rechazado o respuesta ilegible. El mensaje no contiene URLs ni tokens.
 */
public class ProviderUnavailableException extends RuntimeException {

    private final String provider;

    public ProviderUnavailableException(String provider, String reason, Throwable cause) {
        super(provider + ": " + reason, cause);
        this.provider = provider;
    }

    public ProviderUnavailableException(String provider, String reason) {
        this(provider, reason, null);
    }

    public String getProvider() {
        return provider;
    }
}
