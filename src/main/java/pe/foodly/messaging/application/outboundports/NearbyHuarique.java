package pe.foodly.messaging.application.outboundports;

import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;

/** Un huarique de la respuesta de Radar: su id y la distancia en metros al explorador. */
public record NearbyHuarique(HuariqueId huariqueId, double distanceMeters) {
}
