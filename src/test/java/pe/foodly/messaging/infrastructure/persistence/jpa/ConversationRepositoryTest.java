package pe.foodly.messaging.infrastructure.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.enums.ConversationStatus;
import pe.foodly.messaging.domain.model.enums.IntentType;
import pe.foodly.messaging.domain.model.enums.SenderType;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.PriceRange;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;

/**
 * Persistencia contra un MySQL 8 real (Flyway crea el esquema). Se omite si no hay Docker.
 * Sin transacción de prueba, para que cada lectura vaya de verdad a la base y no al contexto de persistencia.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
@Import({ConversationRepository.class, ConversationMapper.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ConversationRepositoryTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.hikari.data-source-properties.sslMode", () -> "DISABLED");
    }

    private static final ExplorerId EXPLORER = ExplorerId.of("11111111-1111-1111-1111-111111111111");
    private static final ExplorerId OTHER_EXPLORER = ExplorerId.of("99999999-9999-9999-9999-999999999999");
    private static final HuariqueId HUARIQUE = HuariqueId.of("22222222-2222-2222-2222-222222222222");
    private static final ReservationId RESERVATION = ReservationId.of("33333333-3333-3333-3333-333333333333");

    @Autowired
    private ConversationRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private TestClock clock;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM messages");
        jdbc.update("DELETE FROM conversations");
        clock = new TestClock(Instant.parse("2026-11-13T15:00:00Z"));
    }

    @Test
    void guardaUnaConversacionConMensajesYLaReconstruye() {
        Conversation conversation = Conversation.start(EXPLORER, clock);
        Message question = conversation.addExplorerMessage(MessageContent.of("quiero una mesa para 4 mañana 8 pm"));
        clock.advance(Duration.ofMillis(1));
        UserIntent intent = new UserIntent(
                IntentType.RESERVATION_REQUEST,
                "ceviche",
                new PriceRange(BigDecimal.ZERO, new BigDecimal("25.50")),
                new GeoCoordinates(-12.12, -77.03),
                4,
                LocalDateTime.of(2026, 11, 14, 20, 0),
                HUARIQUE);
        Message reply = conversation.addLiaReply(MessageContent.of("¡Listo! Te reservo la mesa. 🍤"), intent, question.getId());
        clock.advance(Duration.ofMillis(1));
        Message timeout = conversation.addLiaReply(MessageContent.of("No pude completar la acción."), null, question.getId());
        conversation.linkReservation(question.getId(), RESERVATION);

        repository.save(conversation);
        Conversation loaded = repository.findById(conversation.getId()).orElseThrow();

        assertThat(loaded.getId()).isEqualTo(conversation.getId());
        assertThat(loaded.getExplorerId()).isEqualTo(EXPLORER);
        assertThat(loaded.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(loaded.getStartedAt()).isEqualTo(conversation.getStartedAt());
        assertThat(loaded.getLastMessageAt()).isEqualTo(timeout.getSentAt());
        assertThat(loaded.getMessages()).extracting(Message::getId)
                .containsExactly(question.getId(), reply.getId(), timeout.getId());

        Message loadedQuestion = loaded.getMessages().get(0);
        assertThat(loadedQuestion.getSender()).isEqualTo(SenderType.EXPLORER);
        assertThat(loadedQuestion.getContent()).isEqualTo(question.getContent());
        assertThat(loadedQuestion.getSentAt()).isEqualTo(question.getSentAt());
        assertThat(loadedQuestion.getIntent()).isEmpty();
        assertThat(loadedQuestion.getInReplyToMessageId()).isEmpty();
        assertThat(loadedQuestion.getReservationId()).contains(RESERVATION);

        Message loadedReply = loaded.getMessages().get(1);
        assertThat(loadedReply.isFromLia()).isTrue();
        assertThat(loadedReply.getContent().text()).isEqualTo("¡Listo! Te reservo la mesa. 🍤");
        assertThat(loadedReply.getHuariqueId()).contains(HUARIQUE);
        assertThat(loadedReply.getInReplyToMessageId()).contains(question.getId());
        UserIntent loadedIntent = loadedReply.getIntent().orElseThrow();
        assertThat(loadedIntent.type()).isEqualTo(IntentType.RESERVATION_REQUEST);
        assertThat(loadedIntent.cravings()).isEqualTo("ceviche");
        assertThat(loadedIntent.budgetRange().min()).isEqualByComparingTo("0");
        assertThat(loadedIntent.budgetRange().max()).isEqualByComparingTo("25.50");
        assertThat(loadedIntent.location()).isEqualTo(new GeoCoordinates(-12.12, -77.03));
        assertThat(loadedIntent.partySize()).isEqualTo(4);
        assertThat(loadedIntent.requestedAt()).isEqualTo(LocalDateTime.of(2026, 11, 14, 20, 0));
        assertThat(loadedIntent.huariqueId()).isEqualTo(HUARIQUE);

        Message loadedTimeout = loaded.getMessages().get(2);
        assertThat(loadedTimeout.getIntent()).isEmpty();
        assertThat(loadedTimeout.getInReplyToMessageId()).contains(question.getId());
    }

    @Test
    void intentDataSeGuardaComoObjetoJsonConLaCorrelacionEnLaRaiz() {
        Conversation conversation = Conversation.start(EXPLORER, clock);
        Message question = conversation.addExplorerMessage(MessageContent.of("hola"));
        UserIntent intent = new UserIntent(IntentType.RECOMMENDATION_REQUEST, "ceviche", null,
                new GeoCoordinates(-12.12, -77.03), null, null, null);
        Message reply = conversation.addLiaReply(MessageContent.of("Te busco opciones."), intent, question.getId());
        Message timeout = conversation.addLiaReply(MessageContent.of("Sin respuesta."), null, question.getId());
        repository.save(conversation);

        // Lo que hará LiaTimeoutJob: JSON_EXTRACT sobre la raíz de intent_data.
        List<String> repliesToQuestion = jdbc.queryForList(
                "SELECT id FROM messages WHERE sender = 'LIA'"
                        + " AND JSON_UNQUOTE(JSON_EXTRACT(intent_data, '$.inReplyToMessageId')) = ?",
                String.class, question.getId().toString());
        assertThat(repliesToQuestion).containsExactlyInAnyOrder(reply.getId().toString(), timeout.getId().toString());

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT intent_type, JSON_TYPE(intent_data) AS json_type,"
                        + " JSON_UNQUOTE(JSON_EXTRACT(intent_data, '$.cravings')) AS cravings"
                        + " FROM messages WHERE id = ?", reply.getId().toString());
        assertThat(row.get("intent_type")).isEqualTo("RECOMMENDATION_REQUEST");
        assertThat(row.get("json_type")).isEqualTo("OBJECT");
        assertThat(row.get("cravings")).isEqualTo("ceviche");

        // Mensajes sin intención ni correlación: intent_type e intent_data quedan en NULL.
        Map<String, Object> explorerRow = jdbc.queryForMap(
                "SELECT intent_type, intent_data, huarique_id, reservation_id FROM messages WHERE id = ?",
                question.getId().toString());
        assertThat(explorerRow.values()).containsOnlyNulls();
    }

    @Test
    void guardarDeNuevoActualizaLaConversacionSinDuplicarMensajes() {
        Conversation conversation = Conversation.start(EXPLORER, clock);
        Message first = conversation.addExplorerMessage(MessageContent.of("uno"));
        repository.save(conversation);

        clock.advance(Duration.ofSeconds(2));
        Conversation reloaded = repository.findById(conversation.getId()).orElseThrow();
        Message second = reloaded.addLiaReply(MessageContent.of("dos"), null, first.getId());
        reloaded.linkReservation(first.getId(), RESERVATION);
        reloaded.close();
        repository.save(reloaded);

        Conversation updated = repository.findById(conversation.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(ConversationStatus.CLOSED);
        assertThat(updated.getMessages()).extracting(Message::getId).containsExactly(first.getId(), second.getId());
        assertThat(updated.getMessages().get(0).getReservationId()).contains(RESERVATION);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM messages", Integer.class)).isEqualTo(2);
    }

    @Test
    void findByIdDevuelveVacioSiNoExiste() {
        assertThat(repository.findById(ConversationId.newId())).isEmpty();
    }

    @Test
    void findActiveByExplorerIdDevuelveSoloLaConversacionActivaDeEseExplorador() {
        Conversation closed = Conversation.start(EXPLORER, clock);
        closed.addExplorerMessage(MessageContent.of("vieja"));
        closed.close();
        clock.advance(Duration.ofMinutes(1));
        Conversation active = Conversation.start(EXPLORER, clock);
        active.addExplorerMessage(MessageContent.of("nueva"));
        Conversation otherExplorers = Conversation.start(OTHER_EXPLORER, clock);
        repository.save(closed);
        repository.save(active);
        repository.save(otherExplorers);

        Optional<Conversation> found = repository.findActiveByExplorerId(EXPLORER);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(active.getId());
        assertThat(found.get().isActive()).isTrue();
        assertThat(found.get().getMessages()).hasSize(1);
    }

    @Test
    void findActiveByExplorerIdDevuelveVacioSiTodasEstanCerradasOElExploradorNoTieneNinguna() {
        Conversation closed = Conversation.start(EXPLORER, clock);
        closed.close();
        repository.save(closed);

        assertThat(repository.findActiveByExplorerId(EXPLORER)).isEmpty();
        assertThat(repository.findActiveByExplorerId(OTHER_EXPLORER)).isEmpty();
    }

    @Test
    void findAllByExplorerIdOrdenaPorLastMessageAtDescendente() {
        Conversation oldest = Conversation.start(EXPLORER, clock);
        oldest.addExplorerMessage(MessageContent.of("a"));
        clock.advance(Duration.ofMinutes(1));
        Conversation newest = Conversation.start(EXPLORER, clock);
        newest.addExplorerMessage(MessageContent.of("b"));
        clock.advance(Duration.ofMinutes(1));
        Conversation middle = Conversation.start(EXPLORER, clock);
        middle.addExplorerMessage(MessageContent.of("c"));
        // La más antigua recibe un mensaje al final: su last_message_at pasa a ser el más reciente.
        clock.advance(Duration.ofMinutes(1));
        oldest.addLiaReply(MessageContent.of("respuesta tardía"), null);
        Conversation foreign = Conversation.start(OTHER_EXPLORER, clock);
        foreign.addExplorerMessage(MessageContent.of("ajena"));
        // Se guardan en un orden que no coincide con el esperado.
        repository.save(middle);
        repository.save(oldest);
        repository.save(foreign);
        repository.save(newest);

        List<Conversation> all = repository.findAllByExplorerId(EXPLORER);

        assertThat(all).extracting(Conversation::getId)
                .containsExactly(oldest.getId(), middle.getId(), newest.getId());
        assertThat(all).extracting(Conversation::getLastMessageAt).isSortedAccordingTo(
                java.util.Comparator.<LocalDateTime>naturalOrder().reversed());
        assertThat(all.get(0).getMessages()).hasSize(2);
        assertThat(repository.findAllByExplorerId(ExplorerId.of("00000000-0000-0000-0000-000000000000"))).isEmpty();
    }

    @Test
    void elEsquemaCoincideConElDiagrama5672() {
        // Una sola FK física: messages.conversation_id → conversations.id. Ninguna en explorer_id,
        // huarique_id ni reservation_id.
        List<Map<String, Object>> foreignKeys = jdbc.queryForList(
                "SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME"
                        + " FROM information_schema.KEY_COLUMN_USAGE"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL");
        assertThat(foreignKeys).hasSize(1);
        assertThat(foreignKeys.get(0))
                .containsEntry("TABLE_NAME", "messages")
                .containsEntry("COLUMN_NAME", "conversation_id")
                .containsEntry("REFERENCED_TABLE_NAME", "conversations")
                .containsEntry("REFERENCED_COLUMN_NAME", "id");

        assertThat(indexColumns("conversations", "idx_conversations_explorer_status"))
                .containsExactly("explorer_id", "status");
        assertThat(indexColumns("messages", "idx_messages_conversation_sent_at"))
                .containsExactly("conversation_id", "sent_at");

        assertThat(jdbc.queryForList(
                        "SELECT COLUMN_NAME FROM information_schema.COLUMNS"
                                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'messages'"
                                + " ORDER BY ORDINAL_POSITION", String.class))
                .containsExactly("id", "conversation_id", "sender", "content", "intent_type", "intent_data",
                        "huarique_id", "reservation_id", "sent_at");
        assertThat(jdbc.queryForList(
                        "SELECT COLUMN_NAME FROM information_schema.COLUMNS"
                                + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'conversations'"
                                + " ORDER BY ORDINAL_POSITION", String.class))
                .containsExactly("id", "explorer_id", "status", "started_at", "last_message_at");
    }

    private List<String> indexColumns(String table, String index) {
        return jdbc.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.STATISTICS"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?"
                        + " ORDER BY SEQ_IN_INDEX", String.class, table, index);
    }

    /** Reloj de prueba con precisión de milisegundos, que avanza solo cuando se le pide. */
    private static final class TestClock extends Clock {
        private Instant now;

        TestClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
