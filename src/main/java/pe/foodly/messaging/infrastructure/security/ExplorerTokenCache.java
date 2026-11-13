package pe.foodly.messaging.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.outboundports.IExplorerTokenCache;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;

/**
 * Cache en memoria del JWT del explorador, con clave = id del mensaje (el {@code inReplyToMessageId} del
 * callback). Cada token vive lo que dure el menor entre su propia expiración y
 * {@code LIA_TIMEOUT_SECONDS} más un margen. Se pierde al reiniciar el servicio; nunca se persiste ni se
 * registra en logs.
 */
@Component
public class ExplorerTokenCache implements IExplorerTokenCache {

    private record Entry(ExplorerToken token, Instant expiresAt) {
    }

    private final ConcurrentMap<MessageId, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration maxLifetime;

    public ExplorerTokenCache(
            Clock clock,
            @Value("${messaging.lia.timeout-seconds:20}") long liaTimeoutSeconds,
            @Value("${messaging.lia.token-cache-margin-seconds:60}") long marginSeconds) {
        this.clock = clock;
        this.maxLifetime = Duration.ofSeconds(liaTimeoutSeconds + marginSeconds);
    }

    @Override
    public void put(MessageId messageId, ExplorerToken token) {
        Instant now = clock.instant();
        Instant limit = now.plus(maxLifetime);
        Instant expiresAt = token.expiresAt().isBefore(limit) ? token.expiresAt() : limit;
        purgeExpired(now);
        if (expiresAt.isAfter(now)) {
            entries.put(messageId, new Entry(token, expiresAt));
        }
    }

    @Override
    public Optional<ExplorerToken> find(MessageId messageId) {
        Entry entry = entries.get(messageId);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(messageId, entry);
            return Optional.empty();
        }
        return Optional.of(entry.token());
    }

    @Override
    public void evict(MessageId messageId) {
        entries.remove(messageId);
    }

    private void purgeExpired(Instant now) {
        entries.values().removeIf(entry -> !now.isBefore(entry.expiresAt()));
    }
}
