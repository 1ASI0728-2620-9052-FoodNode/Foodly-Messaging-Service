package pe.foodly.messaging.interfaces.rest;

import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pe.foodly.messaging.application.exceptions.ConversationNotFoundException;
import pe.foodly.messaging.domain.exceptions.ConversationClosedException;
import pe.foodly.messaging.domain.exceptions.MessageNotFoundException;
import pe.foodly.messaging.interfaces.rest.resources.ErrorResponse;

/**
 * Convierte todos los errores en el formato uniforme { status, error, message, path, timestamp } con los
 * códigos de 5.6.2: 400 (datos inválidos), 404 (la conversación no existe o es de otro explorador) y, además,
 * 409 (conversación cerrada). Los 401 y 403 los emite la cadena de seguridad con el mismo formato.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    static final String BAD_REQUEST_PREFIX = "No se pudo procesar la solicitud: ";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Object> handleInvalidRequest(InvalidRequestException ex, WebRequest request) {
        return error(HttpStatus.BAD_REQUEST, BAD_REQUEST_PREFIX + ex.getMessage(), request);
    }

    @ExceptionHandler({ConversationNotFoundException.class, MessageNotFoundException.class})
    public ResponseEntity<Object> handleNotFound(RuntimeException ex, WebRequest request) {
        return error(HttpStatus.NOT_FOUND, "La conversación no existe o no te pertenece.", request);
    }

    @ExceptionHandler(ConversationClosedException.class)
    public ResponseEntity<Object> handleClosed(ConversationClosedException ex, WebRequest request) {
        return error(HttpStatus.CONFLICT, "La conversación está cerrada y no acepta mensajes nuevos. "
                + "Inicia una conversación nueva.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream().map(e -> e.getDefaultMessage()),
                        ex.getBindingResult().getGlobalErrors().stream().map(e -> e.getDefaultMessage()))
                .distinct()
                .sorted()
                .reduce((a, b) -> a + " " + b)
                .orElse("los datos enviados no son válidos.");
        return error(HttpStatus.BAD_REQUEST, BAD_REQUEST_PREFIX + detail, request);
    }

    /** No se devuelve el mensaje de Jackson para no filtrar detalles internos. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return error(HttpStatus.BAD_REQUEST, BAD_REQUEST_PREFIX
                + "el cuerpo no es un JSON válido o tiene valores con formato incorrecto.", request);
    }

    /** El resto de errores de Spring MVC (404, 405, 415...) también salen con el formato uniforme. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        return error(status, messageFor(status), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) throws Exception {
        // La seguridad tiene sus propios manejadores (401 y 403): no se convierten en 500.
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        log.error("Error inesperado en {}", pathOf(request), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, messageFor(HttpStatus.INTERNAL_SERVER_ERROR), request);
    }

    private static String messageFor(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "No se encontró el recurso solicitado.";
            case METHOD_NOT_ALLOWED -> "El método HTTP no está permitido para este recurso.";
            case NOT_ACCEPTABLE -> "El formato de respuesta solicitado no está disponible.";
            case UNSUPPORTED_MEDIA_TYPE -> "El tipo de contenido no es compatible. Use application/json.";
            case BAD_REQUEST -> BAD_REQUEST_PREFIX + "la solicitud no es válida.";
            case INTERNAL_SERVER_ERROR -> "Ocurrió un error interno. Intente nuevamente más tarde.";
            default -> "No se pudo atender la solicitud.";
        };
    }

    private static ResponseEntity<Object> error(HttpStatus status, String message, WebRequest request) {
        return ResponseEntity.status(status).body(ErrorResponse.of(status, message, pathOf(request)));
    }

    private static String pathOf(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest ? servletRequest.getRequest().getRequestURI() : "";
    }
}
