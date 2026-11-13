package pe.foodly.messaging.interfaces.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.foodly.messaging.application.events.LiaReplyRegisteredEvent;
import pe.foodly.messaging.interfaces.rest.transform.MessageResourceFromEntityAssembler;

/**
 * Publica cada respuesta de Lía en {@code /topic/conversations/{conversationId}} (DD-04) con el mismo
 * {@code MessageResource} que devuelve {@code GET .../messages}. Se ejecuta después del commit, así que
 * cuando el cliente recibe el aviso el mensaje ya se puede leer por REST, que sigue siendo la fuente de
 * verdad. Si la publicación falla solo se registra: el cliente se pone al día con {@code GET}.
 */
@Component
public class LiaReplyBroadcaster {

    public static final String TOPIC_PREFIX = "/topic/conversations/";

    private static final Logger log = LoggerFactory.getLogger(LiaReplyBroadcaster.class);

    private final SimpMessagingTemplate messaging;

    public LiaReplyBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void on(LiaReplyRegisteredEvent event) {
        try {
            messaging.convertAndSend(
                    TOPIC_PREFIX + event.conversationId(), MessageResourceFromEntityAssembler.toResource(event.message()));
        } catch (RuntimeException e) {
            log.warn("No se pudo publicar por STOMP la respuesta {} de la conversación {}.",
                    event.message().getId(), event.conversationId(), e);
        }
    }
}
