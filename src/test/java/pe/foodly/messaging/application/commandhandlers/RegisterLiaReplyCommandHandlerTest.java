package pe.foodly.messaging.application.commandhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.MessagingSettings;
import pe.foodly.messaging.application.commands.RegisterLiaReplyCommand;
import pe.foodly.messaging.application.events.LiaReplyRegisteredEvent;
import pe.foodly.messaging.application.commands.RequestedReservation;
import pe.foodly.messaging.application.exceptions.ConversationNotFoundException;
import pe.foodly.messaging.application.exceptions.ProviderUnavailableException;
import pe.foodly.messaging.application.exceptions.ReservationRejectedException;
import pe.foodly.messaging.application.outboundports.HuariqueDetails;
import pe.foodly.messaging.application.outboundports.IBusinessClient;
import pe.foodly.messaging.application.outboundports.IExplorerTokenCache;
import pe.foodly.messaging.application.outboundports.IRadarClient;
import pe.foodly.messaging.application.outboundports.IReservationClient;
import pe.foodly.messaging.application.outboundports.MenuItem;
import pe.foodly.messaging.application.outboundports.NearbyHuarique;
import pe.foodly.messaging.application.outboundports.ReservationConfirmation;
import pe.foodly.messaging.domain.exceptions.ConversationClosedException;
import pe.foodly.messaging.domain.exceptions.MessageNotFoundException;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.enums.IntentType;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;
import pe.foodly.messaging.domain.repositories.IConversationRepository;
import pe.foodly.messaging.domain.services.IntentRoutingService;

@ExtendWith(MockitoExtension.class)
class RegisterLiaReplyCommandHandlerTest {

