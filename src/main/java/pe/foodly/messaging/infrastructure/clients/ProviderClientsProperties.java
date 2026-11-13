package pe.foodly.messaging.infrastructure.clients;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de los clientes REST hacia los contextos proveedores ({@code messaging.clients.*}).
 * Las URLs base vienen de {@code RADAR_URL}, {@code BUSINESS_URL} y {@code RESERVATION_URL}; las rutas
 * son configurables porque están por confirmar con los dueños de cada contexto.
 */
@ConfigurationProperties(prefix = "messaging.clients")
public record ProviderClientsProperties(
        @DefaultValue("3s") Duration timeout, Provider radar, Provider business, Provider reservation) {

    /** URL base y rutas de un proveedor; los {@code {id}} de las rutas los completa el cliente. */
    public record Provider(
            String url,
            @DefaultValue("/radar/search") String searchPath,
            @DefaultValue("/business-api/huariques/{id}") String huariquePath,
            @DefaultValue("/business-api/huariques/{id}/menu") String menuPath,
            @DefaultValue("/reservation-api/reservations") String createPath,
            @DefaultValue("/reservation-api/reservations/{id}") String statusPath) {
    }
}
