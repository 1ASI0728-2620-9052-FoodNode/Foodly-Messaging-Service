package pe.foodly.messaging.infrastructure.clients;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import pe.foodly.messaging.application.outboundports.IBusinessClient;
import pe.foodly.messaging.application.outboundports.IRadarClient;
import pe.foodly.messaging.application.outboundports.IReservationClient;

/**
 * Clientes REST reales. Con el perfil {@code stub} no se crean (ni se exigen {@code RADAR_URL},
 * {@code BUSINESS_URL} ni {@code RESERVATION_URL}): los reemplazan los clientes falsos.
 */
@Configuration
@Profile("!stub")
@EnableConfigurationProperties(ProviderClientsProperties.class)
class ProviderClientsConfig {

    @Bean
    IRadarClient radarClient(ProviderClientsProperties properties) {
        ProviderClientsProperties.Provider radar = properties.radar();
        return new RadarClient(
                ProviderCalls.restClientBuilder(radar.url(), properties.timeout()).build(), radar.searchPath());
    }

    @Bean
    IBusinessClient businessClient(ProviderClientsProperties properties) {
        ProviderClientsProperties.Provider business = properties.business();
        return new BusinessClient(
                ProviderCalls.restClientBuilder(business.url(), properties.timeout()).build(),
                business.huariquePath(),
                business.menuPath());
    }

    @Bean
    IReservationClient reservationClient(ProviderClientsProperties properties) {
        ProviderClientsProperties.Provider reservation = properties.reservation();
        return new ReservationClient(
                ProviderCalls.restClientBuilder(reservation.url(), properties.timeout()).build(),
                reservation.createPath(),
                reservation.statusPath());
    }
}
