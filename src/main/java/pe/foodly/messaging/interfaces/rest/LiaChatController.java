package pe.foodly.messaging.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.foodly.messaging.application.MessagingSettings;
import pe.foodly.messaging.application.commandhandlers.SendMessageCommandHandler;
import pe.foodly.messaging.infrastructure.security.AuthenticatedExplorer;
import pe.foodly.messaging.interfaces.rest.resources.ErrorResponse;
import pe.foodly.messaging.interfaces.rest.resources.SendMessageAcceptedResource;
import pe.foodly.messaging.interfaces.rest.resources.SendMessageResource;
import pe.foodly.messaging.interfaces.rest.transform.SendMessageAcceptedResourceFromResultAssembler;
import pe.foodly.messaging.interfaces.rest.transform.SendMessageCommandFromResourceAssembler;

@RestController
@RequestMapping(value = "/messaging", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Chat con Lía", description = "El explorador le escribe a Lía, la asistente conversacional de Foodly.")
public class LiaChatController {

    private final SendMessageCommandHandler sendMessageHandler;
    private final MessagingSettings settings;

    public LiaChatController(SendMessageCommandHandler sendMessageHandler, MessagingSettings settings) {
        this.sendMessageHandler = sendMessageHandler;
        this.settings = settings;
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Enviar un mensaje a Lía",
            description = "Registra el mensaje del explorador (inicia una conversación si no tiene una activa) y "
                    + "solicita su interpretación. Responde 202 porque la respuesta de Lía llega de forma "
                    + "asíncrona: se publica en el canal STOMP /topic/conversations/{conversationId} (endpoint "
                    + "/messaging/ws) y queda disponible en GET /messaging/conversations/{conversationId}/messages. "
                    + "Requiere el rol GASTRONOMIC_EXPLORER.")
    @ApiResponse(responseCode = "202", description = "Mensaje aceptado; Lía está procesándolo.",
            content = @Content(schema = @Schema(implementation = SendMessageAcceptedResource.class),
                    examples = @ExampleObject(value = """
                            { "conversationId": "5b0d3e8a-6f43-4a0e-9d38-2d7b1c4f9a10",
                              "messageId": "0f6b8c52-1d3a-4c7e-8a55-9b1e2f3a4d60", "status": "PROCESSING" }""")))
    @ApiResponse(responseCode = "400", description = "Mensaje vacío o demasiado largo, o coordenadas inválidas.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es GASTRONOMIC_EXPLORER.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La conversación no existe o no pertenece al explorador.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "La conversación indicada está cerrada.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<SendMessageAcceptedResource> sendMessage(
            @Valid @RequestBody SendMessageResource resource, @AuthenticationPrincipal AuthenticatedExplorer explorer) {
        var result = sendMessageHandler.handle(
                SendMessageCommandFromResourceAssembler.toCommand(resource, explorer, settings));
        return ResponseEntity.accepted().body(SendMessageAcceptedResourceFromResultAssembler.toResource(result));
    }
}
