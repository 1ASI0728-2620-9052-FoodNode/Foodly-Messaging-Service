package pe.foodly.messaging.application.eventhandlers;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.foodly.messaging.application.events.MessageAcceptedEvent;
import pe.foodly.messaging.domain.model.valueobjects.ConversationContext;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.ports.ILiaAgentGateway;

@ExtendWith(MockitoExtension.class)
class MessageAcceptedEventHandlerTest {

    @Mock
    private ILiaAgentGateway gateway;

    private final MessageId messageId = MessageId.newId();
    private final MessageContent content = MessageContent.of("hola");
    private final ConversationContext context = new ConversationContext(ConversationId.newId(), null, List.of());

    @Test
    void pideLaInterpretacionALiaConLosDatosDelEvento() {
        new MessageAcceptedEventHandler(gateway).on(new MessageAcceptedEvent(messageId, content, context));

        verify(gateway).requestInterpretation(messageId, content, context);
    }

    @Test
    void siElGatewayFallaNoSePropagaLaExcepcion() {
        doThrow(new IllegalStateException("integration system caído"))
                .when(gateway).requestInterpretation(messageId, content, context);

        assertThatCode(() -> new MessageAcceptedEventHandler(gateway)
                        .on(new MessageAcceptedEvent(messageId, content, context)))
                .doesNotThrowAnyException();
    }
}
