package pe.foodly.messaging.infrastructure.persistence.jpa;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;

/** Búsqueda de mensajes del explorador que Lía no respondió (para {@code LiaTimeoutJob}). */
@Component
public class UnansweredMessageQuery {

    /** Un mensaje del explorador sin respuesta de Lía. */
    public record Unanswered(ConversationId conversationId, MessageId messageId) {
    }

    private final MessageQueryJpaRepository repository;

    UnansweredMessageQuery(MessageQueryJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * @param since solo conversaciones activas con actividad desde este instante (acota la búsqueda)
     * @param threshold solo mensajes enviados antes de este instante
     * @param limit máximo de resultados, de los más antiguos a los más recientes
     */
    @Transactional(readOnly = true)
    public List<Unanswered> find(LocalDateTime since, LocalDateTime threshold, int limit) {
        return repository.findUnanswered(since, threshold, limit).stream()
                .map(row -> new Unanswered(ConversationId.of(row.getConversationId()), MessageId.of(row.getId())))
                .toList();
    }
}
