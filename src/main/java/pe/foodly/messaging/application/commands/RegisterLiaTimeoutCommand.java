package pe.foodly.messaging.application.commands;

import java.util.Objects;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;

/**
 * Lía no respondió a tiempo al mensaje del explorador. Lo ejecuta {@code LiaTimeoutJob} (ver DEVIATIONS.md;
 * el informe 5.6.3 solo define dos commands).
 */
public record RegisterLiaTimeoutCommand(ConversationId conversationId, MessageId inReplyToMessageId) {

    public RegisterLiaTimeoutCommand {
        Objects.requireNonNull(conversationId, "La conversación es obligatoria.");
        Objects.requireNonNull(inReplyToMessageId, "El mensaje sin respuesta es obligatorio.");
    }
}
