package pe.foodly.messaging.interfaces.websocket;

import java.time.Clock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import pe.foodly.messaging.application.queries.CheckConversationOwnershipQuery;
import pe.foodly.messaging.application.queryhandlers.CheckConversationOwnershipQueryHandler;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.infrastructure.security.AuthenticatedExplorer;
import pe.foodly.messaging.infrastructure.security.JwtTokenValidator;
import pe.foodly.messaging.infrastructure.security.SecurityConfig;

/**
 * Seguridad del canal STOMP (DD-04). El handshake HTTP es público; aquí se aplican las reglas:
 * <ul>
 *   <li>{@code CONNECT}: exige un JWT válido de IAM en el header {@code Authorization: Bearer …} y el rol
 *       {@code GASTRONOMIC_EXPLORER}. Si no, la conexión se cierra con un frame ERROR.</li>
 *   <li>{@code SUBSCRIBE}: solo a {@code /topic/conversations/{id}} de una conversación propia y mientras el
 *       token no haya vencido. Cualquier otro destino o una conversación ajena se rechaza (el mismo error,
 *       exista o no).</li>
 *   <li>{@code SEND}: siempre rechazado. Los clientes no publican: Lía responde por el servidor.</li>
 * </ul>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthChannelInterceptor.class);
    private static final Pattern CONVERSATION_TOPIC = Pattern.compile(
            "^" + Pattern.quote(LiaReplyBroadcaster.TOPIC_PREFIX) + "([0-9a-fA-F-]{36})$");

    private final JwtTokenValidator validator;
    private final CheckConversationOwnershipQueryHandler ownership;
    private final Clock clock;

    public StompAuthChannelInterceptor(
            JwtTokenValidator validator, CheckConversationOwnershipQueryHandler ownership, Clock clock) {
        this.validator = validator;
        this.ownership = ownership;
        this.clock = clock;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        StompCommand command = accessor == null ? null : accessor.getCommand();
        if (command == null) {
            return message; // latidos (heart-beats) y mensajes internos
        }
        switch (command) {
            case CONNECT -> connect(accessor);
            case SUBSCRIBE -> subscribe(accessor);
            case SEND -> throw new MessagingException("Los clientes no pueden publicar mensajes en este canal.");
            case DISCONNECT -> { /* siempre se permite cerrar */ }
            default -> authenticatedExplorer(accessor); // UNSUBSCRIBE, ACK, etc.: solo con sesión iniciada
        }
        return message;
    }

    private void connect(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null) {
            header = accessor.getFirstNativeHeader("authorization");
        }
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new MessagingException("Debe iniciar sesión: falta el token de acceso.");
        }
        AuthenticatedExplorer explorer = validator.validate(header.substring(7).trim())
                .orElseThrow(() -> new MessagingException("El token de acceso no es válido o venció."));
        if (!explorer.hasRole(SecurityConfig.EXPLORER_ROLE)) {
            throw new MessagingException("No tiene permisos: se requiere el rol de explorador gastronómico.");
        }
        accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(explorer, null, explorer.authorities()));
    }

    private void subscribe(StompHeaderAccessor accessor) {
        AuthenticatedExplorer explorer = authenticatedExplorer(accessor);
        if (!explorer.token().expiresAt().isAfter(clock.instant())) {
            throw new MessagingException("La sesión venció. Vuelve a conectarte con un token nuevo.");
        }
        String destination = accessor.getDestination();
        Matcher matcher = destination == null ? null : CONVERSATION_TOPIC.matcher(destination);
        if (matcher == null || !matcher.matches()) {
            throw new MessagingException("Solo puedes suscribirte a las conversaciones propias.");
        }
        boolean mine;
        try {
            mine = ownership.handle(new CheckConversationOwnershipQuery(
                    ConversationId.of(matcher.group(1)), explorer.id()));
        } catch (IllegalArgumentException e) {
            mine = false;
        }
        if (!mine) {
            log.debug("Suscripción rechazada a {}.", destination);
            throw new MessagingException("Solo puedes suscribirte a las conversaciones propias.");
        }
    }

    private AuthenticatedExplorer authenticatedExplorer(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedExplorer explorer) {
            return explorer;
        }
        throw new MessagingException("Debe iniciar sesión: conéctese primero con un token de acceso.");
    }
}
