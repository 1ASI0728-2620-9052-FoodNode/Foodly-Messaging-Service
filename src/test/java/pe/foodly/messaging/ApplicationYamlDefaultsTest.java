package pe.foodly.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pe.foodly.messaging.support.TestJwt;

/** Defaults de application.yml (sin variables de entorno), sin levantar MySQL ni ningún otro servicio. */
class ApplicationYamlDefaultsTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner();

    private ApplicationContextRunner withLocalProfile() {
        return runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local"))
                .withInitializer(new ConfigDataApplicationContextInitializer());
    }

    @Test
    void theDefaultPortIs8083AndTheCallbackUrlFollowsIt() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer()).run(context -> {
            var environment = context.getEnvironment();

            assertThat(environment.getProperty("server.port")).isEqualTo("8083");
            assertThat(environment.getProperty("messaging.lia.direct-n8n.callback-url"))
                    .isEqualTo("http://localhost:8083/messaging/callbacks/n8n");
        });
    }

    @Test
    void jwtDefaultsMatchTheIamContract() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("JWT_SECRET_BASE64=" + TestJwt.SECRET_BASE64).run(context -> {
                    var environment = context.getEnvironment();

                    assertThat(environment.getProperty("messaging.security.jwt.secret-base64"))
                            .isEqualTo(TestJwt.SECRET_BASE64);
                    assertThat(environment.getProperty("messaging.security.jwt.issuer")).isEqualTo("foodly-iam");
                    assertThat(environment.getProperty("messaging.security.jwt.audience")).isEqualTo("foodly-messaging");
                    assertThat(environment.getProperty("messaging.security.jwt.user-id-claim")).isEqualTo("sub");
                    assertThat(environment.getProperty("messaging.security.jwt.role-claim")).isEqualTo("role");
                });
    }

    @Test
    void theLocalProfileDefaultsTheProviderUrlsToTheProjectPortMap() {
        withLocalProfile().run(context -> {
            var environment = context.getEnvironment();

            assertThat(environment.getProperty("messaging.clients.radar.url")).isEqualTo("http://localhost:8082");
            assertThat(environment.getProperty("messaging.clients.business.url")).isEqualTo("http://localhost:8080");
            assertThat(environment.getProperty("messaging.clients.reservation.url")).isEqualTo("http://localhost:8084");
        });
    }

    @Test
    void theLocalProfileUrlsCanBeOverriddenByEnvironment() {
        withLocalProfile().withPropertyValues("RADAR_URL=http://radar:9", "BUSINESS_URL=http://business:9",
                "RESERVATION_URL=http://reservation:9").run(context -> {
            var environment = context.getEnvironment();

            assertThat(environment.getProperty("messaging.clients.radar.url")).isEqualTo("http://radar:9");
            assertThat(environment.getProperty("messaging.clients.business.url")).isEqualTo("http://business:9");
            assertThat(environment.getProperty("messaging.clients.reservation.url")).isEqualTo("http://reservation:9");
        });
    }
}
