package pe.foodly.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.commandhandlers.RegisterLiaReplyCommandHandler;
import pe.foodly.messaging.application.commandhandlers.SendMessageCommandHandler;
import pe.foodly.messaging.application.commands.RegisterLiaReplyCommand;
import pe.foodly.messaging.application.commands.SendMessageCommand;
import pe.foodly.messaging.application.commands.SendMessageResult;
import pe.foodly.messaging.application.outboundports.IExplorerTokenCache;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.enums.IntentType;
import pe.foodly.messaging.domain.model.valueobjects.ConversationContext;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;
import pe.foodly.messaging.domain.ports.ILiaAgentGateway;
import pe.foodly.messaging.domain.repositories.IConversationRepository;

/**
 * Cableado real de la Application Layer: MySQL 8, perfil {@code stub} (clientes falsos de Lima) y un
 * gateway de Lía de prueba. Verifica lo que los mocks no pueden: que la solicitud a Lía sale
 * <b>después del commit</b>, en el executor propio, y que las respuestas se persisten de verdad.
 * Se omite si no hay Docker.
 */
@SpringBootTest(properties = {
        "messaging.security.jwt.secret=una-clave-de-prueba-de-al-menos-32-bytes!!",
        "messaging.security.callback.secret=secreto-de-callback-de-prueba",
        // El job de timeout no debe interferir: aquí no se prueba.
        "messaging.lia.timeout-job.interval-ms=3600000"})
@ActiveProfiles("stub")
@Testcontainers(disabledWithoutDocker = true)
class MessagingFlowIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.hikari.data-source-properties.sslMode", () -> "DISABLED");
    }

    /** Anota cuándo y en qué hilo se la llamó, y si el mensaje ya estaba en la base en ese momento. */
    static class RecordingGateway implements ILiaAgentGateway {
        record Call(MessageId messageId, String thread, int rowsInDatabase) {
        }

        final List<Call> calls = new CopyOnWriteArrayList<>();
        private final JdbcTemplate jdbc;

        RecordingGateway(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public void requestInterpretation(MessageId id, MessageContent content, ConversationContext ctx) {
            Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE id = ?", Integer.class, id.toString());
            calls.add(new Call(id, Thread.currentThread().getName(), rows == null ? -1 : rows));
        }
    }

    @TestConfiguration
    static class GatewayConfig {
        @Bean
        @Primary
        RecordingGateway recordingGateway(JdbcTemplate jdbc) {
            return new RecordingGateway(jdbc);
        }
    }

    private static final ExplorerId EXPLORER = ExplorerId.of("11111111-1111-1111-1111-111111111111");
    private static final HuariqueId FARO = HuariqueId.of("a1111111-1111-4111-8111-111111111111");
    private static final GeoCoordinates MIRAFLORES = new GeoCoordinates(-12.1219, -77.0297);

    @Autowired
    private SendMessageCommandHandler send;
    @Autowired
    private RegisterLiaReplyCommandHandler register;
    @Autowired
    private IConversationRepository conversations;
    @Autowired
    private IExplorerTokenCache tokenCache;
    @Autowired
    private RecordingGateway gateway;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM messages");
        jdbc.update("DELETE FROM conversations");
        gateway.calls.clear();
    }

    private SendMessageResult sendMessage(String text) {
        return send.handle(new SendMessageCommand(
                EXPLORER, null, MessageContent.of(text), MIRAFLORES,
                new ExplorerToken("jwt-del-explorador", Instant.now().plusSeconds(3600))));
    }

    @Test
    void laSolicitudALiaSaleDespuesDelCommitYEnElExecutorPropio() {
        SendMessageResult result = sendMessage("quiero un ceviche barato cerca");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(gateway.calls).hasSize(1));
        RecordingGateway.Call call = gateway.calls.get(0);
        assertThat(call.messageId()).isEqualTo(result.messageId());
        assertThat(call.rowsInDatabase()).as("el mensaje ya estaba confirmado en la base").isEqualTo(1);
        assertThat(call.thread()).startsWith("lia-");
        assertThat(tokenCache.find(result.messageId())).isPresent();
    }

    @Test
    void unaRecomendacionConLosClientesDeLimaSePersisteConSuCorrelacion() {
        SendMessageResult sent = sendMessage("quiero un ceviche barato cerca");
        UserIntent intent = new UserIntent(IntentType.RECOMMENDATION_REQUEST, "ceviche", null, MIRAFLORES, null, null, null);

        Optional<Message> reply = register.handle(new RegisterLiaReplyCommand(
                sent.conversationId(), sent.messageId(), "¡Claro! Te busco opciones cerca…", intent, null, null));

        assertThat(reply).isPresent();
        Conversation stored = conversations.findById(sent.conversationId()).orElseThrow();
        assertThat(stored.getMessages()).hasSize(2);
        Message liaReply = stored.getMessages().get(1);
        assertThat(liaReply.isFromLia()).isTrue();
        assertThat(liaReply.getInReplyToMessageId()).contains(sent.messageId());
        assertThat(liaReply.getContent().text())
                .startsWith("¡Claro!")
                .contains("1. Cevichería El Faro Miraflorino (a ");
        assertThat(tokenCache.find(sent.messageId())).as("el token se retira tras responder").isEmpty();
    }

    @Test
    void unaReservaCompletaQuedaVinculadaAlMensajeDelExplorador() {
        SendMessageResult sent = sendMessage("reserva para 4 mañana a las 8 pm en El Faro");
        UserIntent intent = new UserIntent(
                IntentType.RESERVATION_REQUEST, null, null, null, 4, LocalDateTime.of(2026, 11, 14, 20, 0), FARO);

        Message reply = register.handle(new RegisterLiaReplyCommand(
                        sent.conversationId(), sent.messageId(), "Listo", intent, null, null))
                .orElseThrow();

        Conversation stored = conversations.findById(sent.conversationId()).orElseThrow();
        Message question = stored.getMessages().get(0);
        assertThat(question.getId()).isEqualTo(sent.messageId());
        assertThat(question.hasReservation()).isTrue();
        assertThat(jdbc.queryForObject("SELECT reservation_id FROM messages WHERE id = ?", String.class,
                        sent.messageId().toString()))
                .isEqualTo(question.getReservationId().orElseThrow().toString());
        assertThat(reply.getHuariqueId()).contains(FARO);
        assertThat(jdbc.queryForObject("SELECT huarique_id FROM messages WHERE id = ?", String.class,
                        reply.getId().toString()))
                .isEqualTo(FARO.toString());
    }

    @Test
    void elSegundoCallbackDelMismoMensajeSeIgnora() {
        SendMessageResult sent = sendMessage("hola");
        RegisterLiaReplyCommand callback = new RegisterLiaReplyCommand(
                sent.conversationId(), sent.messageId(), "¡Hola!", null, null, null);

        assertThat(register.handle(callback)).isPresent();
        assertThat(register.handle(callback)).isEmpty();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages WHERE sender = 'LIA'", Integer.class)).isEqualTo(1);
    }
}
