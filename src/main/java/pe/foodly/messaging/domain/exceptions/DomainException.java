package pe.foodly.messaging.domain.exceptions;

/** Base de las violaciones de reglas del agregado {@code Conversation}. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
