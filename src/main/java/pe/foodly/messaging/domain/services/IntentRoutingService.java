package pe.foodly.messaging.domain.services;

import java.util.Objects;
import pe.foodly.messaging.domain.model.enums.RoutingDecision;
import pe.foodly.messaging.domain.model.valueobjects.UserIntent;

/**
 * Decide a qué contexto se delega una {@link UserIntent} (5.6.1). Concentra la regla de ruteo.
 *
 * <ul>
 *   <li>RECOMMENDATION_REQUEST con ubicación → RECOMMEND_VIA_RADAR.</li>
 *   <li>HUARIQUE_INQUIRY con huarique → QUERY_BUSINESS.</li>
 *   <li>RESERVATION_REQUEST completa (huarique, fecha y personas) → FORWARD_TO_RESERVATION.
 *       Messaging no valida horario ni capacidad.</li>
 *   <li>Cualquier otro caso → ASK_CLARIFICATION; qué pedir lo dice {@link UserIntent#missingContext()}.</li>
 * </ul>
 */
public class IntentRoutingService {

    public RoutingDecision route(UserIntent intent) {
        Objects.requireNonNull(intent, "La intención es obligatoria.");
        if (!intent.isComplete()) {
            return RoutingDecision.ASK_CLARIFICATION;
        }
        return switch (intent.type()) {
            case RECOMMENDATION_REQUEST -> RoutingDecision.RECOMMEND_VIA_RADAR;
            case HUARIQUE_INQUIRY -> RoutingDecision.QUERY_BUSINESS;
            case RESERVATION_REQUEST -> RoutingDecision.FORWARD_TO_RESERVATION;
            case UNKNOWN -> RoutingDecision.ASK_CLARIFICATION;
        };
    }
}
