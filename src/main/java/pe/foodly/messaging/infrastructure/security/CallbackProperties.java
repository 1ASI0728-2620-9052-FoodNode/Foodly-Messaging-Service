package pe.foodly.messaging.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Secreto compartido con el Integration System para el callback de Lía ({@code INTEGRATION_CALLBACK_SECRET}). */
@ConfigurationProperties(prefix = "messaging.security.callback")
public record CallbackProperties(String secret) {
}
