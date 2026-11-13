package pe.foodly.messaging.application.outboundports;

import pe.foodly.messaging.domain.model.valueobjects.ReservationId;

/** Reserva creada (o consultada) en Reservation, con su estado tal como lo informa ese contexto. */
public record ReservationConfirmation(ReservationId reservationId, String status) {
}
