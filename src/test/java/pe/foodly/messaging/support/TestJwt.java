package pe.foodly.messaging.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/** Genera JWT firmados como lo haría IAM, para las pruebas de seguridad. */
public final class TestJwt {

    public static final String SECRET = "una-clave-de-prueba-de-al-menos-32-bytes!!";
    public static final String CALLBACK_SECRET = "secreto-de-callback-de-prueba";
    public static final String EXPLORER_ROLE = "GASTRONOMIC_EXPLORER";
    public static final String OWNER_ROLE = "HUARIQUE_OWNER";

    private TestJwt() {
    }

    public static String explorer(String userId) {
        return token(SECRET, userId, EXPLORER_ROLE, Instant.now().plus(Duration.ofHours(1)));
    }

    public static String owner(String userId) {
        return token(SECRET, userId, OWNER_ROLE, Instant.now().plus(Duration.ofHours(1)));
    }

    /** @param role un texto o una lista de textos, como puede venir del claim de rol */
    public static String token(String secret, String userId, Object role, Instant expiresAt) {
        var builder = Jwts.builder().subject(userId).claim("role", role);
        if (expiresAt != null) {
            builder.expiration(Date.from(expiresAt));
        }
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
