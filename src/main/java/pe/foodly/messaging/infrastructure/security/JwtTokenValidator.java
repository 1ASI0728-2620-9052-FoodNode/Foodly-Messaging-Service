package pe.foodly.messaging.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;

/**
 * Valida el JWT emitido por IAM (HMAC con clave compartida, firma y expiración; 5.1.4) y extrae al
 * usuario. Lo usan el filtro HTTP y el canal STOMP, para que las dos puertas apliquen la misma regla
 * (DD-06, QA-1: cada servicio valida el token aunque Kong ya lo haya hecho).
 *
 * <p>Un token es inválido si la firma o la expiración fallan, si no trae {@code exp} (IAM siempre lo
 * incluye) o si el claim del id de usuario no es un UUID. Nunca se registra el token, solo el tipo de fallo.
 */
public class JwtTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenValidator.class);
    private static final String ROLE_PREFIX = "ROLE_";
    /** HS256 exige una clave de al menos 256 bits. */
    private static final int MIN_SECRET_BYTES = 32;

    private final JwtParser parser;
    private final String userIdClaim;
    private final String roleClaim;

    public JwtTokenValidator(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET es obligatorio y debe tener al menos " + MIN_SECRET_BYTES + " bytes.");
        }
        // El secreto se usa como bytes UTF-8 (no Base64). Confirmar con quien implementa IAM.
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.parser = Jwts.parser().verifyWith(key).build();
        this.userIdClaim = properties.userIdClaim();
        this.roleClaim = properties.roleClaim();
    }

    public Optional<AuthenticatedExplorer> validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = parser.parseSignedClaims(rawToken).getPayload();
            Object userId = claims.get(userIdClaim);
            Date expiration = claims.getExpiration();
            if (userId == null || expiration == null) {
                log.debug("Token rechazado: falta el claim '{}' o la expiración.", userIdClaim);
                return Optional.empty();
            }
            return Optional.of(new AuthenticatedExplorer(
                    ExplorerId.of(userId.toString()),
                    new ExplorerToken(rawToken, expiration.toInstant()),
                    rolesOf(claims.get(roleClaim))));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token rechazado: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** El rol puede venir como texto o como lista de textos, con o sin el prefijo {@code ROLE_}. */
    private static Set<String> rolesOf(Object roleClaimValue) {
        Collection<?> roles;
        if (roleClaimValue == null) {
            roles = List.of();
        } else if (roleClaimValue instanceof Collection<?> collection) {
            roles = collection;
        } else {
            roles = List.of(roleClaimValue);
        }
        return roles.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(role -> role.toUpperCase(Locale.ROOT))
                .map(role -> role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role)
                .collect(Collectors.toSet());
    }
}
