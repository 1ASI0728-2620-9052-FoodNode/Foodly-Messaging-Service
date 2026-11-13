package pe.foodly.messaging.application.outboundports;

import java.util.Optional;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;

/**
 * Guarda el JWT del explorador mientras Lía responde, porque el callback no trae el token del usuario.
 * Clave: id del mensaje del explorador (el {@code inReplyToMessageId} del callback). El token vive solo
 * en memoria, nunca se persiste ni se registra en logs.
 */
public interface IExplorerTokenCache {

    /** Guarda el token hasta que venza el token o el tiempo de espera de Lía, lo que ocurra primero. */
    void put(MessageId messageId, ExplorerToken token);

    /** El token, o vacío si venció, se retiró o el servicio se reinició. */
    Optional<ExplorerToken> find(MessageId messageId);

    void evict(MessageId messageId);
}
