package pe.foodly.messaging.interfaces.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.queries.CheckConversationOwnershipQuery;
import pe.foodly.messaging.application.queryhandlers.CheckConversationOwnershipQueryHandler;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.infrastructure.security.AuthenticatedExplorer;
import pe.foodly.messaging.infrastructure.security.JwtProperties;
import pe.foodly.messaging.infrastructure.security.JwtTokenValidator;
import pe.foodly.messaging.support.TestJwt;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    private static final String EXPLORER = "11111111-1111-1111-1111-111111111111";
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");

    @Mock
    private CheckConversationOwnershipQueryHandler ownership;
    @Mock
    private MessageChannel channel;

    private StompAuthChannelInterceptor interceptor;
    private final ConversationId conversation = ConversationId.newId();

    @BeforeEach
    void setUp() {
        JwtTokenValidator validator = new JwtTokenValidator(
                new JwtProperties(TestJwt.SECRET_BASE64, TestJwt.ISSUER, TestJwt.AUDIENCE, "sub", "role"));
        interceptor = new StompAuthChannelInterceptor(validator, ownership, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Message<byte[]> frame(StompCommand command, String destination, String authorization,
                                  AuthenticatedExplorer user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        if (user != null) {
            accessor.setUser(UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static AuthenticatedExplorer explorer(Instant expiresAt) {
        return new AuthenticatedExplorer(
                ExplorerId.of(EXPLORER), new ExplorerToken("jwt", expiresAt), java.util.Set.of("ROLE_GASTRONOMIC_EXPLORER"));
    }

    private String topic() {
        return "/topic/conversations/" + conversation;
    }

    // ---- CONNECT ----

    @Test
    void connectConUnJwtDeExploradorValidoAutenticaLaSesion() {
        String jwt = TestJwt.explorer(EXPLORER);
        Message<byte[]> connect = frame(StompCommand.CONNECT, null, "Bearer " + jwt, null);

        assertThat(interceptor.preSend(connect, channel)).isSameAs(connect);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(connect);
        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        var principal = ((UsernamePasswordAuthenticationToken) accessor.getUser()).getPrincipal();
        assertThat(principal).isInstanceOf(AuthenticatedExplorer.class);
        assertThat(((AuthenticatedExplorer) principal).id()).isEqualTo(ExplorerId.of(EXPLORER));
    }

    @Test
    void connectSinTokenOConUnTokenInvalidoSeRechaza() {
        String forged = TestJwt.token(TestJwt.OTHER_SECRET_BASE64, EXPLORER, TestJwt.EXPLORER_ROLE,
                Instant.now().plusSeconds(3600));
        String expired = TestJwt.token(TestJwt.SECRET_BASE64, EXPLORER, TestJwt.EXPLORER_ROLE, Instant.now().minusSeconds(5));

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, null, null), channel))
                .isInstanceOf(MessagingException.class).hasMessageContaining("falta el token");
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, "Basic abc", null), channel))
                .isInstanceOf(MessagingException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, "Bearer " + forged, null), channel))
                .isInstanceOf(MessagingException.class).hasMessageContaining("no es válido");
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, "Bearer " + expired, null), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void connectConRolHuariqueOwnerSeRechaza() {
        Message<byte[]> connect = frame(StompCommand.CONNECT, null, "Bearer " + TestJwt.owner(EXPLORER), null);

        assertThatThrownBy(() -> interceptor.preSend(connect, channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("explorador gastronómico");
        assertThat(StompHeaderAccessor.wrap(connect).getUser()).isNull();
    }

    @Test
    void connectConEmisorOAudienciaIncorrectosSeRechaza() {
        String otherIssuer = TestJwt.withIssuer(EXPLORER, "otro-emisor");
        String noIssuer = TestJwt.withIssuer(EXPLORER, null);
        String noAudience = TestJwt.withAudience(EXPLORER, null);
        String notForMessaging = TestJwt.withAudience(EXPLORER, java.util.List.of("foodly-iam", "foodly-business"));
        String base64TextAsKey = TestJwt.signedWithTheBase64TextAsKey(EXPLORER);

        for (String jwt : java.util.List.of(otherIssuer, noIssuer, noAudience, notForMessaging, base64TextAsKey)) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, "Bearer " + jwt, null), channel))
                    .isInstanceOf(MessagingException.class).hasMessageContaining("no es válido");
        }
    }

    @Test
    void connectAceptaUnTokenCuyaAudienciaIncluyeFoodlyMessagingJuntoAOtras() {
        String jwt = TestJwt.withAudience(EXPLORER, java.util.List.of("foodly-business", "foodly-messaging"));
        Message<byte[]> connect = frame(StompCommand.CONNECT, null, "Bearer " + jwt, null);

        assertThat(interceptor.preSend(connect, channel)).isSameAs(connect);
        assertThat(StompHeaderAccessor.wrap(connect).getUser()).isNotNull();
    }

    // ---- SUBSCRIBE ----

    @Test
    void subscribeAUnaConversacionPropiaSePermite() {
        when(ownership.handle(new CheckConversationOwnershipQuery(conversation, ExplorerId.of(EXPLORER)))).thenReturn(true);
        Message<byte[]> subscribe = frame(StompCommand.SUBSCRIBE, topic(), null, explorer(NOW.plusSeconds(600)));

        assertThat(interceptor.preSend(subscribe, channel)).isSameAs(subscribe);
    }

    @Test
    void subscribeAUnaConversacionAjenaOInexistenteSeRechazaConElMismoError() {
        when(ownership.handle(any())).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(
                        frame(StompCommand.SUBSCRIBE, topic(), null, explorer(NOW.plusSeconds(600))), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("conversaciones propias");
    }

    @Test
    void subscribeAOtroDestinoOConIdMalFormadoSeRechazaSinConsultarLaBase() {
        AuthenticatedExplorer user = explorer(NOW.plusSeconds(600));

        for (String destination : new String[] {
                "/topic/otra-cosa", "/topic/conversations/", "/topic/conversations/abc",
                "/topic/conversations/" + conversation + "/extra", "/topic/conversations/*", "/queue/x", null}) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, destination, null, user), channel))
                    .as("destino %s", destination)
                    .isInstanceOf(MessagingException.class);
        }
        verifyNoInteractions(ownership);
    }

    @Test
    void subscribeSinHaberIniciadoSesionSeRechaza() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, topic(), null, null), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("Debe iniciar sesión");
        verifyNoInteractions(ownership);
    }

    @Test
    void subscribeConElTokenVencidoSeRechaza() {
        assertThatThrownBy(() -> interceptor.preSend(
                        frame(StompCommand.SUBSCRIBE, topic(), null, explorer(NOW.minusSeconds(1))), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("venció");
        verifyNoInteractions(ownership);
    }

    // ---- SEND y otros ----

    @Test
    void losClientesNoPuedenPublicar() {
        assertThatThrownBy(() -> interceptor.preSend(
                        frame(StompCommand.SEND, topic(), null, explorer(NOW.plusSeconds(600))), channel))
                .isInstanceOf(MessagingException.class)
                .hasMessageContaining("no pueden publicar");
    }

    @Test
    void disconnectSiemprePuedeCerrarYElRestoDeFramesExigeSesion() {
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.DISCONNECT, null, null, null), channel))
                .doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preSend(
                        frame(StompCommand.UNSUBSCRIBE, null, null, explorer(NOW.plusSeconds(60))), channel))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.UNSUBSCRIBE, null, null, null), channel))
                .isInstanceOf(MessagingException.class);
    }

    @Test
    void losLatidosSinComandoPasanSinTocarse() {
        StompHeaderAccessor heartbeat = StompHeaderAccessor.createForHeartbeat();
        heartbeat.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], heartbeat.getMessageHeaders());

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }
}
