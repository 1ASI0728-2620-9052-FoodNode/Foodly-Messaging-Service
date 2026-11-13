package pe.foodly.messaging.interfaces.rest;

/** Un valor del cuerpo que el dominio rechaza (mensaje demasiado largo, presupuesto invertido…): se responde 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
