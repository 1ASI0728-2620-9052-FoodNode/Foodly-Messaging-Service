package pe.foodly.messaging.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.foodly.messaging.domain.exceptions.ConversationClosedException;
import pe.foodly.messaging.domain.exceptions.MessageNotFoundException;
import pe.foodly.messaging.domain.exceptions.ReservationAlreadyLinkedException;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.enums.ConversationStatus;
import pe.foodly.messaging.domain.model.enums.IntentType;
import pe.foodly.messaging.domain.model.enums.SenderType;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;

class ConversationTest {

    private static final ExplorerId EXPLORER = ExplorerId.of("11111111-1111-1111-1111-111111111111");

    private MutableClock clock;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-13T15:00:00Z"));
        conversation = Conversation.start(EXPLORER, clock);
    }

    @Test
    void unaConversacionNuevaEstaActivaYVacia() {
        assertThat(conversation.isActive()).isTrue();
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
        assertThat(conversation.getExplorerId()).isEqualTo(EXPLORER);
        assertThat(conversation.getMessages()).isEmpty();
        assertThat(conversation.getLastMessageAt()).isEqualTo(conversation.getStartedAt());
    }

    @Test
    void addExplorerMessageRegistraElMensajeYActualizaLastMessageAt() {
        clock.advance(Duration.ofSeconds(30));

        Message message = conversation.addExplorerMessage(MessageContent.of("quiero un ceviche"));

        assertThat(message.getSender()).isEqualTo(SenderType.EXPLORER);
        assertThat(message.isFromLia()).isFalse();
        assertThat(message.hasReservation()).isFalse();
        assertThat(message.getIntent()).isEmpty();
        assertThat(message.getHuariqueId()).isEmpty();
        assertThat(message.getSentAt()).isEqualTo(LocalDateTime.of(2026, 11, 13, 15, 0, 30));
        assertThat(conversation.getMessages()).containsExactly(message);
        assertThat(conversation.getLastMessageAt()).isEqualTo(message.getSentAt());
    }

    @Test
    void addLiaReplyRegistraLaRespuestaConSuIntencionYHeredaElHuarique() {
        HuariqueId huarique = HuariqueId.of("22222222-2222-2222-2222-222222222222");
        UserIntent intent = new UserIntent(
                IntentType.HUARIQUE_INQUIRY, null, null, null, null, null, huarique);
        clock.advance(Duration.ofSeconds(5));

        Message reply = conversation.addLiaReply(MessageContent.of("Abre hasta las 10 pm."), intent);

        assertThat(reply.isFromLia()).isTrue();
        assertThat(reply.getIntent()).contains(intent);
        assertThat(reply.getHuariqueId()).contains(huarique);
        assertThat(conversation.getLastMessageAt()).isEqualTo(reply.getSentAt());
    }

    @Test
    void addLiaReplyAceptaUnaRespuestaSinIntencion() {
        Message reply = conversation.addLiaReply(MessageContent.of("No pude completar la acción."), null);

        assertThat(reply.getIntent()).isEmpty();
        assertThat(reply.getHuariqueId()).isEmpty();
    }

    @Test
    void addLiaReplyRegistraAQueMensajeDelExploradorResponde() {
        Message question = conversation.addExplorerMessage(MessageContent.of("hola"));

        Message reply = conversation.addLiaReply(MessageContent.of("¡Hola!"), null, question.getId());
        Message withoutCorrelation = conversation.addLiaReply(MessageContent.of("otra"), null);

        assertThat(reply.getInReplyToMessageId()).contains(question.getId());
        assertThat(withoutCorrelation.getInReplyToMessageId()).isEmpty();
        assertThat(question.getInReplyToMessageId()).isEmpty();

        conversation.linkReservation(reply.getId(), ReservationId.of("33333333-3333-3333-3333-333333333333"));
        assertThat(conversation.getMessages().get(1).getInReplyToMessageId()).contains(question.getId());
    }

    @Test
    void unaConversacionCerradaRechazaMensajesDelExploradorYRespuestasDeLia() {
        conversation.close();

        assertThat(conversation.isActive()).isFalse();
        assertThatThrownBy(() -> conversation.addExplorerMessage(MessageContent.of("hola")))
                .isInstanceOf(ConversationClosedException.class);
        assertThatThrownBy(() -> conversation.addLiaReply(MessageContent.of("hola"), null))
                .isInstanceOf(ConversationClosedException.class);
        assertThat(conversation.getMessages()).isEmpty();
    }

    @Test
    void closeEsIdempotente() {
        conversation.close();
        conversation.close();

        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.CLOSED);
    }

    @Test
    void elHistorialQuedaEnOrdenCronologico() {
        Message first = conversation.addExplorerMessage(MessageContent.of("uno"));
        clock.advance(Duration.ofSeconds(1));
        Message second = conversation.addLiaReply(MessageContent.of("dos"), null);
        clock.advance(Duration.ofSeconds(1));
        Message third = conversation.addExplorerMessage(MessageContent.of("tres"));

        assertThat(conversation.getMessages()).containsExactly(first, second, third);
        assertThat(conversation.getMessages())
                .extracting(Message::getSentAt)
                .isSorted();
    }

    @Test
    void siElRelojRetrocedeElMensajeNuncaQuedaAntesDelAnterior() {
        Message first = conversation.addExplorerMessage(MessageContent.of("uno"));
        clock.advance(Duration.ofMinutes(-10));

        Message second = conversation.addExplorerMessage(MessageContent.of("dos"));

        assertThat(second.getSentAt()).isEqualTo(first.getSentAt());
        assertThat(conversation.getMessages()).containsExactly(first, second);
        assertThat(conversation.getLastMessageAt()).isEqualTo(first.getSentAt());
    }

    @Test
    void elHistorialExpuestoNoSePuedeModificar() {
        conversation.addExplorerMessage(MessageContent.of("uno"));

        assertThatThrownBy(() -> conversation.getMessages().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void linkReservationVinculaLaReservaAlMensaje() {
        Message message = conversation.addExplorerMessage(MessageContent.of("reserva para 4 mañana 8 pm"));
        ReservationId reservation = ReservationId.of("33333333-3333-3333-3333-333333333333");

        conversation.linkReservation(message.getId(), reservation);

        Message linked = conversation.getMessages().get(0);
        assertThat(linked.hasReservation()).isTrue();
        assertThat(linked.getReservationId()).contains(reservation);
        assertThat(linked.getId()).isEqualTo(message.getId());
        assertThat(linked.getContent()).isEqualTo(message.getContent());
        assertThat(linked.getSentAt()).isEqualTo(message.getSentAt());
        assertThat(conversation.getMessages()).hasSize(1);
    }

    @Test
    void linkReservationSobreUnMensajeInexistenteLanzaExcepcion() {
        conversation.addExplorerMessage(MessageContent.of("hola"));

        assertThatThrownBy(() -> conversation.linkReservation(
                        MessageId.newId(), ReservationId.of("33333333-3333-3333-3333-333333333333")))
                .isInstanceOf(MessageNotFoundException.class);
    }

    @Test
    void linkReservationEnUnaConversacionSinMensajesLanzaExcepcion() {
        assertThatThrownBy(() -> conversation.linkReservation(
                        MessageId.newId(), ReservationId.of("33333333-3333-3333-3333-333333333333")))
                .isInstanceOf(MessageNotFoundException.class);
    }

    @Test
    void linkReservationEsIdempotenteParaLaMismaReservaYRechazaOtraDistinta() {
        Message message = conversation.addExplorerMessage(MessageContent.of("reserva"));
        ReservationId first = ReservationId.of("33333333-3333-3333-3333-333333333333");
        ReservationId other = ReservationId.of("44444444-4444-4444-4444-444444444444");
        conversation.linkReservation(message.getId(), first);

        conversation.linkReservation(message.getId(), first);

        assertThat(conversation.getMessages().get(0).getReservationId()).contains(first);
        assertThatThrownBy(() -> conversation.linkReservation(message.getId(), other))
                .isInstanceOf(ReservationAlreadyLinkedException.class);
    }

    @Test
    void restoreReconstruyeLaConversacionOrdenandoLosMensajes() {
        Message early = new Message(MessageId.newId(), SenderType.EXPLORER, MessageContent.of("a"),
                null, null, null, LocalDateTime.of(2026, 11, 13, 10, 0));
        Message late = new Message(MessageId.newId(), SenderType.LIA, MessageContent.of("b"),
                null, null, null, LocalDateTime.of(2026, 11, 13, 10, 5));

        Conversation restored = Conversation.restore(
                conversation.getId(), EXPLORER, ConversationStatus.CLOSED,
                LocalDateTime.of(2026, 11, 13, 9, 59), late.getSentAt(), List.of(late, early));

        assertThat(restored.getId()).isEqualTo(conversation.getId());
        assertThat(restored.isActive()).isFalse();
        assertThat(restored.getMessages()).containsExactly(early, late);
        assertThat(restored.getLastMessageAt()).isEqualTo(late.getSentAt());
    }

    @Test
    void unaConversacionRestauradaYActivaSigueAceptandoMensajesSinRetroceder() {
        LocalDateTime future = LocalDateTime.now(clock).plusHours(1);
        Conversation restored = Conversation.restore(
                conversation.getId(), EXPLORER, ConversationStatus.ACTIVE,
                LocalDateTime.now(clock), future, List.of(), clock);

        Message message = restored.addExplorerMessage(MessageContent.of("hola"));

        assertThat(message.getSentAt()).isEqualTo(future);
    }

    /** Reloj de prueba que solo avanza (o retrocede) cuando se le pide. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
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
