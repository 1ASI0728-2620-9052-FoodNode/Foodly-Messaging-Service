package pe.foodly.messaging.application.commands;

import java.util.Objects;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;

/**
 * Mensaje del explorador a Lía (5.6.3).
 *
 * @param conversationId conversación a la que escribe; {@code null} para usar (o iniciar) la activa
 * @param content texto ya validado (no vacío, dentro del máximo)
 * @param location ubicación que comparte el explorador; opcional
 * @param token JWT del explorador, que se guarda para propagarlo cuando Lía responda
 */
public record SendMessageCommand(
        ExplorerId explorerId,
        ConversationId conversationId,
        MessageContent content,
        GeoCoordinates location,
        ExplorerToken token) {

    public SendMessageCommand {
        Objects.requireNonNull(explorerId, "El explorador es obligatorio.");
        Objects.requireNonNull(content, "El contenido es obligatorio.");
        Objects.requireNonNull(token, "El token del explorador es obligatorio.");
    }
}
