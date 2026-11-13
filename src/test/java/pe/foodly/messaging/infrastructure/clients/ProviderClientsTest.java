package pe.foodly.messaging.infrastructure.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.exceptions.ProviderUnavailableException;
import pe.foodly.messaging.application.exceptions.ReservationRejectedException;
import pe.foodly.messaging.application.outboundports.HuariqueDetails;
import pe.foodly.messaging.application.outboundports.MenuItem;
import pe.foodly.messaging.application.outboundports.NearbyHuarique;
import pe.foodly.messaging.application.outboundports.ReservationConfirmation;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;

/** Los tres clientes REST contra un servidor simulado, y el timeout contra un servidor real lento. */
class ProviderClientsTest {

    private static final ExplorerToken TOKEN = new ExplorerToken("jwt-del-explorador", Instant.parse("2030-01-01T00:00:00Z"));
    private static final HuariqueId H1 = HuariqueId.of("a1111111-1111-4111-8111-111111111111");
    private static final String RESERVATION = "33333333-3333-3333-3333-333333333333";

    // ---- Radar ----

    @Test
    void radarEnviaLaBusquedaConElTokenDelExploradorYLeeLosResultados() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://radar.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://radar.test/radar/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer jwt-del-explorador"))
                .andExpect(jsonPath("$.latitude").value(-12.12))
                .andExpect(jsonPath("$.longitude").value(-77.03))
                .andExpect(jsonPath("$.kRing").value(2))
                .andRespond(withSuccess("""
                        { "huariques": [
                            { "huariqueId": "a1111111-1111-4111-8111-111111111111", "latitude": -12.1, "longitude": -77.0, "distanceMeters": 111.2 },
                            { "huariqueId": "65f1c2a9e4b0a1d2c3f4a5b6", "latitude": -12.1, "longitude": -77.0, "distanceMeters": 500.0 }
                          ], "total": 2 }""", MediaType.APPLICATION_JSON));

        List<NearbyHuarique> result = new RadarClient(builder.build(), "/radar/search")
                .search(TOKEN, new GeoCoordinates(-12.12, -77.03), 2);

