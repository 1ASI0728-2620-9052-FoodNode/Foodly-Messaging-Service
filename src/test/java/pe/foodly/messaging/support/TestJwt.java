package pe.foodly.messaging.support;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Genera JWT como los emite IAM: HS256 con la clave compartida, {@code iss=foodly-iam}, {@code aud} con todos los
 * destinatarios de Foodly, {@code sub}, {@code role}, {@code iat} y {@code exp}. Las claves son Base64
 * ({@link #SECRET_BASE64}) y se firma con los bytes decodificados, igual que {@code JwtTokenIssuer} de IAM.
 */
public final class TestJwt {

    /** Lo que se configura en {@code JWT_SECRET_BASE64}: Base64 de 42 bytes (literal, para usarlo en anotaciones). */
    public static final String SECRET_BASE64 = "dW5hLWNsYXZlLWRlLXBydWViYS1kZS1hbC1tZW5vcy0zMi1ieXRlcyEh";
    /** Otra clave válida (44 bytes), para firmar tokens que Messaging debe rechazar. */
    public static final String OTHER_SECRET_BASE64 = "b3Ryby1zZWNyZXRvLWRpc3RpbnRvLWRlLWFsLW1lbm9zLTMyLWJ5dGVzISE=";
    public static final String CALLBACK_SECRET = "secreto-de-callback-de-prueba";

    public static final String ISSUER = "foodly-iam";
    public static final String AUDIENCE = "foodly-messaging";
    /** Los destinatarios que IAM emite por defecto. */
    public static final List<String> IAM_AUDIENCES = List.of(
            "foodly-iam", "foodly-business", "foodly-radar", "foodly-messaging", "foodly-reservation",
            "foodly-community");

    public static final String EXPLORER_ROLE = "GASTRONOMIC_EXPLORER";
    public static final String OWNER_ROLE = "HUARIQUE_OWNER";

    private TestJwt() {
    }

    public static String explorer(String userId) {
        return token(SECRET_BASE64, userId, EXPLORER_ROLE, Instant.now().plus(Duration.ofHours(1)));
    }

    public static String owner(String userId) {
        return token(SECRET_BASE64, userId, OWNER_ROLE, Instant.now().plus(Duration.ofHours(1)));
    }

    /**
     * @param secretBase64 la clave en Base64
     * @param role un texto o una lista de textos, como puede venir del claim de rol
     */
    public static String token(String secretBase64, String userId, Object role, Instant expiresAt) {
        return build(secretBase64, userId, role, expiresAt, ISSUER, IAM_AUDIENCES);
    }

    /** Explorador válido con otro {@code iss}; {@code issuer} nulo omite el claim. */
    public static String withIssuer(String userId, String issuer) {
        return build(SECRET_BASE64, userId, EXPLORER_ROLE, Instant.now().plus(Duration.ofHours(1)), issuer,
                IAM_AUDIENCES);
    }

    /** Explorador válido con otra {@code aud}; nula o vacía omite el claim. */
    public static String withAudience(String userId, Collection<String> audience) {
        return build(SECRET_BASE64, userId, EXPLORER_ROLE, Instant.now().plus(Duration.ofHours(1)), ISSUER, audience);
    }

    /** Firmado con el texto Base64 usado como bytes UTF-8: el error típico al no decodificar la clave. */
    public static String signedWithTheBase64TextAsKey(String userId) {
        JwtBuilder builder = base(userId, EXPLORER_ROLE, Instant.now().plus(Duration.ofHours(1)), ISSUER, IAM_AUDIENCES);
        return builder.signWith(Keys.hmacShaKeyFor(SECRET_BASE64.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }

    private static String build(String secretBase64, String userId, Object role, Instant expiresAt, String issuer,
                                Collection<String> audience) {
        return base(userId, role, expiresAt, issuer, audience)
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64)), Jwts.SIG.HS256)
                .compact();
    }

    private static JwtBuilder base(String userId, Object role, Instant expiresAt, String issuer,
                                   Collection<String> audience) {
        JwtBuilder builder = Jwts.builder()
                .subject(userId)
                .claim("role", role)
                .issuedAt(Date.from(Instant.now().minusSeconds(60)));
        if (expiresAt != null) {
            builder.expiration(Date.from(expiresAt));
        }
        if (issuer != null) {
            builder.issuer(issuer);
        }
        if (audience != null && !audience.isEmpty()) {
            builder.audience().add(audience).and();
        }
        return builder;
    }
}
