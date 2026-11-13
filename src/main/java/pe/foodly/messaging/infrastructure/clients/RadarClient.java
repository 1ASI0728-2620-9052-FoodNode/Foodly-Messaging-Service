package pe.foodly.messaging.infrastructure.clients;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.outboundports.IRadarClient;
import pe.foodly.messaging.application.outboundports.NearbyHuarique;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;

/**
 * {@code POST {RADAR_URL}/radar/search} con {@code { latitude, longitude, kRing }}. Propaga el JWT del
 * explorador. Un huarique cuyo id no es un UUID se omite con un {@code warn} (Radar lo define como texto).
 */
public class RadarClient implements IRadarClient {

    private static final Logger log = LoggerFactory.getLogger(RadarClient.class);
    private static final String PROVIDER = "Radar";

    private final RestClient client;
    private final String searchPath;

    public RadarClient(RestClient client, String searchPath) {
        this.client = client;
        this.searchPath = searchPath;
    }

    @Override
    public List<NearbyHuarique> search(ExplorerToken token, GeoCoordinates location, int kRing) {
        JsonNode body = ProviderCalls.execute(PROVIDER, () -> client.post()
                .uri(searchPath)
                .header(HttpHeaders.AUTHORIZATION, ProviderCalls.bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("latitude", location.latitude(), "longitude", location.longitude(), "kRing", kRing))
                .retrieve()
                .body(JsonNode.class));

        List<NearbyHuarique> result = new ArrayList<>();
        if (body == null) {
            return result;
        }
        for (JsonNode item : body.path("huariques")) {
            try {
                result.add(new NearbyHuarique(
                        HuariqueId.of(item.path("huariqueId").asText()), item.path("distanceMeters").asDouble()));
            } catch (IllegalArgumentException e) {
                log.warn("Radar devolvió un huarique con id que no es UUID; se omite.");
            }
        }
        return result;
    }
}
