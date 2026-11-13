package pe.foodly.messaging.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import pe.foodly.messaging.interfaces.rest.resources.ErrorResponse;

/** 401 con el formato de error uniforme cuando falta la credencial o no es válida. */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;
    private final String message;
    private final String challenge;

    /**
     * @param message texto para el usuario, en español
     * @param challenge valor del header {@code WWW-Authenticate}
     */
    public RestAuthenticationEntryPoint(ObjectMapper objectMapper, String message, String challenge) {
        this.objectMapper = objectMapper;
        this.message = message;
        this.challenge = challenge;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("WWW-Authenticate", challenge);
        objectMapper.writeValue(response.getOutputStream(),
                ErrorResponse.of(HttpStatus.UNAUTHORIZED, message, request.getRequestURI()));
    }
}
