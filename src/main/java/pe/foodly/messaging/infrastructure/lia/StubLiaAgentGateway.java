package pe.foodly.messaging.infrastructure.lia;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.foodly.messaging.application.commandhandlers.RegisterLiaReplyCommandHandler;
import pe.foodly.messaging.application.commands.RegisterLiaReplyCommand;
import pe.foodly.messaging.domain.model.valueobjects.ConversationContext;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.ports.ILiaAgentGateway;

/**
 * Modo {@code stub} de Lía: sin n8n, sin OpenAI y sin Integration System. Clasifica el mensaje con
 * {@link StubIntentInterpreter} y, tras una espera (1 s por defecto), ejecuta el mismo
 * {@link RegisterLiaReplyCommand} que produciría el callback real, de modo que el resto del flujo (ruteo,
 * consultas a Radar/Business/Reservation, STOMP) es el verdadero.
 */
public class StubLiaAgentGateway implements ILiaAgentGateway, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StubLiaAgentGateway.class);

    private final RegisterLiaReplyCommandHandler handler;
    private final Duration delay;
    private final StubIntentInterpreter interpreter;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "lia-stub");
        thread.setDaemon(true);
        return thread;
    });

    public StubLiaAgentGateway(RegisterLiaReplyCommandHandler handler, Duration delay, Clock clock) {
        this.handler = handler;
        this.delay = delay;
        this.interpreter = new StubIntentInterpreter(clock);
    }

    @Override
    public void requestInterpretation(MessageId id, MessageContent content, ConversationContext ctx) {
        StubIntentInterpreter.Interpretation interpretation =
                interpreter.interpret(content.text(), ctx.location(), ctx.history());
        RegisterLiaReplyCommand command = new RegisterLiaReplyCommand(
                ctx.conversationId(), id, interpretation.replyText(), interpretation.intent(), List.of(), null);
        scheduler.schedule(() -> reply(command), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void reply(RegisterLiaReplyCommand command) {
        try {
            handler.handle(command);
        } catch (RuntimeException e) {
            log.warn("El stub de Lía no pudo registrar la respuesta al mensaje {}: {}",
                    command.inReplyToMessageId(), e.getMessage());
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
