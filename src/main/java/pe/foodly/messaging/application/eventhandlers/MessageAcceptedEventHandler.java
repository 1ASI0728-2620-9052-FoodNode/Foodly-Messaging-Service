package pe.foodly.messaging.application.eventhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import pe.foodly.messaging.application.events.MessageAcceptedEvent;
import pe.foodly.messaging.domain.ports.ILiaAgentGateway;

/**
 * Pide a Lía interpretar el mensaje, solo después de que el mensaje quedó confirmado en la base y en un
 * executor propio ({@code liaTaskExecutor}): así {@code POST /messaging/chat} responde 202 sin esperar
 * al Integration System ni a n8n.
 *
 * <p>Si la solicitud falla no se propaga nada: el mensaje ya está guardado y {@code LiaTimeoutJob}
 * ofrecerá la reserva manual cuando venza el tiempo de espera.
 */
@Component
public class MessageAcceptedEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MessageAcceptedEventHandler.class);

    private final ILiaAgentGateway gateway;

    public MessageAcceptedEventHandler(ILiaAgentGateway gateway) {
        this.gateway = gateway;
    }

    @Async("liaTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MessageAcceptedEvent event) {
        try {
            gateway.requestInterpretation(event.messageId(), event.content(), event.context());
        } catch (RuntimeException e) {
            log.error("No se pudo solicitar a Lía la interpretación del mensaje {}.", event.messageId(), e);
        }
    }
}
