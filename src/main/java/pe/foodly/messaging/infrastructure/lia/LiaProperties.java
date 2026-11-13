package pe.foodly.messaging.infrastructure.lia;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de Lía ({@code messaging.lia.*}).
 *
 * @param mode {@code LIA_GATEWAY_MODE}
 * @param timeoutSeconds {@code LIA_TIMEOUT_SECONDS}: tiempo que Lía tiene para responder antes del aviso de timeout
 * @param requestTimeout tiempo máximo de la llamada HTTP que entrega la solicitud (no el de la respuesta de Lía)
 * @param stubDelay espera del modo {@code stub} antes de «responder»
 */
@ConfigurationProperties(prefix = "messaging.lia")
public record LiaProperties(
        @DefaultValue("integration-system") LiaGatewayMode mode,
        @DefaultValue("20") long timeoutSeconds,
        @DefaultValue("5s") Duration requestTimeout,
        @DefaultValue("1s") Duration stubDelay,
        @DefaultValue IntegrationSystem integrationSystem,
        @DefaultValue DirectN8n directN8n,
        @DefaultValue TimeoutJob timeoutJob) {

    /** {@code INTEGRATION_SYSTEM_URL} y {@code LIA_PATH}: obligatorias en el modo {@code integration-system}. */
    public record IntegrationSystem(String url, String path) {
    }

    /**
     * {@code N8N_WEBHOOK_URL} y {@code MESSAGING_CALLBACK_URL}: la URL del webhook de n8n y la del callback de
     * Messaging que n8n debe invocar, vista desde n8n (en Docker, el nombre del servicio).
     */
    public record DirectN8n(String webhookUrl, String callbackUrl) {
    }

    /**
     * @param intervalMs cada cuántos ms corre {@code LiaTimeoutJob} (por defecto 10 s)
     * @param lookbackHours hasta cuántas horas atrás busca mensajes sin respuesta
     * @param batchSize máximo de avisos por ejecución
     */
    public record TimeoutJob(
            @DefaultValue("10000") long intervalMs,
            @DefaultValue("24") long lookbackHours,
            @DefaultValue("50") int batchSize) {
    }
}
