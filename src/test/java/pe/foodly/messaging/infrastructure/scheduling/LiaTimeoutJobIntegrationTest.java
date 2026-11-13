package pe.foodly.messaging.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
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
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.valueobjects.ConversationContext;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.ports.ILiaAgentGateway;
import pe.foodly.messaging.domain.repositories.IConversationRepository;

/**
 * {@code LiaTimeoutJob} con MySQL real: la consulta nativa con {@code JSON_EXTRACT}, el aviso y su
 * idempotencia. El tiempo de espera de Lía es 1 s y el job se invoca a mano ({@code interval-ms} de 1 h
 * evita que corra solo). Se omite si no hay Docker.
 */
@SpringBootTest(properties = {
        "messaging.security.jwt.secret=una-clave-de-prueba-de-al-menos-32-bytes!!",
        "messaging.security.callback.secret=secreto-de-callback-de-prueba",
        "messaging.lia.timeout-seconds=1",
        "messaging.lia.timeout-job.interval-ms=3600000"})
@ActiveProfiles("stub")
@Testcontainers(disabledWithoutDocker = true)
class LiaTimeoutJobIntegrationTest {

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
            return (MessageId id, MessageContent content, ConversationContext ctx) -> { /* nunca responde */ };
        }
    }

    private static final ExplorerId EXPLORER = ExplorerId.of("11111111-1111-1111-1111-111111111111");

    @Autowired
    private LiaTimeoutJob job;
    @Autowired
    private SendMessageCommandHandler send;
    @Autowired
    private RegisterLiaReplyCommandHandler register;
    @Autowired
    private IConversationRepository conversations;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM messages");
        jdbc.update("DELETE FROM conversations");
    }

    private SendMessageResult sendMessage(String text) {
        return send.handle(new SendMessageCommand(EXPLORER, null, MessageContent.of(text), null,
                new ExplorerToken("jwt-del-explorador", Instant.now().plusSeconds(3600))));
    }

    private static void waitPastTimeout() throws InterruptedException {
        Thread.sleep(1_300);
    }

    private List<Message> liaMessages(SendMessageResult sent) {
        return conversations.findById(sent.conversationId()).orElseThrow().getMessages().stream()
                .filter(Message::isFromLia)
                .toList();
    }

    @Test
    void unMensajeSinRespuestaRecibeElAvisoDeReservaManualUnaSolaVez() throws Exception {
        SendMessageResult sent = sendMessage("quiero una mesa para hoy");

        assertThat(job.checkTimeouts()).as("todavía no pasó el tiempo de espera").isZero();
        waitPastTimeout();
        assertThat(job.checkTimeouts()).isEqualTo(1);

        List<Message> lia = liaMessages(sent);
        assertThat(lia).hasSize(1);
        assertThat(lia.get(0).getInReplyToMessageId()).contains(sent.messageId());
        assertThat(lia.get(0).getContent().text()).contains("reserva de forma manual");
        // La correlación quedó dentro de intent_data, sin columnas nuevas.
        assertThat(jdbc.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(intent_data, '$.inReplyToMessageId')) FROM messages WHERE id = ?",
                String.class, lia.get(0).getId().toString())).isEqualTo(sent.messageId().toString());

        // Idempotente: otra pasada no vuelve a avisar.
        assertThat(job.checkTimeouts()).isZero();
        assertThat(liaMessages(sent)).hasSize(1);
    }

    @Test
    void unMensajeYaRespondidoPorLiaNoRecibeAviso() throws Exception {
        SendMessageResult sent = sendMessage("hola");
        register.handle(new RegisterLiaReplyCommand(sent.conversationId(), sent.messageId(), "¡Hola!", null, null, null));

        waitPastTimeout();

        assertThat(job.checkTimeouts()).isZero();
        assertThat(liaMessages(sent)).hasSize(1);
        // Es la respuesta del callback, no un aviso de timeout.
        assertThat(liaMessages(sent).get(0).getContent().text()).startsWith("¡Hola!").doesNotContain("manual");
    }

    @Test
    void unCallbackTardioDespuesDelAvisoSeIgnoraYNoReemplazaElAviso() throws Exception {
        SendMessageResult sent = sendMessage("quiero una mesa para hoy");
        waitPastTimeout();
        job.checkTimeouts();
        Message notice = liaMessages(sent).get(0);

        var late = register.handle(new RegisterLiaReplyCommand(
                sent.conversationId(), sent.messageId(), "¡Listo, ya reservé!", null, null, null));

        assertThat(late).isEmpty();
        assertThat(liaMessages(sent)).containsExactly(notice);
    }

    @Test
    void elExploradorPuedeVolverAEscribirYElNuevoMensajeTieneSuPropioTimeout() throws Exception {
        SendMessageResult first = sendMessage("hola");
        waitPastTimeout();
        job.checkTimeouts();

        SendMessageResult second = sendMessage("¿estás ahí?");
        assertThat(second.conversationId()).isEqualTo(first.conversationId());
        assertThat(job.checkTimeouts()).as("el mensaje nuevo todavía está a tiempo").isZero();
        waitPastTimeout();
        assertThat(job.checkTimeouts()).isEqualTo(1);

        assertThat(liaMessages(second)).hasSize(2);
        assertThat(liaMessages(second)).extracting(m -> m.getInReplyToMessageId().orElseThrow())
                .containsExactly(first.messageId(), second.messageId());
    }

    @Test
    void unaConversacionCerradaNoRecibeAvisos() throws Exception {
        SendMessageResult sent = sendMessage("hola");
        Conversation conversation = conversations.findById(sent.conversationId()).orElseThrow();
        conversation.close();
        conversations.save(conversation);

        waitPastTimeout();

        assertThat(job.checkTimeouts()).isZero();
        assertThat(liaMessages(sent)).isEmpty();
    }

    @Test
    void unaConversacionFueraDeLaVentanaDeBusquedaNoSeRevisa() throws Exception {
        SendMessageResult sent = sendMessage("hola");
        jdbc.update("UPDATE conversations SET last_message_at = DATE_SUB(NOW(), INTERVAL 30 HOUR) WHERE id = ?",
                sent.conversationId().toString());
        waitPastTimeout();

        assertThat(job.checkTimeouts()).isZero();
        assertThat(liaMessages(sent)).isEmpty();
    }
}
