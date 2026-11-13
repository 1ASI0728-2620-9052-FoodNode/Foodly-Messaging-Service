package pe.foodly.messaging.application.commands;

import java.util.List;
import java.util.Objects;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;

/**
 * Respuesta de Lía que llega por el callback (5.6.3).
 *
 * @param inReplyToMessageId mensaje del explorador al que responde Lía; obligatorio (ver DEVIATIONS.md)
 * @param replyContent texto redactado por n8n; puede estar vacío (la respuesta se arma con los datos obtenidos)
 * @param intent intención interpretada; {@code null} equivale a una intención desconocida
 * @param suggestedHuariques huariques que sugiere Lía; no se usan: mandan los datos de Radar y Business
 * @param requestedReservation reserva solicitada; opcional
 */
public record RegisterLiaReplyCommand(
        ConversationId conversationId,
        MessageId inReplyToMessageId,
        String replyContent,
        UserIntent intent,
        List<HuariqueId> suggestedHuariques,
        RequestedReservation requestedReservation) {

    public RegisterLiaReplyCommand {
        Objects.requireNonNull(conversationId, "La conversación es obligatoria.");
        Objects.requireNonNull(inReplyToMessageId, "El mensaje al que responde Lía es obligatorio.");
        suggestedHuariques = suggestedHuariques == null ? List.of() : List.copyOf(suggestedHuariques);
    }
}
