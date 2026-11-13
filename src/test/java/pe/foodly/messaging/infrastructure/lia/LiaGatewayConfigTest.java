package pe.foodly.messaging.infrastructure.lia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import pe.foodly.messaging.application.commandhandlers.RegisterLiaReplyCommandHandler;
import pe.foodly.messaging.domain.ports.ILiaAgentGateway;
import pe.foodly.messaging.infrastructure.security.CallbackProperties;

class LiaGatewayConfigTest {

    private final LiaGatewayConfig config = new LiaGatewayConfig();
    private final CallbackProperties callback = new CallbackProperties("secreto");

    @SuppressWarnings("unchecked")
    private final ObjectProvider<RegisterLiaReplyCommandHandler> handler = org.mockito.Mockito.mock(ObjectProvider.class);

    private LiaProperties properties(LiaGatewayMode mode, String isUrl, String isPath, String webhook, String callbackUrl) {
        return new LiaProperties(mode, 20, Duration.ofSeconds(5), Duration.ofSeconds(1),
                new LiaProperties.IntegrationSystem(isUrl, isPath),
                new LiaProperties.DirectN8n(webhook, callbackUrl),
                new LiaProperties.TimeoutJob(10_000, 24, 50));
    }

    private ILiaAgentGateway gateway(LiaProperties properties) {
        return config.liaAgentGateway(properties, callback, handler, Clock.systemUTC());
    }

    @Test
    void integrationSystemCreaElGatewayHttp() {
        assertThat(gateway(properties(LiaGatewayMode.INTEGRATION_SYSTEM, "http://is.test/", "lia/interpret", null, null)))
                .isInstanceOf(N8nLiaAgentGateway.class);
    }

    @Test
    void directN8nCreaElGatewayHttp() {
        assertThat(gateway(properties(LiaGatewayMode.DIRECT_N8N, null, null,
                "http://n8n:5678/webhook/lia", "http://messaging:8080/messaging/callbacks/n8n")))
                .isInstanceOf(N8nLiaAgentGateway.class);
    }

    @Test
    void stubCreaElGatewayQueResponde() {
        ILiaAgentGateway stub = gateway(properties(LiaGatewayMode.STUB, null, null, null, null));

        assertThat(stub).isInstanceOf(StubLiaAgentGateway.class);
        ((StubLiaAgentGateway) stub).close();
    }

    @Test
    void integrationSystemSinUrlOSinRutaImpideArrancar() {
        assertThatThrownBy(() -> gateway(properties(LiaGatewayMode.INTEGRATION_SYSTEM, "", "/lia", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("INTEGRATION_SYSTEM_URL");
        assertThatThrownBy(() -> gateway(properties(LiaGatewayMode.INTEGRATION_SYSTEM, "http://is.test", " ", null, null)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("LIA_PATH");
    }

    @Test
    void directN8nSinWebhookImpideArrancar() {
        assertThatThrownBy(() -> gateway(properties(LiaGatewayMode.DIRECT_N8N, null, null, null, "http://cb")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("N8N_WEBHOOK_URL");
    }

    @Test
    void sinConfiguracionElModoEsIntegrationSystemYLiaTieneVeinteSegundos() {
        LiaProperties bound = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("messaging.lia", LiaProperties.class);

        assertThat(bound.mode()).isEqualTo(LiaGatewayMode.INTEGRATION_SYSTEM);
        assertThat(bound.timeoutSeconds()).isEqualTo(20);
        assertThat(bound.requestTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(bound.stubDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(bound.timeoutJob().intervalMs()).isEqualTo(10_000);
    }

    @Test
    void losModosSeEscribenComoEnLaVariableDeEntorno() {
        for (var entry : Map.of(
                "integration-system", LiaGatewayMode.INTEGRATION_SYSTEM,
                "direct-n8n", LiaGatewayMode.DIRECT_N8N,
                "stub", LiaGatewayMode.STUB).entrySet()) {
            LiaProperties bound = new Binder(new MapConfigurationPropertySource(
                    Map.of("messaging.lia.mode", entry.getKey()))).bindOrCreate("messaging.lia", LiaProperties.class);

            assertThat(bound.mode()).isEqualTo(entry.getValue());
        }
    }
}
