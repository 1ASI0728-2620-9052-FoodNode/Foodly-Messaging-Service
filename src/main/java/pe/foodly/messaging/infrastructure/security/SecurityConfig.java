package pe.foodly.messaging.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Dos cadenas independientes (DD-06, QA-1: cada servicio valida las credenciales aunque Kong ya lo haga):
 * <ol>
 *   <li>{@code /messaging/callbacks/**}: solo el secreto compartido con el Integration System; no pasa
 *       por el filtro JWT.</li>
 *   <li>El resto: JWT de IAM. {@code /messaging/chat} y {@code /messaging/conversations/**} exigen el rol
 *       {@code GASTRONOMIC_EXPLORER}. Actuator health y Swagger son públicos, y el handshake de
 *       {@code /messaging/ws} también: el JWT se valida en el frame STOMP {@code CONNECT}.</li>
 * </ol>
 * Sin sesión ni CSRF: la API es stateless y se consume con credencial en cabecera.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({JwtProperties.class, CallbackProperties.class})
public class SecurityConfig {

    public static final String EXPLORER_ROLE = "GASTRONOMIC_EXPLORER";

    @Bean
    JwtTokenValidator jwtTokenValidator(JwtProperties properties) {
        return new JwtTokenValidator(properties);
    }

    @Bean
    @Order(1)
    SecurityFilterChain callbackSecurityFilterChain(
            HttpSecurity http, CallbackProperties callback, ObjectMapper objectMapper) throws Exception {
        stateless(http)
                .securityMatcher("/messaging/callbacks/**")
                .exceptionHandling(handling -> handling.authenticationEntryPoint(new RestAuthenticationEntryPoint(
                        objectMapper, "El secreto del callback falta o no es válido.", "X-Callback-Secret")))
                .authorizeHttpRequests(requests -> requests.anyRequest().hasRole(CallbackSecretFilter.ROLE))
                .addFilterBefore(new CallbackSecretFilter(callback.secret()), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http, JwtTokenValidator validator, ObjectMapper objectMapper) throws Exception {
        stateless(http)
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(new RestAuthenticationEntryPoint(objectMapper,
                                "Debe iniciar sesión: falta el token de acceso o no es válido.", "Bearer"))
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/messaging/ws", "/messaging/ws/**").permitAll()
                        .requestMatchers("/messaging/chat", "/messaging/conversations/**").hasRole(EXPLORER_ROLE)
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(validator), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static HttpSecurity stateless(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }
}
