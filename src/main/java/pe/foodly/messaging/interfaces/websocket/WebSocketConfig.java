package pe.foodly.messaging.interfaces.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Canal STOMP sobre WebSocket para entregar la respuesta de Lía sin polling (DD-04). Endpoint
 * {@code /messaging/ws}, con el broker simple en memoria (una sola instancia, DD-10) y destinos
 * {@code /topic/…}. No hay destinos de aplicación: los clientes solo se suscriben.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/messaging/ws";

    /** Latido en ms (cliente y servidor), para que Kong y los balanceadores no cierren un canal inactivo. */
    private static final long HEARTBEAT_MS = 10_000;

    private final StompAuthChannelInterceptor authInterceptor;
    private final String[] allowedOrigins;

    public WebSocketConfig(
            StompAuthChannelInterceptor authInterceptor,
            @Value("${messaging.websocket.allowed-origins:*}") String[] allowedOrigins) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // La credencial es un JWT en el frame CONNECT (no una cookie), así que el origen no es un riesgo de CSRF.
        registry.addEndpoint(ENDPOINT).setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.setPoolSize(1);
        heartbeatScheduler.setThreadNamePrefix("stomp-heartbeat-");
        heartbeatScheduler.initialize();
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[] {HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(heartbeatScheduler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
