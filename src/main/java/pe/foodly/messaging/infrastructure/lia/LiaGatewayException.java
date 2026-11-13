package pe.foodly.messaging.infrastructure.lia;

/**
 * No se pudo entregar la solicitud a Lía. El mensaje nunca incluye la URL (el webhook de n8n puede ser un
 * secreto) ni el contenido de la solicitud, y no se encadena la causa por la misma razón.
 */
public class LiaGatewayException extends RuntimeException {

    public LiaGatewayException(String message) {
        super(message);
    }
}
