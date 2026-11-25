package pe.foodly.messaging.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.support.TestJwt;

class JwtTokenValidatorTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";

    private final JwtTokenValidator validator = new JwtTokenValidator(properties(TestJwt.SECRET_BASE64));

    private static JwtProperties properties(String secretBase64) {
        return new JwtProperties(secretBase64, TestJwt.ISSUER, TestJwt.AUDIENCE, "sub", "role");
    }

    private Instant inOneHour() {
        return Instant.now().plusSeconds(3600);
    }

    @Test
    void unTokenValidoDaElIdElRolYElTokenCrudoConSuExpiracion() {
        Instant exp = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String jwt = TestJwt.token(TestJwt.SECRET_BASE64, USER, TestJwt.EXPLORER_ROLE, exp);

        AuthenticatedExplorer explorer = validator.validate(jwt).orElseThrow();

        assertThat(explorer.id()).isEqualTo(ExplorerId.of(USER));
        assertThat(explorer.token().value()).isEqualTo(jwt);
        assertThat(explorer.token().expiresAt()).isEqualTo(exp);
        assertThat(explorer.hasRole("GASTRONOMIC_EXPLORER")).isTrue();
        assertThat(explorer.hasRole("HUARIQUE_OWNER")).isFalse();
        assertThat(explorer.token().toString()).doesNotContain(jwt);
    }

    @Test
    void elRolPuedeSerUnTextoOUnaListaConOSinPrefijo() {
        for (Object role : List.of("GASTRONOMIC_EXPLORER", "ROLE_GASTRONOMIC_EXPLORER", "gastronomic_explorer",
                List.of("OTRO", "GASTRONOMIC_EXPLORER"))) {
            AuthenticatedExplorer explorer = validator.validate(
                    TestJwt.token(TestJwt.SECRET_BASE64, USER, role, inOneHour())).orElseThrow();
            assertThat(explorer.hasRole("GASTRONOMIC_EXPLORER")).as("%s", role).isTrue();
        }
    }

    @Test
    void sinRolElTokenEsValidoPeroNoTieneNingunRol() {
        AuthenticatedExplorer explorer = validator.validate(
                TestJwt.token(TestJwt.SECRET_BASE64, USER, null, inOneHour())).orElseThrow();

        assertThat(explorer.roles()).isEmpty();
    }

    @Test
    void rechazaLosTokensInvalidos() {
        List<String> invalid = List.of(
                TestJwt.token(TestJwt.OTHER_SECRET_BASE64, USER, TestJwt.EXPLORER_ROLE, inOneHour()),
                TestJwt.token(TestJwt.SECRET_BASE64, USER, TestJwt.EXPLORER_ROLE, Instant.now().minusSeconds(1)),
                TestJwt.token(TestJwt.SECRET_BASE64, USER, TestJwt.EXPLORER_ROLE, null),
                TestJwt.token(TestJwt.SECRET_BASE64, "no-es-un-uuid", TestJwt.EXPLORER_ROLE, inOneHour()),
                "basura", "a.b.c", "");

        for (String token : invalid) {
            assertThat(validator.validate(token)).as("token %s", token.length()).isEqualTo(Optional.empty());
        }
        assertThat(validator.validate(null)).isEmpty();
    }

    @Test
    void rechazaUnTokenFirmadoConElTextoBase64ComoClave() {
        // La clave se decodifica de Base64: firmar con el texto Base64 como bytes UTF-8 no vale.
        assertThat(validator.validate(TestJwt.signedWithTheBase64TextAsKey(USER))).isEmpty();
    }

    // ---- iss y aud (contrato JWT v1 de IAM) ----

    @Test
    void rechazaUnTokenConOtroEmisorOSinEmisor() {
        assertThat(validator.validate(TestJwt.withIssuer(USER, "otro-emisor"))).isEmpty();
        assertThat(validator.validate(TestJwt.withIssuer(USER, null))).isEmpty();
    }

    @Test
    void rechazaUnTokenSinAudienciaOSinFoodlyMessaging() {
        assertThat(validator.validate(TestJwt.withAudience(USER, null))).isEmpty();
        assertThat(validator.validate(TestJwt.withAudience(USER, List.of("foodly-iam", "foodly-business")))).isEmpty();
    }

    @Test
    void aceptaUnTokenCuyaAudienciaIncluyeFoodlyMessagingJuntoAOtras() {
        assertThat(validator.validate(TestJwt.withAudience(USER, List.of("foodly-business", "foodly-messaging"))))
                .isPresent();
        assertThat(validator.validate(TestJwt.withAudience(USER, List.of("foodly-messaging")))).isPresent();
    }

    @Test
    void elEmisorYLaAudienciaSonConfigurables() {
        JwtTokenValidator custom = new JwtTokenValidator(
                new JwtProperties(TestJwt.SECRET_BASE64, "otro-emisor", "foodly-radar", "sub", "role"));

        assertThat(custom.validate(TestJwt.withIssuer(USER, "otro-emisor"))).isPresent();
        // El token por defecto tiene iss=foodly-iam: con ese emisor configurado ya no sirve.
        assertThat(custom.validate(TestJwt.explorer(USER))).isEmpty();
    }

    /**
     * El token que valida Messaging se reenvía tal cual a Business, Radar y Reservation (ExplorerTokenCache), y
     * cada uno exige su propia audiencia: el token de IAM debe traer las tres.
     */
    @Test
    void elTokenReenviadoALosProveedoresConservaSuAudienciaEnElClaimAud() {
        String jwt = TestJwt.explorer(USER);

        AuthenticatedExplorer explorer = validator.validate(jwt).orElseThrow();

        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TestJwt.SECRET_BASE64)))
                .build().parseSignedClaims(explorer.token().value()).getPayload();
        assertThat(claims.getAudience()).contains("foodly-business", "foodly-radar", "foodly-reservation");
        assertThat(explorer.token().value()).as("el reenvío usa el token original, sin modificarlo").isEqualTo(jwt);
    }

    // ---- claims de usuario y rol ----

    @Test
    void usaLosNombresDeClaimConfigurables() {
        JwtTokenValidator custom = new JwtTokenValidator(
                new JwtProperties(TestJwt.SECRET_BASE64, TestJwt.ISSUER, TestJwt.AUDIENCE, "uid", "roles"));
        String jwt = Jwts.builder().issuer(TestJwt.ISSUER).audience().add(TestJwt.AUDIENCE).and()
                .claim("uid", USER).claim("roles", List.of("GASTRONOMIC_EXPLORER"))
                .expiration(Date.from(inOneHour()))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(TestJwt.SECRET_BASE64)))
                .compact();

        assertThat(custom.validate(jwt)).isPresent();
        assertThat(validator.validate(jwt)).as("con los nombres por defecto no hay 'sub'").isEmpty();
    }

    // ---- la clave: Base64 de al menos 32 bytes ----

    @Test
    void unSecretoAusenteOEnBlancoImpideArrancar() {
        assertThatThrownBy(() -> new JwtTokenValidator(properties(null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET_BASE64");
        assertThatThrownBy(() -> new JwtTokenValidator(properties("  ")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET_BASE64");
    }

    @Test
    void unSecretoDeMenosDe32BytesDecodificadosImpideArrancar() {
        String thirtyOneBytes = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatThrownBy(() -> new JwtTokenValidator(properties(thirtyOneBytes)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
    }

    @Test
    void unSecretoDeExactamente32BytesSeAcepta() {
        assertThat(new JwtTokenValidator(properties(Base64.getEncoder().encodeToString(new byte[32])))).isNotNull();
    }

    @Test
    void unSecretoQueNoEsBase64ImpideArrancar() {
        assertThatThrownBy(() -> new JwtTokenValidator(properties("esto no es base64 ***")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Base64");
    }

    @Test
    void unEmisorOUnaAudienciaEnBlancoImpidenArrancar() {
        assertThatThrownBy(() -> new JwtTokenValidator(
                new JwtProperties(TestJwt.SECRET_BASE64, " ", TestJwt.AUDIENCE, "sub", "role")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_ISSUER");
        assertThatThrownBy(() -> new JwtTokenValidator(
                new JwtProperties(TestJwt.SECRET_BASE64, TestJwt.ISSUER, null, "sub", "role")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_AUDIENCE");
    }

    @Test
    void unSecretoDeCallbackVacioImpideArrancar() {
        assertThatThrownBy(() -> new CallbackSecretFilter(" "))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("INTEGRATION_CALLBACK_SECRET");
        assertThatThrownBy(() -> new CallbackSecretFilter(null)).isInstanceOf(IllegalStateException.class);
    }
}
