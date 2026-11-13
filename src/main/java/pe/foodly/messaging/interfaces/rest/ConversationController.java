package pe.foodly.messaging.interfaces.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.foodly.messaging.application.queries.GetConversationsByExplorerQuery;
import pe.foodly.messaging.application.queries.GetMessagesByConversationQuery;
import pe.foodly.messaging.application.queryhandlers.GetConversationsByExplorerQueryHandler;
import pe.foodly.messaging.application.queryhandlers.GetMessagesByConversationQueryHandler;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.infrastructure.security.AuthenticatedExplorer;
import pe.foodly.messaging.interfaces.rest.resources.ConversationResource;
import pe.foodly.messaging.interfaces.rest.resources.ErrorResponse;
import pe.foodly.messaging.interfaces.rest.resources.MessageResource;
import pe.foodly.messaging.interfaces.rest.transform.ConversationResourceFromEntityAssembler;
import pe.foodly.messaging.interfaces.rest.transform.MessageResourceFromEntityAssembler;

@RestController
@RequestMapping(value = "/messaging/conversations", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Conversaciones", description = "Historial del explorador con Lía. Es la fuente de verdad: "
        + "el canal STOMP solo avisa de las respuestas nuevas.")
@SecurityRequirement(name = "bearerAuth")
public class ConversationController {

    private final GetConversationsByExplorerQueryHandler conversationsHandler;
    private final GetMessagesByConversationQueryHandler messagesHandler;

    public ConversationController(
            GetConversationsByExplorerQueryHandler conversationsHandler,
            GetMessagesByConversationQueryHandler messagesHandler) {
        this.conversationsHandler = conversationsHandler;
        this.messagesHandler = messagesHandler;
    }

    @GetMapping("/me")
    @Operation(
            summary = "Listar mis conversaciones",
            description = "Devuelve las conversaciones del explorador autenticado, la de actividad más reciente "
                    + "primero. Requiere el rol GASTRONOMIC_EXPLORER.")
    @ApiResponse(responseCode = "200", description = "Conversaciones del explorador (posiblemente una lista vacía).",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ConversationResource.class))))
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es GASTRONOMIC_EXPLORER.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<ConversationResource> myConversations(@AuthenticationPrincipal AuthenticatedExplorer explorer) {
        return conversationsHandler.handle(new GetConversationsByExplorerQuery(explorer.id())).stream()
                .map(ConversationResourceFromEntityAssembler::toResource)
                .toList();
    }

    @GetMapping("/{conversationId}/messages")
    @Operation(
            summary = "Listar los mensajes de una conversación",
            description = "Devuelve los mensajes en orden cronológico, incluidas las respuestas de Lía. La "
                    + "conversación debe pertenecer al explorador autenticado. Requiere el rol GASTRONOMIC_EXPLORER.")
    @ApiResponse(responseCode = "200", description = "Mensajes de la conversación.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MessageResource.class))))
    @ApiResponse(responseCode = "400", description = "El identificador de la conversación no es válido.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "El rol no es GASTRONOMIC_EXPLORER.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La conversación no existe o no pertenece al explorador.",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<MessageResource> messages(
            @PathVariable UUID conversationId, @AuthenticationPrincipal AuthenticatedExplorer explorer) {
        return messagesHandler
                .handle(new GetMessagesByConversationQuery(new ConversationId(conversationId), explorer.id()))
                .stream()
                .map(MessageResourceFromEntityAssembler::toResource)
                .toList();
    }
}
