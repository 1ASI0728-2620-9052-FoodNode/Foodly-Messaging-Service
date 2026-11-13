package pe.foodly.messaging.infrastructure.scheduling;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pe.foodly.messaging.application.commandhandlers.RegisterLiaTimeoutCommandHandler;
import pe.foodly.messaging.application.commands.RegisterLiaTimeoutCommand;
import pe.foodly.messaging.infrastructure.lia.LiaProperties;
import pe.foodly.messaging.infrastructure.persistence.jpa.UnansweredMessageQuery;

/**
 * Detecta los mensajes del explorador a los que Lía no respondió dentro de {@code LIA_TIMEOUT_SECONDS} y
 * ejecuta {@link RegisterLiaTimeoutCommand} por cada uno (5.6.4): el error queda en el log y el explorador
 * recibe una respuesta de Lía que le ofrece la reserva manual (RF-15).
 *
 * <p>Corre cada 10 s y es idempotente: lo que ya tiene respuesta (de Lía o de un aviso anterior) no vuelve a
 * seleccionarse, y el handler lo verifica otra vez antes de insertar. Asume una sola instancia (DD-10); con
 * varias haría falta un lock distribuido (por ejemplo, ShedLock). Las fechas se comparan con la zona
 * horaria del servidor, la misma con la que el dominio guarda {@code sent_at}.
 */
@Component
public class LiaTimeoutJob {

    private static final Logger log = LoggerFactory.getLogger(LiaTimeoutJob.class);

    private final UnansweredMessageQuery unanswered;
    private final RegisterLiaTimeoutCommandHandler handler;
    private final LiaProperties properties;
    private final Clock clock;

    public LiaTimeoutJob(
            UnansweredMessageQuery unanswered,
            RegisterLiaTimeoutCommandHandler handler,
            LiaProperties properties,
            Clock clock) {
        this.unanswered = unanswered;
        this.handler = handler;
        this.properties = properties;
        this.clock = clock;
    }

    /** {@code fixedDelay}: la siguiente ejecución espera a que termine la anterior (nunca se solapan). */
    @Scheduled(
            initialDelayString = "${messaging.lia.timeout-job.interval-ms:10000}",
            fixedDelayString = "${messaging.lia.timeout-job.interval-ms:10000}")
    public void run() {
        try {
            checkTimeouts();
        } catch (RuntimeException e) {
            log.error("No se pudo revisar los mensajes sin respuesta de Lía.", e);
        }
    }

    /** @return cuántos avisos de timeout se registraron en esta pasada */
    public int checkTimeouts() {
        LocalDateTime now = LocalDateTime.now(clock);
        LiaProperties.TimeoutJob job = properties.timeoutJob();
        List<UnansweredMessageQuery.Unanswered> pending = unanswered.find(
                now.minus(Duration.ofHours(job.lookbackHours())),
                now.minusSeconds(properties.timeoutSeconds()),
                job.batchSize());

        int registered = 0;
        for (UnansweredMessageQuery.Unanswered message : pending) {
            try {
                if (handler.handle(new RegisterLiaTimeoutCommand(message.conversationId(), message.messageId()))
                        .isPresent()) {
                    registered++;
                }
            } catch (RuntimeException e) {
                // Un mensaje con problemas no debe impedir avisar a los demás; se reintenta en la siguiente pasada.
                log.error("No se pudo registrar el timeout de Lía para el mensaje {}.", message.messageId(), e);
            }
        }
        return registered;
    }
}
