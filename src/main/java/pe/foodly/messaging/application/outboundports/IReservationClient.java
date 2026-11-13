package pe.foodly.messaging.application.outboundports;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.exceptions.ProviderUnavailableException;
import pe.foodly.messaging.application.exceptions.ReservationRejectedException;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;

/** Lo que Messaging necesita de Reservation (5.6.3): solicitar una reserva y consultar su estado. */
public interface IReservationClient {

    /**
     * Solicita la reserva en nombre del explorador (Reservation lo toma del JWT).
     *
     * @throws ReservationRejectedException si Reservation no acepta la solicitud
     * @throws ProviderUnavailableException si Reservation no puede atender la llamada
     */
    ReservationConfirmation request(
            ExplorerToken token, HuariqueId huariqueId, LocalDate date, LocalTime time, int partySize);

    /**
     * @return la reserva con su estado actual, o vacío si no existe
     * @throws ProviderUnavailableException si Reservation no puede atender la llamada
     */
    Optional<ReservationConfirmation> findStatus(ExplorerToken token, ReservationId reservationId);
}
