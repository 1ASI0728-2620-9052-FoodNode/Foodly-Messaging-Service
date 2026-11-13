package pe.foodly.messaging.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Protege el callback de Lía con el secreto compartido con el Integration System, en el header
 * {@code X-Callback-Secret} (5.6.2, 5.6.4). No usa JWT. Si el secreto coincide, autentica la petición con
 * el rol {@code INTEGRATION_SYSTEM}; si no, la deja sin autenticar y la cadena responde 401.
 *
 * <p>La comparación es de tiempo constante: se comparan los resúmenes SHA-256 de ambos secretos con
 * {@link MessageDigest#isEqual}, así ni el contenido ni la longitud del secreto se filtran por el tiempo
 * de respuesta.
 */
public class CallbackSecretFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Callback-Secret";
    public static final String ROLE = "INTEGRATION_SYSTEM";

    private final byte[] expectedDigest;

    public CallbackSecretFilter(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("INTEGRATION_CALLBACK_SECRET es obligatorio.");
        }
        this.expectedDigest = sha256(secret);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided != null && MessageDigest.isEqual(expectedDigest, sha256(provided))) {
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    "integration-system", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible.", e);
        }
    }
}
