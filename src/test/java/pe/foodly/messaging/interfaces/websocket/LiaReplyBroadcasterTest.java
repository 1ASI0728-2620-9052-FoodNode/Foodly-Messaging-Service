package pe.foodly.messaging.interfaces.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import pe.foodly.messaging.application.events.LiaReplyRegisteredEvent;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.interfaces.rest.resources.MessageResource;

@ExtendWith(MockitoExtension.class)
class LiaReplyBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messaging;

    private final Conversation conversation = Conversation.start(ExplorerId.of("11111111-1111-1111-1111-111111111111"));

    @Test
    void publicaElMessageResourceEnElTopicDeLaConversacion() {
        Message reply = conversation.addLiaReply(MessageContent.of("¡Hola!"), null);

        new LiaReplyBroadcaster(messaging).on(new LiaReplyRegisteredEvent(conversation.getId(), reply));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq("/topic/conversations/" + conversation.getId()), payload.capture());
        assertThat(payload.getValue()).isInstanceOfSatisfying(MessageResource.class, resource -> {
            assertThat(resource.id()).isEqualTo(reply.getId().value());
            assertThat(resource.sender()).isEqualTo("LIA");
            assertThat(resource.content()).isEqualTo("¡Hola!");
        });
    }

    @Test
    void siLaPublicacionFallaNoSePropagaLaExcepcion() {
        Message reply = conversation.addLiaReply(MessageContent.of("¡Hola!"), null);
        doThrow(new MessagingException("broker caído")).when(messaging).convertAndSend(any(String.class), any(Object.class));

        assertThatCode(() -> new LiaReplyBroadcaster(messaging)
                        .on(new LiaReplyRegisteredEvent(conversation.getId(), reply)))
                .doesNotThrowAnyException();
    }
}
