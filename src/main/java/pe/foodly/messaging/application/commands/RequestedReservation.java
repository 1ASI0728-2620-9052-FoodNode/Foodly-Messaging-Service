package pe.foodly.messaging.application.commands;

import java.time.LocalDateTime;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;

/**
 * Reserva que Lía interpretó del mensaje (campo {@code requestedReservation} del callback). Sirve para
 * completar los datos que la intención no trae; si la intención ya los trae, ella manda. Cualquier
 * campo puede ser {@code null}.
 */
public record RequestedReservation(HuariqueId huariqueId, LocalDateTime requestedAt, Integer partySize) {
}
