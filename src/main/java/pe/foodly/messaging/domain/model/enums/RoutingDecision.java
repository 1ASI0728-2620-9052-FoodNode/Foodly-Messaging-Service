package pe.foodly.messaging.domain.model.enums;

/** Contexto al que se delega una intención (5.6.1, {@code IntentRoutingService}). */
public enum RoutingDecision {
    /** Recomendación: se consulta a Radar con la ubicación del explorador. */
    RECOMMEND_VIA_RADAR,
    /** Consulta de menú, precios u horario: se consulta a Business. */
    QUERY_BUSINESS,
    /** Solicitud de reserva completa: se reenvía a Reservation. */
    FORWARD_TO_RESERVATION,
    /** Falta contexto: Lía pide la aclaración correspondiente. */
    ASK_CLARIFICATION
}