        // El segundo id no es un UUID (Radar lo define como texto): se omite.
        assertThat(result).containsExactly(new NearbyHuarique(H1, 111.2));
        server.verify();
    }

    @Test
    void radarConZonaVaciaDevuelveUnaListaVacia() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://radar.test");
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("http://radar.test/radar/search"))
                .andRespond(withSuccess("{ \"huariques\": [], \"total\": 0 }", MediaType.APPLICATION_JSON));

        assertThat(new RadarClient(builder.build(), "/radar/search")
                        .search(TOKEN, new GeoCoordinates(-12.12, -77.03), 2))
                .isEmpty();
    }

    @Test
    void radarConErrorDelServidorOTokenRechazadoEsProveedorNoDisponible() {
        for (HttpStatus status : List.of(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN)) {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://radar.test");
            MockRestServiceServer.bindTo(builder).build()
                    .expect(requestTo("http://radar.test/radar/search")).andRespond(withStatus(status));

            assertThatThrownBy(() -> new RadarClient(builder.build(), "/radar/search")
                            .search(TOKEN, new GeoCoordinates(-12.12, -77.03), 2))
                    .isInstanceOf(ProviderUnavailableException.class)
                    .hasMessageContaining("Radar")
                    .hasMessageNotContaining("jwt-del-explorador");
        }
    }

    // ---- Business ----

    @Test
    void businessLeeElDetalleYElMenuConElTokenDelExplorador() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://business.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://business.test/business-api/huariques/" + H1))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer jwt-del-explorador"))
                .andRespond(withSuccess("""
                        { "name": "Cevichería A", "description": "Pescado fresco", "address": "Av. Larco 345",
                          "openingHours": "12:00 - 17:00", "isOpen": true }""", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://business.test/business-api/huariques/" + H1 + "/menu"))
                .andExpect(header("Authorization", "Bearer jwt-del-explorador"))
                .andRespond(withSuccess("""
                        { "items": [ { "name": "Ceviche clásico", "price": 32.5 }, { "name": "Chicha", "description": "morada" } ] }""",
                        MediaType.APPLICATION_JSON));
        BusinessClient client = new BusinessClient(
                builder.build(), "/business-api/huariques/{id}", "/business-api/huariques/{id}/menu");

        HuariqueDetails details = client.findHuarique(TOKEN, H1).orElseThrow();
        List<MenuItem> menu = client.findMenu(TOKEN, H1);

        assertThat(details).isEqualTo(new HuariqueDetails(
                H1, "Cevichería A", "Pescado fresco", "Av. Larco 345", "12:00 - 17:00", true));
        assertThat(menu).extracting(MenuItem::name).containsExactly("Ceviche clásico", "Chicha");
        assertThat(menu.get(0).price()).isEqualByComparingTo("32.5");
        assertThat(menu.get(1).price()).isNull();
        server.verify();
    }

    @Test
    void businessAceptaElMenuComoArregloDirecto() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://business.test");
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("http://business.test/business-api/huariques/" + H1 + "/menu"))
                .andRespond(withSuccess("[ { \"name\": \"Anticucho\", \"price\": 22 } ]", MediaType.APPLICATION_JSON));

        assertThat(new BusinessClient(builder.build(), "/business-api/huariques/{id}", "/business-api/huariques/{id}/menu")
                        .findMenu(TOKEN, H1))
                .extracting(MenuItem::name).containsExactly("Anticucho");
    }

    @Test
    void businessConHuariqueInexistenteDevuelveVacioYConErrorDelServidorFalla() {
        RestClient.Builder notFound = RestClient.builder().baseUrl("http://business.test");
        MockRestServiceServer.bindTo(notFound).build()
                .expect(requestTo("http://business.test/business-api/huariques/" + H1))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThat(new BusinessClient(notFound.build(), "/business-api/huariques/{id}", "/menu/{id}")
                        .findHuarique(TOKEN, H1))
                .isEmpty();

        RestClient.Builder down = RestClient.builder().baseUrl("http://business.test");
        MockRestServiceServer.bindTo(down).build()
                .expect(requestTo("http://business.test/business-api/huariques/" + H1))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> new BusinessClient(down.build(), "/business-api/huariques/{id}", "/menu/{id}")
                        .findHuarique(TOKEN, H1))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    // ---- Reservation ----

    @Test
    void reservationCreaLaReservaYDevuelveSuIdYEstado() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://reservation.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://reservation.test/reservation-api/reservations"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer jwt-del-explorador"))
                .andExpect(jsonPath("$.huariqueId").value(H1.toString()))
                .andExpect(jsonPath("$.date").value("2026-11-14"))
                .andExpect(jsonPath("$.time").value("20:00"))
                .andExpect(jsonPath("$.partySize").value(4))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{ \"id\": \"" + RESERVATION + "\", \"status\": \"PENDING\" }"));

        ReservationConfirmation confirmation = new ReservationClient(
                        builder.build(), "/reservation-api/reservations", "/reservation-api/reservations/{id}")
                .request(TOKEN, H1, LocalDate.of(2026, 11, 14), LocalTime.of(20, 0), 4);

        assertThat(confirmation).isEqualTo(new ReservationConfirmation(ReservationId.of(RESERVATION), "PENDING"));
        server.verify();
    }

    @Test
    void reservationRechazaConUn409YElMotivoLlegaAlHandler() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://reservation.test");
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("http://reservation.test/reservation-api/reservations"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON).body("""
                        { "status": 409, "error": "Conflict", "message": "No hay mesas disponibles a esa hora." }"""));

        assertThatThrownBy(() -> new ReservationClient(builder.build(), "/reservation-api/reservations", "/x/{id}")
                        .request(TOKEN, H1, LocalDate.of(2026, 11, 14), LocalTime.of(20, 0), 4))
                .isInstanceOfSatisfying(ReservationRejectedException.class,
                        e -> assertThat(e.getReason()).isEqualTo("No hay mesas disponibles a esa hora."));
    }

    @Test
    void reservationRechazaConUn400SinCuerpoYSinMotivo() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://reservation.test");
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("http://reservation.test/reservation-api/reservations"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> new ReservationClient(builder.build(), "/reservation-api/reservations", "/x/{id}")
                        .request(TOKEN, H1, LocalDate.of(2026, 11, 14), LocalTime.of(20, 0), 4))
                .isInstanceOfSatisfying(ReservationRejectedException.class, e -> assertThat(e.getReason()).isNull());
    }

    @Test
    void reservationConErrorDelServidorOUnTokenRechazadoEsProveedorNoDisponible() {
        for (HttpStatus status : List.of(HttpStatus.BAD_GATEWAY, HttpStatus.UNAUTHORIZED)) {
            RestClient.Builder builder = RestClient.builder().baseUrl("http://reservation.test");
            MockRestServiceServer.bindTo(builder).build()
                    .expect(requestTo("http://reservation.test/reservation-api/reservations"))
                    .andRespond(withStatus(status));

            assertThatThrownBy(() -> new ReservationClient(builder.build(), "/reservation-api/reservations", "/x/{id}")
                            .request(TOKEN, H1, LocalDate.of(2026, 11, 14), LocalTime.of(20, 0), 4))
                    .isInstanceOf(ProviderUnavailableException.class);
        }
    }

    @Test
    void reservationConUnaRespuestaSinIdEsProveedorNoDisponible() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://reservation.test");
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("http://reservation.test/reservation-api/reservations"))
                .andRespond(withSuccess("{ \"status\": \"PENDING\" }", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new ReservationClient(builder.build(), "/reservation-api/reservations", "/x/{id}")
                        .request(TOKEN, H1, LocalDate.of(2026, 11, 14), LocalTime.of(20, 0), 4))
                .isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void reservationConsultaElEstadoYUnaReservaInexistenteDevuelveVacio() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://reservation.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://reservation.test/reservation-api/reservations/" + RESERVATION))
                .andExpect(header("Authorization", "Bearer jwt-del-explorador"))
                .andRespond(withSuccess("{ \"id\": \"" + RESERVATION + "\", \"status\": \"ACCEPTED\" }",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://reservation.test/reservation-api/reservations/00000000-0000-0000-0000-000000000000"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        ReservationClient client = new ReservationClient(
                builder.build(), "/reservation-api/reservations", "/reservation-api/reservations/{id}");

        assertThat(client.findStatus(TOKEN, ReservationId.of(RESERVATION)))
                .contains(new ReservationConfirmation(ReservationId.of(RESERVATION), "ACCEPTED"));
        assertThat(client.findStatus(TOKEN, ReservationId.of("00000000-0000-0000-0000-000000000000"))).isEmpty();
    }

    // ---- timeout real ----

    @Test
    void unProveedorQueNoRespondeAlTiempoEsProveedorNoDisponible() throws IOException {
        HttpServer slow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slow.createContext("/", exchange -> {
            try {
                Thread.sleep(3_000);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException | IOException ignored) {
                // el cliente ya cortó la conexión
            } finally {
                exchange.close();
            }
        });
        slow.start();
        try {
            RestClient client = ProviderCalls.restClientBuilder(
                    "http://127.0.0.1:" + slow.getAddress().getPort(), Duration.ofMillis(300)).build();
            long start = System.nanoTime();

            assertThatThrownBy(() -> new RadarClient(client, "/radar/search")
                            .search(TOKEN, new GeoCoordinates(-12.12, -77.03), 2))
                    .isInstanceOf(ProviderUnavailableException.class)
                    .hasMessageContaining("no respondió a tiempo");

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        } finally {
            slow.stop(0);
        }
    }

    @Test
    void unProveedorSinConexionEsProveedorNoDisponible() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        RestClient client = ProviderCalls.restClientBuilder("http://127.0.0.1:" + closedPort, Duration.ofSeconds(1)).build();

        assertThatThrownBy(() -> new BusinessClient(client, "/business-api/huariques/{id}", "/menu/{id}")
                        .findHuarique(TOKEN, H1))
                .isInstanceOf(ProviderUnavailableException.class);
    }
}