    private static final ExplorerId EXPLORER = ExplorerId.of("11111111-1111-1111-1111-111111111111");
    private static final ExplorerToken TOKEN = new ExplorerToken("jwt-secreto", Instant.parse("2030-01-01T00:00:00Z"));
    private static final GeoCoordinates LOCATION = new GeoCoordinates(-12.12, -77.03);
    private static final HuariqueId H1 = HuariqueId.of("a1111111-1111-4111-8111-111111111111");
    private static final HuariqueId H2 = HuariqueId.of("a2222222-2222-4222-8222-222222222222");
    private static final HuariqueId H3 = HuariqueId.of("a3333333-3333-4333-8333-333333333333");
    private static final HuariqueId H4 = HuariqueId.of("a4444444-4444-4444-8444-444444444444");
    private static final ReservationId RESERVATION = ReservationId.of("33333333-3333-3333-3333-333333333333");
    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 11, 14, 20, 0);

    @Mock
    private IConversationRepository conversations;
    @Mock
    private IRadarClient radar;
    @Mock
    private IBusinessClient business;
    @Mock
    private IReservationClient reservations;
    @Mock
    private IExplorerTokenCache tokenCache;
    @Mock
    private ApplicationEventPublisher events;

    private final ReplyInFlightRegistry inFlight = new ReplyInFlightRegistry();
    private Conversation conversation;
    private Message question;
    private RegisterLiaReplyCommandHandler handler;

    @BeforeEach
    void setUp() {
        conversation = Conversation.start(EXPLORER);
        question = conversation.addExplorerMessage(MessageContent.of("quiero un ceviche barato cerca"));
        lenient().when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        lenient().when(tokenCache.find(question.getId())).thenReturn(Optional.of(TOKEN));
        handler = handlerWith(new MessagingSettings(1000, 2));
    }

    private RegisterLiaReplyCommandHandler handlerWith(MessagingSettings settings) {
        return new RegisterLiaReplyCommandHandler(
                conversations, new IntentRoutingService(), radar, business, reservations, tokenCache,
                TransactionOperations.withoutTransaction(), settings, events, inFlight);
    }

    private RegisterLiaReplyCommand command(UserIntent intent) {
        return new RegisterLiaReplyCommand(conversation.getId(), question.getId(), "¡Claro!", intent, null, null);
    }

    private static UserIntent recommendation() {
        return new UserIntent(IntentType.RECOMMENDATION_REQUEST, "ceviche", null, LOCATION, null, null, null);
    }

    private static UserIntent inquiry(HuariqueId huarique) {
        return new UserIntent(IntentType.HUARIQUE_INQUIRY, null, null, null, null, null, huarique);
    }

    private static UserIntent reservation() {
        return new UserIntent(IntentType.RESERVATION_REQUEST, null, null, null, 4, WHEN, H1);
    }

    private static HuariqueDetails details(HuariqueId id, String name) {
        return new HuariqueDetails(id, name, null, null, null, null);
    }

    private Message lastMessage() {
        List<Message> messages = conversation.getMessages();
        return messages.get(messages.size() - 1);
    }

    // ---- una prueba por cada RoutingDecision ----

    @Test
    void recommendViaRadarBuscaConElKRingConfiguradoYPideElNombreSoloDeLosTresPrimeros() {
        when(radar.search(TOKEN, LOCATION, 2)).thenReturn(List.of(
                new NearbyHuarique(H1, 350), new NearbyHuarique(H2, 820),
                new NearbyHuarique(H3, 1500), new NearbyHuarique(H4, 2400)));
        when(business.findHuarique(TOKEN, H1)).thenReturn(Optional.of(details(H1, "Cevichería A")));
        when(business.findHuarique(TOKEN, H2)).thenReturn(Optional.of(details(H2, "Anticuchos B")));
        when(business.findHuarique(TOKEN, H3)).thenReturn(Optional.of(details(H3, "Picantería C")));

        Optional<Message> reply = handler.handle(command(recommendation()));

        assertThat(reply).isPresent();
        assertThat(reply.get().isFromLia()).isTrue();
        assertThat(reply.get().getContent().text())
                .startsWith("¡Claro!")
                .contains("1. Cevichería A (a 350 m)", "2. Anticuchos B (a 820 m)", "3. Picantería C (a 1.5 km)")
                .doesNotContain("4.");
        verify(business, never()).findHuarique(TOKEN, H4);
        verifyNoInteractions(reservations);
        assertThat(lastMessage()).isSameAs(reply.get());
        assertThat(reply.get().getInReplyToMessageId()).contains(question.getId());
        assertThat(reply.get().getHuariqueId()).isEmpty();
        verify(conversations).save(conversation);
    }

    @Test
    void recommendViaRadarSinResultadosLoDiceConAmabilidad() {
        when(radar.search(TOKEN, LOCATION, 2)).thenReturn(List.of());

        Message reply = handler.handle(command(recommendation())).orElseThrow();

        assertThat(reply.getContent().text()).contains("no encontré huariques cerca");
        verifyNoInteractions(business);
    }

    @Test
    void queryBusinessInformaDetalleYMenuYGuardaElHuarique() {
        when(business.findHuarique(TOKEN, H1)).thenReturn(Optional.of(new HuariqueDetails(
                H1, "Cevichería A", null, "Av. Larco 345", "12:00 - 17:00", true)));
        when(business.findMenu(TOKEN, H1)).thenReturn(List.of(
                new MenuItem("Ceviche clásico", null, new BigDecimal("32")),
                new MenuItem("Chicha morada", null, null)));

        Message reply = handler.handle(command(inquiry(H1))).orElseThrow();

        assertThat(reply.getContent().text())
                .contains("Cevichería A", "Av. Larco 345", "12:00 - 17:00", "Ahora está abierto",
                        "- Ceviche clásico: S/ 32.00", "- Chicha morada");
        assertThat(reply.getHuariqueId()).contains(H1);
        verifyNoInteractions(radar, reservations);
    }

    @Test
    void queryBusinessDeUnHuariqueInexistenteLoAvisa() {
        when(business.findHuarique(TOKEN, H1)).thenReturn(Optional.empty());

        Message reply = handler.handle(command(inquiry(H1))).orElseThrow();

        assertThat(reply.getContent().text()).contains("No encontré información de ese huarique");
        verify(business, never()).findMenu(any(), any());
    }

    @Test
    void forwardToReservationReenviaLaSolicitudYVinculaElReservationId() {
        when(reservations.request(TOKEN, H1, LocalDate.of(2026, 11, 14), LocalTime.of(20, 0), 4))
                .thenReturn(new ReservationConfirmation(RESERVATION, "PENDING"));

        Message reply = handler.handle(command(reservation())).orElseThrow();

        verify(reservations).request(TOKEN, H1, LocalDate.of(2026, 11, 14), LocalTime.of(20, 0), 4);
        // La reserva queda vinculada al mensaje del explorador que la originó.
        Message linked = conversation.getMessages().get(0);
        assertThat(linked.getId()).isEqualTo(question.getId());
        assertThat(linked.getReservationId()).contains(RESERVATION);
        assertThat(reply.getContent().text())
                .contains("4 personas", "14/11/2026", "20:00", "pendiente de aprobación del dueño");
        assertThat(reply.getHuariqueId()).contains(H1);
        assertThat(reply.getReservationId()).isEmpty();
        verify(conversations).save(conversation);
        verifyNoInteractions(radar, business);
    }

    @Test
    void forwardToReservationCompletaLosDatosConLaReservaSolicitadaDelCallback() {
        UserIntent withoutDate = new UserIntent(IntentType.RESERVATION_REQUEST, null, null, null, 2, null, H1);
        when(reservations.request(TOKEN, H1, LocalDate.of(2026, 11, 14), LocalTime.of(20, 0), 2))
                .thenReturn(new ReservationConfirmation(RESERVATION, "PENDING"));
        RegisterLiaReplyCommand command = new RegisterLiaReplyCommand(
                conversation.getId(), question.getId(), "Listo", withoutDate, null,
                new RequestedReservation(null, WHEN, null));

        handler.handle(command);

        verify(reservations).request(TOKEN, H1, LocalDate.of(2026, 11, 14), LocalTime.of(20, 0), 2);
    }

    @Test
    void askClarificationArmaLaPreguntaConMissingContextYNoLlamaANadie() {
        UserIntent withoutDate = new UserIntent(IntentType.RESERVATION_REQUEST, null, null, null, 4, null, H1);

        Message reply = handler.handle(command(withoutDate)).orElseThrow();

        assertThat(withoutDate.missingContext()).containsExactly("requestedAt");
        assertThat(reply.getContent().text()).startsWith("¡Claro!").contains("para qué fecha y hora");
        verifyNoInteractions(radar, business, reservations);
        verify(tokenCache, never()).find(any());
    }

    @Test
    void askClarificationPideTodoLoQueFalta() {
        UserIntent empty = new UserIntent(IntentType.RESERVATION_REQUEST, null, null, null, null, null, null);

        Message reply = handler.handle(command(empty)).orElseThrow();

        assertThat(reply.getContent().text())
                .contains("a qué huarique te refieres, para qué fecha y hora y para cuántas personas");
    }

    @Test
    void unaIntencionNulaOUnknownPideAclaracionSobreQueQuiere() {
        Message reply = handler.handle(command(null)).orElseThrow();

        assertThat(reply.getContent().text()).contains("una recomendación, información de un huarique o hacer una reserva");
        assertThat(reply.getIntent()).isPresent();
        assertThat(reply.getIntent().get().type()).isEqualTo(IntentType.UNKNOWN);
        verifyNoInteractions(radar, business, reservations);
    }

    // ---- idempotencia ----

    @Test
    void siYaHayUnaRespuestaParaElMensajeSeIgnoraSinLlamarANadie() {
        conversation.addLiaReply(MessageContent.of("Ya te respondí"), null, question.getId());

        Optional<Message> reply = handler.handle(command(recommendation()));

        assertThat(reply).isEmpty();
        assertThat(conversation.getMessages()).hasSize(2);
        verifyNoInteractions(radar, business, reservations);
        verify(conversations, never()).save(any());
        verify(tokenCache, never()).evict(any());
    }

    @Test
    void siElMensajeYaSeEstaRespondiendoElCallbackSeIgnora() {
        inFlight.tryAcquire(question.getId());

        Optional<Message> reply = handler.handle(command(recommendation()));

        assertThat(reply).isEmpty();
        verifyNoInteractions(radar, business, reservations);
        verify(conversations, never()).save(any());
    }

    @Test
    void despuesDeResponderElMensajeQuedaLibreEnElRegistro() {
        handler.handle(command(null));

        assertThat(inFlight.tryAcquire(question.getId())).isTrue();
    }

    @Test
    void elAvisoDeTimeoutCuentaComoRespuestaYUnCallbackTardioNoLoReemplaza() {
        Message timeout = conversation.addLiaReply(
                MessageContent.of("No pude responder a tiempo. Puedes hacer una reserva manual."), null, question.getId());

        Optional<Message> late = handler.handle(command(reservation()));

        assertThat(late).isEmpty();
        assertThat(conversation.getMessages()).containsExactly(question, timeout);
        verifyNoInteractions(reservations);
    }

    @Test
    void entregarDosVecesElMismoCallbackSoloRegistraUnaRespuestaYReservaUnaVez() {
        when(reservations.request(any(), any(), any(), any(), anyInt()))
                .thenReturn(new ReservationConfirmation(RESERVATION, "PENDING"));

        Optional<Message> first = handler.handle(command(reservation()));
        Optional<Message> second = handler.handle(command(reservation()));

        assertThat(first).isPresent();
        assertThat(second).isEmpty();
        verify(reservations, times(1)).request(any(), any(), any(), any(), anyInt());
        assertThat(conversation.getMessages()).hasSize(2);
    }

    // ---- fallos ----

    @Test
    void siRadarFallaLiaRespondeConUnMensajeAmableSinOfrecerReserva() {
        when(radar.search(TOKEN, LOCATION, 2)).thenThrow(new ProviderUnavailableException("Radar", "no respondió a tiempo"));

        Message reply = handler.handle(command(recommendation())).orElseThrow();

        assertThat(reply.getContent().text())
                .contains("no pude completar tu solicitud")
                .doesNotContain("manual")
                .doesNotContain("¡Claro!"); // lo que prometió n8n ya no es cierto
        assertThat(reply.getInReplyToMessageId()).contains(question.getId());
        verify(conversations).save(conversation);
    }

    @Test
    void siBusinessFallaAlPedirLosNombresTambienRespondeConAmabilidad() {
        when(radar.search(TOKEN, LOCATION, 2)).thenReturn(List.of(new NearbyHuarique(H1, 100)));
        when(business.findHuarique(TOKEN, H1)).thenThrow(new ProviderUnavailableException("Business", "respondió 503"));

        Message reply = handler.handle(command(recommendation())).orElseThrow();

        assertThat(reply.getContent().text()).contains("no pude completar tu solicitud");
    }

    @Test
    void siReservationSeCaeOfreceLaReservaManualYNoVinculaNada() {
        when(reservations.request(any(), any(), any(), any(), anyInt()))
                .thenThrow(new ProviderUnavailableException("Reservation", "no respondió a tiempo"));

        Message reply = handler.handle(command(reservation())).orElseThrow();

        assertThat(reply.getContent().text()).contains("no pude completar tu solicitud", "reserva de forma manual");
        assertThat(conversation.getMessages().get(0).hasReservation()).isFalse();
    }

    @Test
    void siReservationRechazaLaSolicitudLiaExplicaElMotivoYOfreceLaReservaManual() {
        when(reservations.request(any(), any(), any(), any(), anyInt()))
                .thenThrow(new ReservationRejectedException("No hay mesas disponibles a esa hora."));

        Message reply = handler.handle(command(reservation())).orElseThrow();

        assertThat(reply.getContent().text())
                .contains("No hay mesas disponibles a esa hora", "otra fecha u hora", "reserva de forma manual");
        assertThat(conversation.getMessages().get(0).hasReservation()).isFalse();
    }

    @Test
    void conUnTokenVencidoNoSeLlamaAProveedoresYLiaOfreceLaReservaManualSiEraUnaReserva() {
        when(tokenCache.find(question.getId())).thenReturn(Optional.empty());

        Message reply = handler.handle(command(reservation())).orElseThrow();

        assertThat(reply.getContent().text()).contains("no pude completar tu solicitud", "reserva de forma manual");
        verifyNoInteractions(radar, business, reservations);
        verify(tokenCache).evict(question.getId());
    }

    @Test
    void conUnTokenVencidoUnaRecomendacionSoloPideReintentar() {
        when(tokenCache.find(question.getId())).thenReturn(Optional.empty());

        Message reply = handler.handle(command(recommendation())).orElseThrow();

        assertThat(reply.getContent().text()).contains("no pude completar tu solicitud").doesNotContain("manual");
        verifyNoInteractions(radar, business, reservations);
    }

    // ---- validaciones y otros ----

    @Test
    void unaConversacionCerradaNoLlamaAProveedores() {
        conversation.close();

        assertThatThrownBy(() -> handler.handle(command(reservation())))
                .isInstanceOf(ConversationClosedException.class);

        verifyNoInteractions(radar, business, reservations);
        verify(conversations, never()).save(any());
    }

    @Test
    void unMensajeQueNoEsDeEsaConversacionSeRechaza() {
        RegisterLiaReplyCommand unknown = new RegisterLiaReplyCommand(
                conversation.getId(), MessageId.newId(), "hola", recommendation(), null, null);

        assertThatThrownBy(() -> handler.handle(unknown)).isInstanceOf(MessageNotFoundException.class);

        verifyNoInteractions(radar, business, reservations);
    }

    @Test
    void laRespuestaDeLiaNoPuedeResponderALaPropiaLia() {
        Message lia = conversation.addLiaReply(MessageContent.of("hola"), null);
        RegisterLiaReplyCommand toLia = new RegisterLiaReplyCommand(
                conversation.getId(), lia.getId(), "x", recommendation(), null, null);

        assertThatThrownBy(() -> handler.handle(toLia)).isInstanceOf(MessageNotFoundException.class);
    }

    @Test
    void unaConversacionInexistenteLanzaNotFound() {
        ConversationId unknown = ConversationId.newId();
        when(conversations.findById(unknown)).thenReturn(Optional.empty());
        RegisterLiaReplyCommand command = new RegisterLiaReplyCommand(
                unknown, question.getId(), "hola", recommendation(), null, null);

        assertThatThrownBy(() -> handler.handle(command)).isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void publicaElEventoQueAvisaPorStompDeLaRespuestaRegistrada() {
        Message reply = handler.handle(command(null)).orElseThrow();

        verify(events).publishEvent(new LiaReplyRegisteredEvent(conversation.getId(), reply));
    }

    @Test
    void siElCallbackSeIgnoraNoSePublicaNingunEvento() {
        conversation.addLiaReply(MessageContent.of("Ya te respondí"), null, question.getId());

        handler.handle(command(recommendation()));

        verifyNoInteractions(events);
    }

    @Test
    void retiraElTokenDelCacheCuandoRegistraLaRespuesta() {
        handler.handle(command(null));

        verify(tokenCache).evict(question.getId());
    }

    @Test
    void laRespuestaNuncaSuperaLaLongitudMaximaDelChat() {
        handler = handlerWith(new MessagingSettings(80, 2));
        RegisterLiaReplyCommand command = new RegisterLiaReplyCommand(
                conversation.getId(), question.getId(), "Un texto muy largo. ".repeat(20), null, null, null);

        Message reply = handler.handle(command).orElseThrow();

        assertThat(reply.getContent().length()).isLessThanOrEqualTo(80);
        assertThat(reply.getContent().text()).endsWith("…");
    }
}
