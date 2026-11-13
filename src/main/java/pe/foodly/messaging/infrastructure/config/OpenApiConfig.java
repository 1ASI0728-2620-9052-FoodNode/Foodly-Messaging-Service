package pe.foodly.messaging.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación OpenAPI en español ({@code /swagger-ui.html}, {@code /v3/api-docs}). */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";
    public static final String CALLBACK_SCHEME = "callbackSecret";

    @Bean
    OpenAPI messagingOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Foodly - Messaging Service (MS)")
                        .version("0.0.1")
                        .description("""
                                Experiencia conversacional de Foodly. El explorador le escribe a Lía, que \
                                interpreta su intención en lenguaje natural; Messaging coordina la respuesta con \
                                Radar (huariques cercanos), Business (detalle y menú) y Reservation (reservas).

                                **Respuesta de Lía.** El envío de un mensaje responde 202 y la respuesta llega de \
                                forma asíncrona por STOMP sobre WebSocket: endpoint `/messaging/ws` (se envía el \
                                JWT en el header `Authorization: Bearer …` del frame CONNECT) y suscripción a \
                                `/topic/conversations/{conversationId}`, solo a conversaciones propias. Cada \
                                mensaje publicado tiene el formato de `MessageResource`. \
                                `GET /messaging/conversations/{conversationId}/messages` es la fuente de verdad \
                                y sirve de respaldo."""))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("JWT emitido por IAM (firma HMAC, expira a los 60 minutos). "
                                        + "Se requiere el rol GASTRONOMIC_EXPLORER."))
                        .addSecuritySchemes(CALLBACK_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Callback-Secret")
                                .description("Secreto compartido con el Integration System "
                                        + "(INTEGRATION_CALLBACK_SECRET). Solo para el callback de Lía.")));
    }
}
