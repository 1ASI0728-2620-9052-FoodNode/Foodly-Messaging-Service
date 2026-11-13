package pe.foodly.messaging.infrastructure.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.exceptions.ProviderUnavailableException;
import pe.foodly.messaging.application.exceptions.ReservationRejectedException;
import pe.foodly.messaging.application.outboundports.IReservationClient;
import pe.foodly.messaging.application.outboundports.ReservationConfirmation;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;

/**
 * {@code POST {RESERVATION_URL}/reservation-api/reservations} con {@code { huariqueId, date, time,
 * partySize }} (responde 201; Reservation toma al explorador del JWT) y {@code GET .../{id}} para
 * consultar el estado. Un 400, 409 o 422 es una reserva rechazada, no una caída del servicio.
 */
public class ReservationClient implements IReservationClient {

    private static final String PROVIDER = "Reservation";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient client;
    private final String createPath;
    private final String statusPath;

    public ReservationClient(RestClient client, String createPath, String statusPath) {
        this.client = client;
        this.createPath = createPath;
        this.statusPath = statusPath;
    }

    @Override
    public ReservationConfirmation request(
            ExplorerToken token, HuariqueId huariqueId, LocalDate date, LocalTime time, int partySize) {
        JsonNode body;
        try {
            body = ProviderCalls.execute(PROVIDER, () -> client.post()
                    .uri(createPath)
                    .header(HttpHeaders.AUTHORIZATION, ProviderCalls.bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "huariqueId", huariqueId.toString(),
                            "date", date.toString(),
                            "time", time.toString(),
                            "partySize", partySize))
                    .retrieve()
                    .body(JsonNode.class));
        } catch (ProviderUnavailableException e) {
            if (e.getCause() instanceof HttpClientErrorException rejected && isRejection(rejected.getStatusCode())) {
                throw new ReservationRejectedException(reasonOf(rejected));
            }
            throw e;
        }
        return toConfirmation(body);
    }

    @Override
    public Optional<ReservationConfirmation> findStatus(ExplorerToken token, ReservationId reservationId) {
        return ProviderCalls.executeOptional(PROVIDER, () -> client.get()
                        .uri(statusPath, reservationId.toString())
                        .header(HttpHeaders.AUTHORIZATION, ProviderCalls.bearer(token))
                        .retrieve()
                        .body(JsonNode.class))
                .map(ReservationClient::toConfirmation);
    }

    private static boolean isRejection(HttpStatusCode status) {
        int code = status.value();
        return code == 400 || code == 409 || code == 422;
    }

    /** El {@code message} del error uniforme de Reservation, si lo trae. */
    private static String reasonOf(HttpClientErrorException e) {
        try {
            String message = JSON.readTree(e.getResponseBodyAsString()).path("message").asText(null);
            return message == null || message.isBlank() ? null : message;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ReservationConfirmation toConfirmation(JsonNode body) {
        if (body == null) {
            throw new ProviderUnavailableException(PROVIDER, "respuesta vacía");
        }
        JsonNode id = body.hasNonNull("id") ? body.get("id") : body.path("reservationId");
        try {
            return new ReservationConfirmation(ReservationId.of(id.asText()), body.path("status").asText(null));
        } catch (IllegalArgumentException e) {
            throw new ProviderUnavailableException(PROVIDER, "respuesta sin un id de reserva válido", e);
        }
    }
}
