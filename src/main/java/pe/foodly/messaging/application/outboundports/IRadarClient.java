package pe.foodly.messaging.application.outboundports;

import java.util.List;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.exceptions.ProviderUnavailableException;
import pe.foodly.messaging.domain.model.valueobjects.GeoCoordinates;

/** Lo que Messaging necesita de Radar (5.6.3): huariques cercanos por proximidad H3. */
public interface IRadarClient {

    /**
     * @param token JWT del explorador, que se propaga a Radar
     * @param kRing k-ring de la búsqueda ({@code MESSAGING_RADAR_K_RING})
     * @return huariques activos de la zona, del más cercano al más lejano; vacío si no hay ninguno
     * @throws ProviderUnavailableException si Radar no puede atender la llamada
     */
    List<NearbyHuarique> search(ExplorerToken token, GeoCoordinates location, int kRing);
}
