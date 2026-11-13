package pe.foodly.messaging.interfaces.rest.transform;

import pe.foodly.messaging.application.commands.SendMessageResult;
import pe.foodly.messaging.interfaces.rest.resources.SendMessageAcceptedResource;

public final class SendMessageAcceptedResourceFromResultAssembler {

    private SendMessageAcceptedResourceFromResultAssembler() {
    }

    public static SendMessageAcceptedResource toResource(SendMessageResult result) {
        return new SendMessageAcceptedResource(
                result.conversationId().value(), result.messageId().value(), SendMessageAcceptedResource.PROCESSING);
    }
}
