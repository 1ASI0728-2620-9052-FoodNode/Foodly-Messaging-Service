package pe.foodly.messaging.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Valida el JWT de IAM en los endpoints de usuario (5.6.4) y deja al explorador autenticado: su
 * {@link AuthenticatedExplorer} (con el token crudo para el {@code ExplorerTokenCache}) es el principal de
 * la petición. Un token ausente o inválido no se rechaza aquí: la petición sigue sin autenticar y la
 * configuración de rutas responde 401 en las protegidas y 403 si el rol no es el de explorador.
 *
 * <p>No es un bean: lo crea {@link SecurityConfig} para que solo corra dentro de la cadena de seguridad.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenValidator validator;

    public JwtAuthenticationFilter(JwtTokenValidator validator) {
        this.validator = validator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            validator.validate(header.substring(BEARER_PREFIX.length()).trim()).ifPresent(explorer -> {
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(explorer, null, explorer.authorities()));
                SecurityContextHolder.setContext(context);
            });
        }
        chain.doFilter(request, response);
    }
}
