package pe.foodly.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.foodly.messaging.domain.model.valueobjects.ConversationContext;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.ports.ILiaAgentGateway;
import pe.foodly.messaging.support.TestJwt;

/**
 * Flujo completo contra el servidor real (puerto aleatorio, MySQL 8, perfil {@code stub}): el explorador
 * escribe por REST, se suscribe por STOMP, el Integration System llama al callback y la respuesta de Lía
 * llega por el canal. Se omite si no hay Docker.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "messaging.security.jwt.secret-base64=" + TestJwt.SECRET_BASE64,
            "messaging.security.callback.secret=" + TestJwt.CALLBACK_SECRET,
            "messaging.lia.timeout-job.interval-ms=3600000"
        })
@ActiveProfiles("stub")
@Testcontainers(disabledWithoutDocker = true)
class MessagingWebSocketIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.hikari.data-source-properties.sslMode", () -> "DISABLED");
    }

    @TestConfiguration
    static class GatewayConfig {
        @Bean
        @Primary
        ILiaAgentGateway testLiaAgentGateway() {
            return (MessageId id, MessageContent content, ConversationContext ctx) -> { /* Lía responde por el callback */ };
        }
    }

    private static final String EXPLORER = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER_EXPLORER = "99999999-9999-9999-9999-999999999999";

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SimpUserRegistry userRegistry;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM messages");
        jdbc.update("DELETE FROM conversations");
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        // El cliente necesita un scheduler para rastrear recibos (receipts) y enviar latidos.
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-test-client-");
        scheduler.initialize();
        stompClient.setTaskScheduler(scheduler);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        stompClient.setMessageConverter(converter);
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders bearer(String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Map<String, Object> sendChat(String jwt, String body) {
        ResponseEntity<Map<String, Object>> response = rest.exchange(url("/messaging/chat"), HttpMethod.POST,
                new HttpEntity<>(body, bearer(jwt)), new ParameterizedTypeReference<>() { });
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        return response.getBody();
    }

    /** Conecta con el JWT dado y devuelve la sesión, con los frames ERROR del servidor en {@code errors}. */
    private StompSession connect(String jwt, CompletableFuture<String> errors) throws Exception {
        StompHeaders headers = new StompHeaders();
        if (jwt != null) {
            headers.add("Authorization", "Bearer " + jwt);
        }
        return stompClient.connectAsync(
                        "ws://localhost:" + port + "/messaging/ws", new WebSocketHttpHeaders(), headers,
                        new StompSessionHandlerAdapter() {
                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                errors.complete(headers.getFirst("message"));
                            }

                            @Override
                            public void handleTransportError(StompSession session, Throwable exception) {
                                errors.complete("transporte: " + exception.getMessage());
                            }
                        })
                .get(10, TimeUnit.SECONDS);
    }

    /**
     * Se suscribe y espera a que el servidor registre la suscripción. El broker simple de Spring no emite
     * frames RECEIPT, así que se consulta su registro de usuarios; sin esto la prueba podría publicar antes
     * de que la suscripción exista.
     */
    private BlockingQueue<Map<String, Object>> subscribe(
            StompSession session, String conversationId, CompletableFuture<String> errors) {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        String destination = "/topic/conversations/" + conversationId;
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        await().atMost(Duration.ofSeconds(10))
                .alias("suscripción registrada (error del servidor: " + errors.getNow("ninguno") + ")")
                .until(() -> !userRegistry.findSubscriptions(
                        subscription -> destination.equals(subscription.getDestination())).isEmpty());
        return received;
    }

    private String callbackBody(String conversationId, String messageId) {
        return """
                { "conversationId": "%s", "inReplyToMessageId": "%s",
                  "replyText": "¡Claro! Te busco opciones cerca…",
                  "intent": { "type": "RECOMMENDATION_REQUEST", "cravings": "ceviche",
                              "location": { "latitude": -12.1219, "longitude": -77.0297 } },
                  "suggestedHuariques": [] }""".formatted(conversationId, messageId);
    }

    private ResponseEntity<Map<String, Object>> callback(String body, String secret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Callback-Secret", secret);
        return rest.exchange(url("/messaging/callbacks/n8n"), HttpMethod.POST,
                new HttpEntity<>(body, headers), new ParameterizedTypeReference<>() { });
    }

    @Test
    void laRespuestaDeLiaLlegaPorStompYQuedaDisponibleEnElHistorial() throws Exception {
        String jwt = TestJwt.explorer(EXPLORER);
        Map<String, Object> accepted = sendChat(jwt,
                "{ \"content\": \"quiero un ceviche barato cerca\", \"latitude\": -12.1219, \"longitude\": -77.0297 }");
        String conversationId = (String) accepted.get("conversationId");
        String messageId = (String) accepted.get("messageId");
        assertThat(accepted.get("status")).isEqualTo("PROCESSING");

        CompletableFuture<String> errors = new CompletableFuture<>();
        StompSession session = connect(jwt, errors);
        BlockingQueue<Map<String, Object>> received = subscribe(session, conversationId, errors);

        ResponseEntity<Map<String, Object>> ack = callback(callbackBody(conversationId, messageId), TestJwt.CALLBACK_SECRET);
        assertThat(ack.getStatusCode().value()).isEqualTo(202);
        assertThat(ack.getBody()).containsEntry("status", "RECEIVED");

        Map<String, Object> pushed = received.poll(15, TimeUnit.SECONDS);
        assertThat(pushed).as("mensaje publicado por STOMP").isNotNull();
        assertThat(pushed.get("sender")).isEqualTo("LIA");
        assertThat((String) pushed.get("content"))
                .startsWith("¡Claro!")
                .contains("Cevichería El Faro Miraflorino");
        assertThat(pushed.get("intentType")).isEqualTo("RECOMMENDATION_REQUEST");

        // GET .../messages es la fuente de verdad: devuelve el mismo mensaje que llegó por STOMP.
        ResponseEntity<List<Map<String, Object>>> history = rest.exchange(
                url("/messaging/conversations/" + conversationId + "/messages"), HttpMethod.GET,
                new HttpEntity<>(bearer(jwt)), new ParameterizedTypeReference<>() { });
        assertThat(history.getStatusCode().value()).isEqualTo(200);
        assertThat(history.getBody()).extracting(m -> m.get("id")).containsExactly(messageId, pushed.get("id"));

        // Un callback repetido no publica un segundo mensaje.
        callback(callbackBody(conversationId, messageId), TestJwt.CALLBACK_SECRET);
        assertThat(received.poll(1, TimeUnit.SECONDS)).isNull();
    }

    @Test
    void unExploradorNoPuedeSuscribirseALaConversacionDeOtro() throws Exception {
        Map<String, Object> accepted = sendChat(TestJwt.explorer(EXPLORER), "{ \"content\": \"hola\" }");
        String conversationId = (String) accepted.get("conversationId");

        CompletableFuture<String> errors = new CompletableFuture<>();
        StompSession intruder = connect(TestJwt.explorer(OTHER_EXPLORER), errors);
        intruder.subscribe("/topic/conversations/" + conversationId, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
            }
        });

        assertThat(errors.get(10, TimeUnit.SECONDS)).contains("conversaciones propias");
    }

    @Test
    void elCanalRechazaLaConexionSinTokenOConRolDeDueno() throws Exception {
        for (String jwt : new String[] {null, TestJwt.owner(EXPLORER), "no-es-un-jwt",
                TestJwt.withIssuer(EXPLORER, "otro-emisor"),
                TestJwt.withAudience(EXPLORER, java.util.List.of("foodly-iam", "foodly-business")),
                TestJwt.signedWithTheBase64TextAsKey(EXPLORER)}) {
            CompletableFuture<String> errors = new CompletableFuture<>();
            stompClient.connectAsync("ws://localhost:" + port + "/messaging/ws", new WebSocketHttpHeaders(),
                    headersWith(jwt), new StompSessionHandlerAdapter() {
                        @Override
                        public void handleFrame(StompHeaders headers, Object payload) {
                            errors.complete(headers.getFirst("message"));
                        }

                        @Override
                        public void handleTransportError(StompSession session, Throwable exception) {
                            errors.complete("transporte");
                        }
                    });

            assertThat(errors.get(10, TimeUnit.SECONDS)).as("jwt=%s", jwt == null ? "ninguno" : "presente").isNotBlank();
        }
    }

    private static StompHeaders headersWith(String jwt) {
        StompHeaders headers = new StompHeaders();
        if (jwt != null) {
            headers.add("Authorization", "Bearer " + jwt);
        }
        return headers;
    }

    @Test
    void elCallbackConSecretoIncorrectoDa401EnElServidorReal() {
        ResponseEntity<Map<String, Object>> response = callback(
                callbackBody("5b0d3e8a-6f43-4a0e-9d38-2d7b1c4f9a10", "0f6b8c52-1d3a-4c7e-8a55-9b1e2f3a4d60"), "incorrecto");

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).containsEntry("status", 401);
    }
}
