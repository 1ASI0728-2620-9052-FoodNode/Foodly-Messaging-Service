package pe.foodly.messaging.infrastructure.persistence.jpa;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Consultas nativas de solo lectura sobre {@code messages}, aparte de {@link ConversationJpaRepository} para
 * no ampliar {@code IConversationRepository}. Las usa {@code LiaTimeoutJob} a través de
 * {@link UnansweredMessageQuery}.
 */
interface MessageQueryJpaRepository extends Repository<MessageEntity, String> {

    /** Fila de resultado: el mensaje sin respuesta y su conversación. */
    interface UnansweredRow {
        String getId();

        String getConversationId();
    }

    /**
     * Mensajes del explorador, de conversaciones activas con actividad reciente, enviados antes del umbral y
     * sin una respuesta de Lía cuyo {@code intent_data.inReplyToMessageId} sea su id. Los más antiguos primero.
     * No requiere un índice nuevo: recorre las conversaciones activas recientes y luego usa el índice
     * {@code (conversation_id, sent_at)} de {@code messages}.
     */
    @Query(nativeQuery = true, value = """
            SELECT m.id AS id, m.conversation_id AS conversationId
            FROM messages m
            JOIN conversations c ON c.id = m.conversation_id
            WHERE c.status = 'ACTIVE'
              AND c.last_message_at >= :since
              AND m.sender = 'EXPLORER'
              AND m.sent_at < :threshold
              AND NOT EXISTS (
                    SELECT 1 FROM messages r
                    WHERE r.conversation_id = m.conversation_id
                      AND r.sender = 'LIA'
                      AND JSON_UNQUOTE(JSON_EXTRACT(r.intent_data, '$.inReplyToMessageId')) = m.id)
            ORDER BY m.sent_at
            LIMIT :limit
            """)
    List<UnansweredRow> findUnanswered(
            @Param("since") LocalDateTime since,
            @Param("threshold") LocalDateTime threshold,
            @Param("limit") int limit);
}
