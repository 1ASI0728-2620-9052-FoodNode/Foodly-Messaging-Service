package pe.foodly.messaging.infrastructure.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.foodly.messaging.application.commandhandlers.RegisterLiaTimeoutCommandHandler;
import pe.foodly.messaging.application.commands.RegisterLiaTimeoutCommand;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.infrastructure.lia.LiaGatewayMode;
import pe.foodly.messaging.infrastructure.lia.LiaProperties;
import pe.foodly.messaging.infrastructure.persistence.jpa.UnansweredMessageQuery;
import pe.foodly.messaging.infrastructure.persistence.jpa.UnansweredMessageQuery.Unanswered;

@ExtendWith(MockitoExtension.class)
class LiaTimeoutJobTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-13T15:00:00Z"), ZoneOffset.UTC);

    @Mock
    private UnansweredMessageQuery unanswered;
    @Mock
    private RegisterLiaTimeoutCommandHandler handler;

    private LiaTimeoutJob job;

    @BeforeEach
    void setUp() {
        LiaProperties properties = new LiaProperties(LiaGatewayMode.STUB, 20, Duration.ofSeconds(5), Duration.ofSeconds(1),
                new LiaProperties.IntegrationSystem(null, null), new LiaProperties.DirectN8n(null, null),
                new LiaProperties.TimeoutJob(10_000, 24, 50));
        job = new LiaTimeoutJob(unanswered, handler, properties, CLOCK);
    }

    private static Unanswered pending() {
        return new Unanswered(ConversationId.newId(), MessageId.newId());
    }

    @Test
    void buscaLosMensajesMasViejosQueElTiempoDeEsperaDentroDeLaVentanaYConElLoteConfigurado() {
        when(unanswered.find(any(), any(), eq(50))).thenReturn(List.of());

        assertThat(job.checkTimeouts()).isZero();

        // 15:00 UTC con zona UTC; umbral = ahora - 20 s; ventana = ahora - 24 h.
        verify(unanswered).find(
                LocalDateTime.of(2026, 11, 12, 15, 0, 0), LocalDateTime.of(2026, 11, 13, 14, 59, 40), 50);
        verify(handler, never()).handle(any());
    }

    @Test
    void ejecutaElCommandPorCadaMensajeSinRespuestaYCuentaLosAvisosRegistrados() {
        Unanswered first = pending();
        Unanswered second = pending();
        when(unanswered.find(any(), any(), eq(50))).thenReturn(List.of(first, second));
        when(handler.handle(new RegisterLiaTimeoutCommand(first.conversationId(), first.messageId())))
                .thenReturn(Optional.of(mock(Message.class)));
        when(handler.handle(new RegisterLiaTimeoutCommand(second.conversationId(), second.messageId())))
                .thenReturn(Optional.empty()); // otro hilo ya lo respondió

        assertThat(job.checkTimeouts()).isEqualTo(1);
    }

    @Test
    void unMensajeConProblemasNoImpideAvisarALosDemas() {
        Unanswered failing = pending();
        Unanswered healthy = pending();
        when(unanswered.find(any(), any(), eq(50))).thenReturn(List.of(failing, healthy));
        doThrow(new IllegalStateException("base caída"))
                .when(handler).handle(new RegisterLiaTimeoutCommand(failing.conversationId(), failing.messageId()));
        when(handler.handle(new RegisterLiaTimeoutCommand(healthy.conversationId(), healthy.messageId())))
                .thenReturn(Optional.of(mock(Message.class)));

        assertThat(job.checkTimeouts()).isEqualTo(1);
    }

    @Test
    void siLaConsultaFallaLaEjecucionProgramadaNoPropagaLaExcepcion() {
        when(unanswered.find(any(), any(), eq(50))).thenThrow(new IllegalStateException("sin conexión"));

        job.run(); // un @Scheduled que lanza excepción no debe romper el ciclo siguiente

        verify(handler, never()).handle(any());
    }
}
