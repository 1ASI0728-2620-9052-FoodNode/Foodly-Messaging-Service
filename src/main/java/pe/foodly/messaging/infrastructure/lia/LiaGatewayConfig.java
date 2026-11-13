package pe.foodly.messaging.infrastructure.lia;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import pe.foodly.messaging.application.commandhandlers.RegisterLiaReplyCommandHandler;
import pe.foodly.messaging.domain.ports.ILiaAgentGateway;
import pe.foodly.messaging.infrastructure.security.CallbackProperties;

/** Elige la implementación de {@link ILiaAgentGateway} según {@code LIA_GATEWAY_MODE}. */
@Configuration
@EnableConfigurationProperties(LiaProperties.class)
class LiaGatewayConfig {

    private static final Logger log = LoggerFactory.getLogger(LiaGatewayConfig.class);

    @Bean
    ILiaAgentGateway liaAgentGateway(
            LiaProperties properties,
            CallbackProperties callback,
            ObjectProvider<RegisterLiaReplyCommandHandler> replyHandler,
            Clock clock) {
        return switch (properties.mode()) {
            case INTEGRATION_SYSTEM -> integrationSystem(properties);
            case DIRECT_N8N -> directN8n(properties, callback);
            case STUB -> {
                log.warn("Lía en modo STUB: clasifica por palabras clave, sin IA. No es para producción.");
                yield new StubLiaAgentGateway(replyHandler.getObject(), properties.stubDelay(), clock);
            }
        };
    }

    private ILiaAgentGateway integrationSystem(LiaProperties properties) {
        String url = require(properties.integrationSystem().url(), "INTEGRATION_SYSTEM_URL", "integration-system");
        String path = require(properties.integrationSystem().path(), "LIA_PATH", "integration-system");
        URI target = URI.create(url.replaceAll("/+$", "") + (path.startsWith("/") ? path : "/" + path));
        log.info("Lía en modo integration-system.");
        return new N8nLiaAgentGateway(restClient(properties.requestTimeout()), target, null, "El Integration System");
    }

    private ILiaAgentGateway directN8n(LiaProperties properties, CallbackProperties callback) {
        String webhook = require(properties.directN8n().webhookUrl(), "N8N_WEBHOOK_URL", "direct-n8n");
        String callbackUrl = require(properties.directN8n().callbackUrl(), "MESSAGING_CALLBACK_URL", "direct-n8n");
        log.warn("Lía en modo DIRECT-N8N (solo desarrollo local): cada solicitud envía a n8n el secreto del "
                + "callback. En producción use integration-system (DD-07).");
        return new N8nLiaAgentGateway(
                restClient(properties.requestTimeout()),
                URI.create(webhook),
                new N8nLiaAgentGateway.DirectCallback(callbackUrl, callback.secret()),
                "n8n");
    }

    private static String require(String value, String variable, String mode) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(variable + " es obligatoria con LIA_GATEWAY_MODE=" + mode + ".");
        }
        return value.strip();
    }

    private static RestClient restClient(Duration timeout) {
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build());
        factory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(factory).build();
    }
}
