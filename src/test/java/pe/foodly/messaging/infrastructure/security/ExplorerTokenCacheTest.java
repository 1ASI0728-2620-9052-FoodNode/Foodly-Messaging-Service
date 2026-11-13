package pe.foodly.messaging.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;

class ExplorerTokenCacheTest {

    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");

    private MutableClock clock;
    private ExplorerTokenCache cache;
    private final MessageId messageId = MessageId.newId();

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        // LIA_TIMEOUT_SECONDS = 30 y margen = 60 → un token vive como máximo 90 s.
        cache = new ExplorerTokenCache(clock, 30, 60);
    }

    private ExplorerToken tokenExpiringIn(Duration duration) {
        return new ExplorerToken("jwt", NOW.plus(duration));
    }

    @Test
    void guardaElTokenPorElIdDelMensaje() {
        ExplorerToken token = tokenExpiringIn(Duration.ofMinutes(60));
        cache.put(messageId, token);

        assertThat(cache.find(messageId)).contains(token);
        assertThat(cache.find(MessageId.newId())).isEmpty();
    }

    @Test
    void viveElTiempoDeEsperaDeLiaMasElMargenSiElTokenDuraMas() {
        cache.put(messageId, tokenExpiringIn(Duration.ofMinutes(60)));

        clock.advance(Duration.ofSeconds(89));
        assertThat(cache.find(messageId)).isPresent();

        clock.advance(Duration.ofSeconds(1));
        assertThat(cache.find(messageId)).isEmpty();
    }

    @Test
    void viveSoloHastaQueVenzaElTokenSiVenceAntes() {
        cache.put(messageId, tokenExpiringIn(Duration.ofSeconds(10)));

        clock.advance(Duration.ofSeconds(9));
        assertThat(cache.find(messageId)).isPresent();

        clock.advance(Duration.ofSeconds(1));
        assertThat(cache.find(messageId)).isEmpty();
    }

    @Test
    void unTokenYaVencidoNoSeGuarda() {
        cache.put(messageId, tokenExpiringIn(Duration.ofSeconds(-5)));

        assertThat(cache.find(messageId)).isEmpty();
    }

    @Test
    void evictRetiraElToken() {
        cache.put(messageId, tokenExpiringIn(Duration.ofMinutes(5)));

        cache.evict(messageId);

        assertThat(cache.find(messageId)).isEmpty();
    }

    @Test
    void alGuardarOtroTokenSeLimpianLosVencidos() {
        MessageId old = MessageId.newId();
        cache.put(old, tokenExpiringIn(Duration.ofSeconds(5)));
        clock.advance(Duration.ofSeconds(6));

        cache.put(messageId, new ExplorerToken("jwt2", clock.instant().plusSeconds(600)));

        assertThat(cache.find(old)).isEmpty();
        assertThat(cache.find(messageId)).isPresent();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
