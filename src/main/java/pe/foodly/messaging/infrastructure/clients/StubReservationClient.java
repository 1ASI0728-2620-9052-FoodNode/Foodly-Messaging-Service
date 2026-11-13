package pe.foodly.messaging.infrastructure.clients;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.exceptions.ReservationRejectedException;
import pe.foodly.messaging.application.outboundports.IReservationClient;
import pe.foodly.messaging.application.outboundports.ReservationConfirmation;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;

/**
 * Reservation falso (perfil {@code stub}): acepta toda solicitud de un huarique de ejemplo y la deja
 * {@code PENDING}. Rechaza un huarique que no conoce, como haría Reservation.
 */
@Component
@Profile("stub")
class StubReservationClient implements IReservationClient {

    private final Map<ReservationId, ReservationConfirmation> reservations = new ConcurrentHashMap<>();

    @Override
    public ReservationConfirmation request(
            ExplorerToken token, HuariqueId huariqueId, LocalDate date, LocalTime time, int partySize) {
        if (StubLimaData.find(huariqueId).isEmpty()) {
            throw new ReservationRejectedException("el huarique no existe");
        }
        ReservationConfirmation confirmation = new ReservationConfirmation(new ReservationId(UUID.randomUUID()), "PENDING");
        reservations.put(confirmation.reservationId(), confirmation);
        return confirmation;
    }

    @Override
    public Optional<ReservationConfirmation> findStatus(ExplorerToken token, ReservationId reservationId) {
        return Optional.ofNullable(reservations.get(reservationId));
    }
}
