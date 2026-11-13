package pe.foodly.messaging.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.foodly.messaging.application.commandhandlers.RegisterLiaReplyCommandHandler;
import pe.foodly.messaging.application.commands.RegisterLiaReplyCommand;
import pe.foodly.messaging.application.exceptions.ConversationNotFoundException;
import pe.foodly.messaging.domain.exceptions.ConversationClosedException;
import pe.foodly.messaging.domain.exceptions.MessageNotFoundException;
import pe.foodly.messaging.interfaces.rest.resources.CallbackAckResource;
import pe.foodly.messaging.interfaces.rest.resources.ErrorResponse;
import pe.foodly.messaging.interfaces.rest.resources.LiaCallbackResource;
import pe.foodly.messaging.interfaces.rest.transform.RegisterLiaReplyCommandFromResourceAssembler;

/**
 * Recibe la respuesta de Lía. Lo invoca el Integration System (no n8n directamente) y no usa el JWT de un
 * usuario: lo protege el secreto compartido de {@code CallbackSecretFilter}.
 */
@RestController
@RequestMapping(value = "/messaging/callbacks", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Callback de Lía", description = "Uso interno: lo invoca el Integration System, no los clientes.")
public class N8nCallbackController {

    private static final Logger log = LoggerFactory.getLogger(N8nCallbackController.class);

    private final RegisterLiaReplyCommandHandler registerLiaReplyHandler;
    private final Executor callbackExecutor;

    public N8nCallbackController(
            RegisterLiaReplyCommandHandler registerLiaReplyHandler,
            @Qualifier("liaCallbackExecutor") Executor callbackExecutor) {
        this.registerLiaReplyHandler = registerLiaReplyHandler;
        this.callbackExecutor = callbackExecutor;
    }

    @PostMapping(value = "/n8n", consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirement(name = "callbackSecret")
    @Operation(
            summary = "Registrar la respuesta de Lía",
            description = "Recibe la respuesta que generó n8n, reenviada por el Integration System. Se protege "
                    + "con el secreto compartido del header X-Callback-Secret (no usa JWT). Responde 202 de "
                    + "inmediato y el ruteo (consultas a Radar, Business y Reservation) se procesa de forma "
                    + "asíncrona. Es idempotente por inReplyToMessageId: una respuesta repetida o tardía se ignora.")
    @ApiResponse(responseCode = "202", description = "Respuesta recibida; se procesa de forma asíncrona.",
            content = @Content(schema = @Schema(implementation = CallbackAckResource.class),
                    examples = @ExampleObject(value = "{ \"status\": \"RECEIVED\" }")))
    @ApiResponse(responseCode = "400", description = "El cuerpo no es válido.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Falta el secreto o es incorrecto.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CallbackAckResource> receive(@Valid @RequestBody LiaCallbackResource resource) {
        RegisterLiaReplyCommand command = RegisterLiaReplyCommandFromResourceAssembler.toCommand(resource);
        callbackExecutor.execute(() -> process(command));
        return ResponseEntity.accepted().body(new CallbackAckResource(CallbackAckResource.RECEIVED));
    }

    /** Ya se respondió 202: lo que falle aquí solo puede registrarse. */
    private void process(RegisterLiaReplyCommand command) {
        try {
            registerLiaReplyHandler.handle(command);
        } catch (ConversationNotFoundException | MessageNotFoundException | ConversationClosedException e) {
            log.warn("No se registró la respuesta de Lía al mensaje {}: {}", command.inReplyToMessageId(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Error al registrar la respuesta de Lía al mensaje {}.", command.inReplyToMessageId(), e);
        }
    }
}
