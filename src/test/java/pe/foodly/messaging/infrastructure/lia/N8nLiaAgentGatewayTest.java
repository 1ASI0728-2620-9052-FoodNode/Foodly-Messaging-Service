package pe.foodly.messaging.infrastructure.lia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.valueobjects.ConversationContext;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;

class N8nLiaAgentGatewayTest {

    private static final String WEBHOOK = "http://n8n.test/webhook/secreto-del-webhook-123";
    private static final ExplorerId EXPLORER = ExplorerId.of("11111111-1111-1111-1111-111111111111");

    private final Conversation conversation = Conversation.start(EXPLORER);
    private final MessageId messageId = MessageId.newId();

    private ConversationContext context(GeoCoordinates location, int previousMessages) {
        List<Message> history = new java.util.ArrayList<>();
        Conversation previous = Conversation.start(EXPLORER);
        for (int i = 0; i < previousMessages; i++) {
            history.add(i % 2 == 0
                    ? previous.addExplorerMessage(MessageContent.of("mensaje " + i))
                    : previous.addLiaReply(MessageContent.of("mensaje " + i), null));
        }
        return new ConversationContext(conversation.getId(), location, history);
    }

    private static N8nLiaAgentGateway gateway(MockRestServiceServer[] serverOut, URI target,
                                              N8nLiaAgentGateway.DirectCallback callback) {
        RestClient.Builder builder = RestClient.builder();
        serverOut[0] = MockRestServiceServer.bindTo(builder).build();
        return new N8nLiaAgentGateway(builder.build(), target, callback, "n8n");
    }

    @Test
    void integrationSystemEnviaElCuerpoDeLaSeccion35SinDatosDelCallback() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        N8nLiaAgentGateway gateway = gateway(server, URI.create("http://integration.test/lia/interpret"), null);
        server[0].expect(requestTo("http://integration.test/lia/interpret"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", "application/json"))
                .andExpect(jsonPath("$.conversationId").value(conversation.getId().toString()))
                .andExpect(jsonPath("$.messageId").value(messageId.toString()))
                .andExpect(jsonPath("$.content").value("quiero un ceviche barato cerca"))
                .andExpect(jsonPath("$.location.latitude").value(-12.12))
                .andExpect(jsonPath("$.location.longitude").value(-77.03))
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.history[0].sender").value("EXPLORER"))
                .andExpect(jsonPath("$.history[0].content").value("mensaje 0"))
                .andExpect(jsonPath("$.history[1].sender").value("LIA"))
                .andExpect(jsonPath("$.callbackUrl").doesNotExist())
                .andExpect(jsonPath("$.callbackSecret").doesNotExist())
                .andRespond(withSuccess());

        gateway.requestInterpretation(messageId, MessageContent.of("quiero un ceviche barato cerca"),
                context(new GeoCoordinates(-12.12, -77.03), 2));

        server[0].verify();
    }

    @Test
    void elHistorialLlevaSoloLosUltimosDiezMensajesEnOrden() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        N8nLiaAgentGateway gateway = gateway(server, URI.create("http://integration.test/lia"), null);
        server[0].expect(requestTo("http://integration.test/lia"))
                .andExpect(jsonPath("$.history.length()").value(10))
                .andExpect(jsonPath("$.history[0].content").value("mensaje 5"))
                .andExpect(jsonPath("$.history[9].content").value("mensaje 14"))
                .andRespond(withSuccess());

        gateway.requestInterpretation(messageId, MessageContent.of("hola"), context(null, 15));

        server[0].verify();
    }

    @Test
    void sinUbicacionLaClaveLocationVaEnNullYSinHistorialEsUnaListaVacia() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        N8nLiaAgentGateway gateway = gateway(server, URI.create("http://integration.test/lia"), null);
        server[0].expect(requestTo("http://integration.test/lia"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"location\":null")))
                .andExpect(jsonPath("$.history.length()").value(0))
                .andRespond(withSuccess());

        gateway.requestInterpretation(messageId, MessageContent.of("hola"), context(null, 0));

        server[0].verify();
    }

    @Test
    void directN8nEnviaElMismoCuerpoMasLaUrlYElSecretoDelCallback() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        N8nLiaAgentGateway gateway = gateway(server, URI.create(WEBHOOK),
                new N8nLiaAgentGateway.DirectCallback("http://messaging:8080/messaging/callbacks/n8n", "mi-secreto"));
        server[0].expect(requestTo(WEBHOOK))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.messageId").value(messageId.toString()))
                .andExpect(jsonPath("$.content").value("hola"))
                .andExpect(jsonPath("$.history").isArray())
                .andExpect(jsonPath("$.callbackUrl").value("http://messaging:8080/messaging/callbacks/n8n"))
                .andExpect(jsonPath("$.callbackSecret").value("mi-secreto"))
                .andRespond(withSuccess());

        gateway.requestInterpretation(messageId, MessageContent.of("hola"), context(null, 0));

        server[0].verify();
    }

    @Test
    void unaRespuestaDeErrorLanzaUnaExcepcionSinFiltrarLaUrlNiElContenidoNiElSecreto() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        N8nLiaAgentGateway gateway = gateway(server, URI.create(WEBHOOK),
                new N8nLiaAgentGateway.DirectCallback("http://messaging:8080/cb", "mi-secreto"));
        server[0].expect(requestTo(WEBHOOK)).andRespond(withStatus(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.TEXT_PLAIN).body("error con mi-secreto"));

        assertThatThrownBy(() -> gateway.requestInterpretation(
                        messageId, MessageContent.of("mi tarjeta es 1234"), context(null, 0)))
                .isInstanceOf(LiaGatewayException.class)
                .hasMessageContaining("n8n respondió 502")
                .hasMessageNotContaining("secreto-del-webhook")
                .hasMessageNotContaining("mi-secreto")
                .hasMessageNotContaining("1234")
                .hasNoCause();
    }

    @Test
    void siNoSePuedeConectarLanzaUnaExcepcionSinLaUrl() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        N8nLiaAgentGateway gateway = gateway(server, URI.create(WEBHOOK), null);
        server[0].expect(requestTo(WEBHOOK)).andRespond(request -> {
            throw new IOException("Connection refused: " + WEBHOOK);
        });

        assertThatThrownBy(() -> gateway.requestInterpretation(messageId, MessageContent.of("hola"), context(null, 0)))
                .isInstanceOf(LiaGatewayException.class)
                .hasMessageContaining("no respondió")
                .hasMessageNotContaining("secreto-del-webhook")
                .hasNoCause();
    }

    @Test
    void elSecretoNoApareceEnElTextoDeLosObjetos() {
        N8nLiaRequest request = new N8nLiaRequest("c", "m", "texto privado", null, List.of(), "http://cb", "mi-secreto");

        assertThat(request.toString()).doesNotContain("mi-secreto").doesNotContain("texto privado");
        assertThat(new N8nLiaAgentGateway.DirectCallback("http://cb", "mi-secreto").toString())
                .doesNotContain("mi-secreto");
    }
}
