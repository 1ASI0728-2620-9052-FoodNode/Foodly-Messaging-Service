package pe.foodly.messaging.application.commandhandlers;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;

/**
 * Mensajes del explorador cuya respuesta se está registrando en este momento. Lo comparten el callback de
 * Lía y el aviso de timeout para no responder dos veces al mismo mensaje cuando coinciden en el tiempo.
 * Es local al proceso: se asume una sola instancia (DD-10).
 */
@Component
public class ReplyInFlightRegistry {

    private final Set<MessageId> inFlight = ConcurrentHashMap.newKeySet();

    /** @return {@code false} si otro hilo ya está respondiendo a ese mensaje */
    public boolean tryAcquire(MessageId messageId) {
        return inFlight.add(messageId);
    }

    public void release(MessageId messageId) {
        inFlight.remove(messageId);
    }
}
