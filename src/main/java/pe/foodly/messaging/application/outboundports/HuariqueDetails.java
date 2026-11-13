package pe.foodly.messaging.application.outboundports;

import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;

/**
 * Detalle de un huarique que informa Business. Solo {@code id} y {@code name} son obligatorios; el resto
 * puede ser {@code null}. Los campos son los que usa Lía para responder (ver DEVIATIONS.md).
 */
public record HuariqueDetails(
        HuariqueId id, String name, String description, String address, String openingHours, Boolean open) {
}
