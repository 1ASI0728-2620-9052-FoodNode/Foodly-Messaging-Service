package pe.foodly.messaging.application.outboundports;

import java.util.List;
import java.util.Optional;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.exceptions.ProviderUnavailableException;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;

/** Lo que Messaging necesita de Business (5.6.3): detalle, menú, precios, horarios y estado de un huarique. */
public interface IBusinessClient {

    /**
     * @param token JWT del explorador, que se propaga a Business
     * @return el detalle, o vacío si el huarique no existe
     * @throws ProviderUnavailableException si Business no puede atender la llamada
     */
    Optional<HuariqueDetails> findHuarique(ExplorerToken token, HuariqueId id);

    /**
     * @return los platos del huarique; vacío si no tiene menú
     * @throws ProviderUnavailableException si Business no puede atender la llamada
     */
    List<MenuItem> findMenu(ExplorerToken token, HuariqueId id);
}
