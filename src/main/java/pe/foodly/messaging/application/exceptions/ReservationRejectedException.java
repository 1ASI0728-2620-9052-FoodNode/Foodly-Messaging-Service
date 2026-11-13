package pe.foodly.messaging.application.exceptions;

/**
 * Reservation respondió, pero no aceptó la solicitud (datos inválidos o sin disponibilidad).
 * Messaging no valida horario ni capacidad: esa decisión es de Reservation.
 */
public class ReservationRejectedException extends RuntimeException {

    private final String reason;

    /** @param reason motivo que informó Reservation; puede ser {@code null} */
    public ReservationRejectedException(String reason) {
        super("Reservation rechazó la solicitud.");
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
