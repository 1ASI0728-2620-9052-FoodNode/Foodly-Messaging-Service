package pe.foodly.messaging.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.support.TestJwt;

class JwtTokenValidatorTest {

    private static final String USER = "11111111-1111-1111-1111-111111111111";

    private final JwtTokenValidator validator = new JwtTokenValidator(new JwtProperties(TestJwt.SECRET, "sub", "role"));

    private Instant inOneHour() {
        return Instant.now().plusSeconds(3600);
    }

    @Test
    void unTokenValidoDaElIdElRolYElTokenCrudoConSuExpiracion() {
        Instant exp = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String jwt = TestJwt.token(TestJwt.SECRET, USER, TestJwt.EXPLORER_ROLE, exp);

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
                    TestJwt.token(TestJwt.SECRET, USER, role, inOneHour())).orElseThrow();
            assertThat(explorer.hasRole("GASTRONOMIC_EXPLORER")).as("%s", role).isTrue();
        }
    }

    @Test
    void sinRolElTokenEsValidoPeroNoTieneNingunRol() {
        AuthenticatedExplorer explorer = validator.validate(
                TestJwt.token(TestJwt.SECRET, USER, null, inOneHour())).orElseThrow();

        assertThat(explorer.roles()).isEmpty();
    }

    @Test
    void rechazaLosTokensInvalidos() {
        List<String> invalid = List.of(
                TestJwt.token("otro-secreto-distinto-de-al-menos-32-bytes!!", USER, TestJwt.EXPLORER_ROLE, inOneHour()),
                TestJwt.token(TestJwt.SECRET, USER, TestJwt.EXPLORER_ROLE, Instant.now().minusSeconds(1)),
                TestJwt.token(TestJwt.SECRET, USER, TestJwt.EXPLORER_ROLE, null),
                TestJwt.token(TestJwt.SECRET, "no-es-un-uuid", TestJwt.EXPLORER_ROLE, inOneHour()),
                "basura", "a.b.c", "");

        for (String token : invalid) {
            assertThat(validator.validate(token)).as("token %s", token.length()).isEqualTo(Optional.empty());
        }
        assertThat(validator.validate(null)).isEmpty();
    }

    @Test
    void usaLosNombresDeClaimConfigurables() {
        JwtTokenValidator custom = new JwtTokenValidator(new JwtProperties(TestJwt.SECRET, "uid", "roles"));
        String jwt = io.jsonwebtoken.Jwts.builder().claim("uid", USER).claim("roles", List.of("GASTRONOMIC_EXPLORER"))
                .expiration(java.util.Date.from(inOneHour()))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        TestJwt.SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        assertThat(custom.validate(jwt)).isPresent();
        assertThat(validator.validate(jwt)).as("con los nombres por defecto no hay 'sub'").isEmpty();
    }

    @Test
    void unSecretoCortoOAusenteImpideArrancar() {
        assertThatThrownBy(() -> new JwtTokenValidator(new JwtProperties("corto", "sub", "role")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_SECRET");
        assertThatThrownBy(() -> new JwtTokenValidator(new JwtProperties(null, "sub", "role")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unSecretoDeCallbackVacioImpideArrancar() {
        assertThatThrownBy(() -> new CallbackSecretFilter(" "))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("INTEGRATION_CALLBACK_SECRET");
        assertThatThrownBy(() -> new CallbackSecretFilter(null)).isInstanceOf(IllegalStateException.class);
    }
}
