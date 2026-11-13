package pe.foodly.messaging.infrastructure.security;

import java.security.Principal;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pe.foodly.messaging.application.ExplorerToken;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;

/**
 * Usuario autenticado con un JWT válido: su id, el token crudo (que {@code SendMessageCommandHandler} guarda
 * en el {@code ExplorerTokenCache} para propagarlo a otros contextos) y sus roles, en mayúsculas y con el
 * prefijo {@code ROLE_}. Es el principal de la petición HTTP y de la sesión STOMP.
 */
public record AuthenticatedExplorer(ExplorerId id, ExplorerToken token, Set<String> roles) implements Principal {

    public AuthenticatedExplorer {
        roles = Set.copyOf(roles);
    }

    /** El nombre del usuario es el id del explorador (así se identifica en el registro de usuarios STOMP). */
    @Override
    public String getName() {
        return id.toString();
    }

    public boolean hasRole(String role) {
        return roles.contains("ROLE_" + role);
    }

    public List<GrantedAuthority> authorities() {
        return roles.stream().<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
    }
}
