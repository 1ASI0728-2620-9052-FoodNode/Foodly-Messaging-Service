package pe.foodly.messaging.domain.exceptions;

import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;

/** El mensaje ya está vinculado a otra reserva. */
public class ReservationAlreadyLinkedException extends DomainException {

    public ReservationAlreadyLinkedException(MessageId messageId, ReservationId existing) {
        super("El mensaje " + messageId + " ya está vinculado a la reserva " + existing + ".");
    }
}
