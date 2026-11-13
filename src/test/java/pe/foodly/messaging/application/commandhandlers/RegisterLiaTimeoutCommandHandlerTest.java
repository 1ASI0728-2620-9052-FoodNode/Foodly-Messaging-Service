package pe.foodly.messaging.application.commandhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;
import pe.foodly.messaging.application.MessagingSettings;
import pe.foodly.messaging.application.commands.RegisterLiaTimeoutCommand;
import pe.foodly.messaging.application.events.LiaReplyRegisteredEvent;
import pe.foodly.messaging.application.outboundports.IExplorerTokenCache;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.repositories.IConversationRepository;

@ExtendWith(MockitoExtension.class)
class RegisterLiaTimeoutCommandHandlerTest {

    @Mock
    private IConversationRepository conversations;
    @Mock
    private IExplorerTokenCache tokenCache;
    @Mock
    private ApplicationEventPublisher events;

    private final ReplyInFlightRegistry inFlight = new ReplyInFlightRegistry();
    private Conversation conversation;
    private Message question;
    private RegisterLiaTimeoutCommandHandler handler;

    @BeforeEach
    void setUp() {
        conversation = Conversation.start(ExplorerId.of("11111111-1111-1111-1111-111111111111"));
        question = conversation.addExplorerMessage(MessageContent.of("quiero una mesa para hoy"));
        lenient().when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));
        handler = new RegisterLiaTimeoutCommandHandler(
                conversations, tokenCache, TransactionOperations.withoutTransaction(), events, inFlight,
                new MessagingSettings(1000, 2));
    }

    private RegisterLiaTimeoutCommand command() {
        return new RegisterLiaTimeoutCommand(conversation.getId(), question.getId());
    }

    @Test
    void registraUnaRespuestaDeLiaQueOfreceLaReservaManualConLaCorrelacion() {
        Message notice = handler.handle(command()).orElseThrow();

        assertThat(notice.isFromLia()).isTrue();
        assertThat(notice.getInReplyToMessageId()).contains(question.getId());
        assertThat(notice.getContent().text()).contains("reserva de forma manual");
        assertThat(notice.getIntent()).isEmpty();
        assertThat(conversation.getMessages()).containsExactly(question, notice);
        verify(conversations).save(conversation);
    }

    @Test
    void publicaElEventoParaQueElClienteLoVeaPorStompYRetiraElToken() {
        Message notice = handler.handle(command()).orElseThrow();

        verify(events).publishEvent(new LiaReplyRegisteredEvent(conversation.getId(), notice));
        verify(tokenCache).evict(question.getId());
    }

    @Test
    void esIdempotenteSiElMensajeYaTieneRespuesta() {
        conversation.addLiaReply(MessageContent.of("Ya te respondí"), null, question.getId());

        assertThat(handler.handle(command())).isEmpty();

        assertThat(conversation.getMessages()).hasSize(2);
        verify(conversations, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void ejecutarloDosVecesSoloRegistraUnAviso() {
        assertThat(handler.handle(command())).isPresent();
        assertThat(handler.handle(command())).isEmpty();

        assertThat(conversation.getMessages()).hasSize(2);
    }

    @Test
    void noHaceNadaSiElCallbackEstaProcesandoElMensaje() {
        inFlight.tryAcquire(question.getId());

        assertThat(handler.handle(command())).isEmpty();

        verify(conversations, never()).save(any());
        verifyNoInteractions(events, tokenCache);
    }

    @Test
    void liberaElMensajeAlTerminar() {
        handler.handle(command());

        assertThat(inFlight.tryAcquire(question.getId())).isTrue();
    }

    @Test
    void noHaceNadaSiLaConversacionEstaCerradaOYaNoExiste() {
        conversation.close();
        assertThat(handler.handle(command())).isEmpty();

        ConversationId unknown = ConversationId.newId();
        when(conversations.findById(unknown)).thenReturn(Optional.empty());
        assertThat(handler.handle(new RegisterLiaTimeoutCommand(unknown, question.getId()))).isEmpty();

        verify(conversations, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void ignoraUnMensajeQueNoEsDelExploradorDeEsaConversacion() {
        Message lia = conversation.addLiaReply(MessageContent.of("hola"), null);

        assertThat(handler.handle(new RegisterLiaTimeoutCommand(conversation.getId(), lia.getId()))).isEmpty();
        assertThat(handler.handle(new RegisterLiaTimeoutCommand(conversation.getId(), MessageId.newId()))).isEmpty();

        verify(conversations, never()).save(any());
    }
}
