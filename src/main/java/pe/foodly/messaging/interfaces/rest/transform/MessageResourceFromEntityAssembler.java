package pe.foodly.messaging.interfaces.rest.transform;

import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;
import pe.foodly.messaging.interfaces.rest.resources.MessageResource;

/** Lo usan el endpoint REST y el canal STOMP: el cliente recibe el mismo {@link MessageResource} por ambos. */
public final class MessageResourceFromEntityAssembler {

    private MessageResourceFromEntityAssembler() {
    }

    public static MessageResource toResource(Message message) {
        return new MessageResource(
                message.getId().value(),
                message.getSender().name(),
                message.getContent().text(),
                message.getIntent().map(intent -> intent.type().name()).orElse(null),
                message.getHuariqueId().map(HuariqueId::value).orElse(null),
                message.getReservationId().map(ReservationId::value).orElse(null),
                message.getSentAt());
    }
}
