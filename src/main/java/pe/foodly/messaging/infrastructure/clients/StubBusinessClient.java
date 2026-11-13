package pe.foodly.messaging.infrastructure.clients;

import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.application.outboundports.HuariqueDetails;
import pe.foodly.messaging.application.outboundports.IBusinessClient;
import pe.foodly.messaging.application.outboundports.MenuItem;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;

/** Business falso (perfil {@code stub}): detalle y menú de los huariques de ejemplo de Lima. */
@Component
@Profile("stub")
class StubBusinessClient implements IBusinessClient {

    @Override
    public Optional<HuariqueDetails> findHuarique(ExplorerToken token, HuariqueId id) {
        return StubLimaData.find(id).map(StubLimaData.Entry::details);
    }

    @Override
    public List<MenuItem> findMenu(ExplorerToken token, HuariqueId id) {
        return StubLimaData.find(id).map(StubLimaData.Entry::menu).orElse(List.of());
    }
}
