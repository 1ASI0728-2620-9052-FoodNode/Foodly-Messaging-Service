package pe.foodly.messaging.domain.model.valueobjects;

/** Ubicación del explorador (5.6.7.1, {@code location}). Latitud entre -90 y 90 y longitud entre -180 y 180. */
public record GeoCoordinates(double latitude, double longitude) {

    public GeoCoordinates {
        // La forma negada también rechaza NaN.
        if (!(latitude >= -90.0 && latitude <= 90.0)) {
            throw new IllegalArgumentException("La latitud debe estar entre -90 y 90.");
        }
        if (!(longitude >= -180.0 && longitude <= 180.0)) {
            throw new IllegalArgumentException("La longitud debe estar entre -180 y 180.");
        }
    }
}
