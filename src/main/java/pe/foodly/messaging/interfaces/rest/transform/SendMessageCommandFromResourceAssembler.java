package pe.foodly.messaging.interfaces.rest.transform;

import pe.foodly.messaging.application.MessagingSettings;
import pe.foodly.messaging.application.commands.SendMessageCommand;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.infrastructure.security.AuthenticatedExplorer;
import pe.foodly.messaging.interfaces.rest.InvalidRequestException;
import pe.foodly.messaging.interfaces.rest.resources.SendMessageResource;

public final class SendMessageCommandFromResourceAssembler {

    private SendMessageCommandFromResourceAssembler() {
    }

    /**
     * @throws InvalidRequestException si el texto excede {@code MESSAGING_MAX_LENGTH} o las coordenadas no son válidas
     */
    public static SendMessageCommand toCommand(
            SendMessageResource resource, AuthenticatedExplorer explorer, MessagingSettings settings) {
        try {
            return new SendMessageCommand(
                    explorer.id(),
                    resource.conversationId() == null ? null : new ConversationId(resource.conversationId()),
                    MessageContent.of(resource.content(), settings.maxMessageLength()),
                    resource.latitude() == null ? null : new GeoCoordinates(resource.latitude(), resource.longitude()),
                    explorer.token());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(e.getMessage(), e);
        }
    }
}
