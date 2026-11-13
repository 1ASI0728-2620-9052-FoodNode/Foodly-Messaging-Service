package pe.foodly.messaging.infrastructure.clients;

import java.util.Comparator;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.outboundports.IRadarClient;
import pe.foodly.messaging.application.outboundports.NearbyHuarique;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;

/** Radar falso (perfil {@code stub}): los huariques de ejemplo de Lima, del más cercano al más lejano. */
@Component
@Profile("stub")
class StubRadarClient implements IRadarClient {

    private static final double EARTH_RADIUS_METERS = 6_371_008.8;

    @Override
    public List<NearbyHuarique> search(ExplorerToken token, GeoCoordinates location, int kRing) {
        return StubLimaData.HUARIQUES.stream()
                .map(entry -> new NearbyHuarique(entry.details().id(), haversine(location, entry.location())))
                .sorted(Comparator.comparingDouble(NearbyHuarique::distanceMeters))
                .toList();
    }

    private static double haversine(GeoCoordinates a, GeoCoordinates b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLng / 2), 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
