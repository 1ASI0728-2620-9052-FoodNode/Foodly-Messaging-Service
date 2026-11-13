package pe.foodly.messaging.infrastructure.lia;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Cuerpo de la solicitud a n8n (sección 3.5 de la spec). Es el único lugar que conoce ese formato: el
 * dominio y la aplicación no lo ven. {@code callbackUrl} y {@code callbackSecret} solo van en el modo
 * {@code direct-n8n}; con el Integration System se omiten (él conoce el callback).
 */
record N8nLiaRequest(
        String conversationId,
        String messageId,
        String content,
        Location location,
        List<HistoryEntry> history,
        @JsonInclude(JsonInclude.Include.NON_NULL) String callbackUrl,
        @JsonInclude(JsonInclude.Include.NON_NULL) String callbackSecret) {

    record Location(double latitude, double longitude) {
    }

    record HistoryEntry(String sender, String content) {
    }

    /** No incluye el secreto del callback ni el contenido, por si alguien registra el objeto por error. */
    @Override
    public String toString() {
        return "N8nLiaRequest[conversationId=" + conversationId + ", messageId=" + messageId + "]";
    }
}
