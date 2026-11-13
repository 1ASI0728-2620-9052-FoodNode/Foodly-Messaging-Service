package pe.foodly.messaging.infrastructure.lia;

import java.net.URI;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.valueobjects.ConversationContext;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.ports.ILiaAgentGateway;

/**
 * Anti-Corruption Layer hacia n8n y OpenAI (5.6.4, DD-07). Traduce el modelo interno (el mensaje y el
 * contexto de la conversación) al formato del webhook y <b>solo envía la solicitud</b>: la respuesta no
 * es el valor de retorno, llega después por {@code POST /messaging/callbacks/n8n}, que convierte
 * {@code N8nCallbackController}. Si la entrega falla, lanza {@link LiaGatewayException}; el aviso de
 * timeout de {@code LiaTimeoutJob} cubre el caso.
 *
 * <p>Dos modos, con el mismo cuerpo:
 * <ul>
 *   <li>{@code integration-system} (DD-07): {@code POST INTEGRATION_SYSTEM_URL + LIA_PATH}.</li>
 *   <li>{@code direct-n8n} (solo local): {@code POST N8N_WEBHOOK_URL}, y el cuerpo agrega
 *       {@code callbackUrl} y {@code callbackSecret} para que n8n llame directo al callback.</li>
 * </ul>
 */
public class N8nLiaAgentGateway implements ILiaAgentGateway {

    /** Cantidad de mensajes previos que viajan en {@code history}. */
    public static final int HISTORY_LIMIT = 10;

    /** Datos que n8n necesita para llamar directo al callback (solo {@code direct-n8n}). */
    public record DirectCallback(String url, String secret) {

        @Override
        public String toString() {
            return "DirectCallback[url=" + url + ", secret=***]";
        }
    }

    private final RestClient client;
    private final URI target;
    private final DirectCallback callback;
    private final String destination;

    /**
     * @param callback {@code null} en el modo {@code integration-system}
     * @param destination nombre para los mensajes de error (sin la URL)
     */
    public N8nLiaAgentGateway(RestClient client, URI target, DirectCallback callback, String destination) {
        this.client = client;
        this.target = target;
        this.callback = callback;
        this.destination = destination;
    }

    @Override
    public void requestInterpretation(MessageId id, MessageContent content, ConversationContext ctx) {
        N8nLiaRequest body = toRequest(id, content, ctx);
        try {
            client.post()
                    .uri(target)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            throw new LiaGatewayException(destination + " respondió " + e.getStatusCode().value() + " a " + body);
        } catch (RestClientException e) {
            throw new LiaGatewayException(
                    destination + " no respondió (" + e.getClass().getSimpleName() + ") a " + body);
        }
    }

    private N8nLiaRequest toRequest(MessageId id, MessageContent content, ConversationContext ctx) {
        List<Message> previous = ctx.history();
        List<N8nLiaRequest.HistoryEntry> history = previous
                .subList(Math.max(0, previous.size() - HISTORY_LIMIT), previous.size()).stream()
                .map(message -> new N8nLiaRequest.HistoryEntry(
                        message.getSender().name(), message.getContent().text()))
                .toList();
        return new N8nLiaRequest(
                ctx.conversationId().toString(),
                id.toString(),
                content.text(),
                ctx.location() == null
                        ? null
                        : new N8nLiaRequest.Location(ctx.location().latitude(), ctx.location().longitude()),
                history,
                callback == null ? null : callback.url(),
                callback == null ? null : callback.secret());
    }
}
