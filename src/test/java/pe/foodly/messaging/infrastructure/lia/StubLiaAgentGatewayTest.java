package pe.foodly.messaging.infrastructure.lia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.foodly.messaging.application.commandhandlers.RegisterLiaReplyCommandHandler;
import pe.foodly.messaging.application.commands.RegisterLiaReplyCommand;
import pe.foodly.messaging.domain.model.enums.IntentType;
import pe.foodly.messaging.domain.model.valueobjects.ConversationContext;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;

@ExtendWith(MockitoExtension.class)
class StubLiaAgentGatewayTest {

    @Mock
    private RegisterLiaReplyCommandHandler handler;

    private StubLiaAgentGateway gateway;
    private final ConversationId conversationId = ConversationId.newId();

    @BeforeEach
    void setUp() {
        gateway = new StubLiaAgentGateway(handler, Duration.ofMillis(300), Clock.systemUTC());
    }

    @AfterEach
    void tearDown() {
        gateway.close();
    }

    private ConversationContext context() {
        return new ConversationContext(conversationId, new GeoCoordinates(-12.12, -77.03), List.of());
    }

    @Test
    void respondeDespuesDeLaEsperaConElMismoCommandQueElCallbackReal() {
        MessageId messageId = MessageId.newId();

        gateway.requestInterpretation(messageId, MessageContent.of("quiero algo cerca"), context());

        verifyNoInteractions(handler); // todavía no pasó el tiempo de espera
        ArgumentCaptor<RegisterLiaReplyCommand> captor = ArgumentCaptor.forClass(RegisterLiaReplyCommand.class);
        verify(handler, timeout(3000)).handle(captor.capture());
        RegisterLiaReplyCommand command = captor.getValue();
        assertThat(command.conversationId()).isEqualTo(conversationId);
        assertThat(command.inReplyToMessageId()).isEqualTo(messageId);
        assertThat(command.replyContent()).startsWith("¡Claro!");
        assertThat(command.intent().type()).isEqualTo(IntentType.RECOMMENDATION_REQUEST);
        assertThat(command.intent().location()).isEqualTo(new GeoCoordinates(-12.12, -77.03));
    }

    @Test
    void siElHandlerFallaElStubSigueAtendiendo() {
        doThrow(new IllegalStateException("base caída")).when(handler).handle(any());

        gateway.requestInterpretation(MessageId.newId(), MessageContent.of("quiero algo cerca"), context());
        gateway.requestInterpretation(MessageId.newId(), MessageContent.of("el menú"), context());

        verify(handler, timeout(3000).times(2)).handle(any());
    }

    @Test
    void alCerrarNoQuedanRespuestasPendientes() throws Exception {
        gateway.requestInterpretation(MessageId.newId(), MessageContent.of("quiero algo cerca"), context());

        gateway.close();
        Thread.sleep(600);

        verify(handler, times(0)).handle(any());
    }
}
