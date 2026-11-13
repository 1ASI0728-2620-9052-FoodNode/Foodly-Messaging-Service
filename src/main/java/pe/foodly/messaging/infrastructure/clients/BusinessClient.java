package pe.foodly.messaging.infrastructure.clients;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.exceptions.ProviderUnavailableException;
import pe.foodly.messaging.application.outboundports.HuariqueDetails;
import pe.foodly.messaging.application.outboundports.IBusinessClient;
import pe.foodly.messaging.application.outboundports.MenuItem;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;

/**
 * {@code GET {BUSINESS_URL}/business-api/huariques/{id}} y {@code .../{id}/menu}, con el JWT del
 * explorador. El informe no define el formato de respuesta de Business (ver DEVIATIONS.md): se leen los
 * campos {@code name}, {@code description}, {@code address}, {@code openingHours} e {@code isOpen}, y del
 * menú un arreglo (o {@code { items: [...] }}) de {@code { name, description, price }}.
 */
public class BusinessClient implements IBusinessClient {

    private static final String PROVIDER = "Business";

    private final RestClient client;
    private final String huariquePath;
    private final String menuPath;

    public BusinessClient(RestClient client, String huariquePath, String menuPath) {
        this.client = client;
        this.huariquePath = huariquePath;
        this.menuPath = menuPath;
    }

    @Override
    public Optional<HuariqueDetails> findHuarique(ExplorerToken token, HuariqueId id) {
        return ProviderCalls.executeOptional(PROVIDER, () -> get(token, huariquePath, id))
                .map(node -> toDetails(id, node));
    }

    @Override
    public List<MenuItem> findMenu(ExplorerToken token, HuariqueId id) {
        Optional<JsonNode> body = ProviderCalls.executeOptional(PROVIDER, () -> get(token, menuPath, id));
        List<MenuItem> items = new ArrayList<>();
        if (body.isEmpty()) {
            return items; // 404: el huarique no tiene menú
        }
        JsonNode list = body.get().isArray() ? body.get() : body.get().path("items");
        for (JsonNode item : list) {
            String name = text(item, "name");
            if (name != null) {
                BigDecimal price = item.path("price").isNumber() ? item.path("price").decimalValue() : null;
                items.add(new MenuItem(name, text(item, "description"), price));
            }
        }
        return items;
    }

    private JsonNode get(ExplorerToken token, String path, HuariqueId id) {
        return client.get()
                .uri(path, id.toString())
                .header(HttpHeaders.AUTHORIZATION, ProviderCalls.bearer(token))
                .retrieve()
                .body(JsonNode.class);
    }

    private static HuariqueDetails toDetails(HuariqueId id, JsonNode node) {
        String name = text(node, "name");
        if (name == null) {
            throw new ProviderUnavailableException(PROVIDER, "respuesta sin nombre del huarique");
        }
        JsonNode hours = node.path("openingHours");
        return new HuariqueDetails(
                id,
                name,
                text(node, "description"),
                text(node, "address"),
                hours.isMissingNode() || hours.isNull() ? null : hours.isTextual() ? hours.asText() : hours.toString(),
                node.path("isOpen").isBoolean() ? node.path("isOpen").asBoolean() : null);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank() ? value.asText() : null;
    }
}
