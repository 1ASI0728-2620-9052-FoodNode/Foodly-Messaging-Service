package pe.foodly.messaging.application.commandhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.commands.SendMessageCommand;
import pe.foodly.messaging.application.commands.SendMessageResult;
import pe.foodly.messaging.application.events.MessageAcceptedEvent;
import pe.foodly.messaging.application.exceptions.ConversationNotFoundException;
import pe.foodly.messaging.application.outboundports.IExplorerTokenCache;
import pe.foodly.messaging.domain.exceptions.ConversationClosedException;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.repositories.IConversationRepository;

@ExtendWith(MockitoExtension.class)
class SendMessageCommandHandlerTest {

    private static final ExplorerId EXPLORER = ExplorerId.of("11111111-1111-1111-1111-111111111111");
    private static final ExplorerId OTHER = ExplorerId.of("99999999-9999-9999-9999-999999999999");
    private static final ExplorerToken TOKEN = new ExplorerToken("jwt-secreto", Instant.parse("2030-01-01T00:00:00Z"));
    private static final GeoCoordinates LOCATION = new GeoCoordinates(-12.12, -77.03);

    @Mock
    private IConversationRepository conversations;
    @Mock
    private IExplorerTokenCache tokenCache;
    @Mock
    private ApplicationEventPublisher events;

    private SendMessageCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SendMessageCommandHandler(conversations, tokenCache, events);
    }

    private SendMessageCommand command(ConversationId conversationId, String text) {
        return new SendMessageCommand(EXPLORER, conversationId, MessageContent.of(text), LOCATION, TOKEN);
    }

    @Test
    void creaLaConversacionSiElExploradorNoTieneUnaActiva() {
        when(conversations.findActiveByExplorerId(EXPLORER)).thenReturn(Optional.empty());

        SendMessageResult result = handler.handle(command(null, "quiero un ceviche barato cerca"));

        ArgumentCaptor<Conversation> saved = ArgumentCaptor.forClass(Conversation.class);
        verify(conversations).save(saved.capture());
        Conversation conversation = saved.getValue();
        assertThat(conversation.getId()).isEqualTo(result.conversationId());
        assertThat(conversation.getExplorerId()).isEqualTo(EXPLORER);
        assertThat(conversation.isActive()).isTrue();
        assertThat(conversation.getMessages()).hasSize(1);
        Message message = conversation.getMessages().get(0);
        assertThat(message.getId()).isEqualTo(result.messageId());
        assertThat(message.getContent().text()).isEqualTo("quiero un ceviche barato cerca");
        assertThat(message.isFromLia()).isFalse();
    }

    @Test
    void reutilizaLaConversacionActivaDelExplorador() {
        Conversation active = Conversation.start(EXPLORER);
        active.addExplorerMessage(MessageContent.of("hola"));
        when(conversations.findActiveByExplorerId(EXPLORER)).thenReturn(Optional.of(active));

        SendMessageResult result = handler.handle(command(null, "otra cosa"));

        assertThat(result.conversationId()).isEqualTo(active.getId());
        assertThat(active.getMessages()).hasSize(2);
        verify(conversations).save(active);
    }

    @Test
    void usaLaConversacionIndicada() {
        Conversation conversation = Conversation.start(EXPLORER);
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        SendMessageResult result = handler.handle(command(conversation.getId(), "hola"));

        assertThat(result.conversationId()).isEqualTo(conversation.getId());
        verify(conversations, never()).findActiveByExplorerId(any());
    }

    @Test
    void unaConversacionInexistenteDaNotFoundYNoHaceNada() {
        ConversationId unknown = ConversationId.newId();
        when(conversations.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(command(unknown, "hola")))
                .isInstanceOf(ConversationNotFoundException.class);

        verify(conversations, never()).save(any());
        verifyNoInteractions(tokenCache, events);
    }

    @Test
    void unaConversacionDeOtroExploradorDaElMismoNotFound() {
        Conversation foreign = Conversation.start(OTHER);
        when(conversations.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> handler.handle(command(foreign.getId(), "hola")))
                .isInstanceOf(ConversationNotFoundException.class);

        assertThat(foreign.getMessages()).isEmpty();
        verify(conversations, never()).save(any());
        verifyNoInteractions(tokenCache, events);
    }

    @Test
    void unaConversacionCerradaRechazaElMensajeSinGuardarNiPublicarNada() {
        Conversation closed = Conversation.start(EXPLORER);
        closed.close();
        when(conversations.findById(closed.getId())).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> handler.handle(command(closed.getId(), "hola")))
                .isInstanceOf(ConversationClosedException.class);

        verify(conversations, never()).save(any());
        verifyNoInteractions(tokenCache, events);
    }

    @Test
    void guardaElTokenEnElCacheConElIdDelMensajeComoClave() {
        when(conversations.findActiveByExplorerId(EXPLORER)).thenReturn(Optional.empty());

        SendMessageResult result = handler.handle(command(null, "hola"));

        verify(tokenCache).put(result.messageId(), TOKEN);
    }

    @Test
    void publicaElEventoQueDisparaLaSolicitudAfterCommitConUbicacionEHistorial() {
        Conversation active = Conversation.start(EXPLORER);
        Message first = active.addExplorerMessage(MessageContent.of("primero"));
        Message reply = active.addLiaReply(MessageContent.of("respuesta"), null);
        when(conversations.findActiveByExplorerId(EXPLORER)).thenReturn(Optional.of(active));

        SendMessageResult result = handler.handle(command(null, "segundo"));

        ArgumentCaptor<MessageAcceptedEvent> published = ArgumentCaptor.forClass(MessageAcceptedEvent.class);
        verify(events).publishEvent(published.capture());
        MessageAcceptedEvent event = published.getValue();
        assertThat(event.messageId()).isEqualTo(result.messageId());
        assertThat(event.content().text()).isEqualTo("segundo");
        assertThat(event.context().conversationId()).isEqualTo(active.getId());
        assertThat(event.context().location()).isEqualTo(LOCATION);
        // El historial son los mensajes previos: no incluye el que se está enviando.
        assertThat(event.context().history()).containsExactly(first, reply);
    }

    @Test
    void elHistorialSeLimitaALosUltimosMensajes() {
        Conversation active = Conversation.start(EXPLORER);
        for (int i = 0; i < SendMessageCommandHandler.HISTORY_LIMIT + 5; i++) {
            active.addExplorerMessage(MessageContent.of("mensaje " + i));
        }
        when(conversations.findActiveByExplorerId(EXPLORER)).thenReturn(Optional.of(active));

        handler.handle(command(null, "nuevo"));

        ArgumentCaptor<MessageAcceptedEvent> published = ArgumentCaptor.forClass(MessageAcceptedEvent.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue().context().history()).hasSize(SendMessageCommandHandler.HISTORY_LIMIT);
        assertThat(published.getValue().context().history().get(0).getContent().text()).isEqualTo("mensaje 5");
    }

    @Test
    void elTokenNuncaApareceEnElTextoDeLosObjetos() {
        assertThat(TOKEN.toString()).doesNotContain("jwt-secreto");
        assertThat(command(null, "hola").toString()).doesNotContain("jwt-secreto");
    }
}
