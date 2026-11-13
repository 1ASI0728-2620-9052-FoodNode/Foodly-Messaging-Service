package pe.foodly.messaging.application.commandhandlers;

import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.foodly.messaging.application.commands.SendMessageCommand;
import pe.foodly.messaging.application.commands.SendMessageResult;
import pe.foodly.messaging.application.events.MessageAcceptedEvent;
import pe.foodly.messaging.application.exceptions.ConversationNotFoundException;
import pe.foodly.messaging.application.outboundports.IExplorerTokenCache;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.valueobjects.ConversationContext;
import pe.foodly.messaging.domain.repositories.IConversationRepository;

/**
 * Registra el mensaje del explorador (5.6.3): obtiene o crea la conversación activa, agrega el mensaje
 * al agregado, persiste y guarda el JWT del explorador en memoria. La solicitud a Lía no se hace aquí:
 * se publica un {@link MessageAcceptedEvent} que se atiende después del commit y de forma asíncrona.
 */
@Component
public class SendMessageCommandHandler {

    /** Mensajes previos que acompañan a la solicitud como historial. */
    static final int HISTORY_LIMIT = 20;

    private final IConversationRepository conversations;
    private final IExplorerTokenCache tokenCache;
    private final ApplicationEventPublisher events;

    public SendMessageCommandHandler(
            IConversationRepository conversations, IExplorerTokenCache tokenCache, ApplicationEventPublisher events) {
        this.conversations = conversations;
        this.tokenCache = tokenCache;
        this.events = events;
    }

    /**
     * @throws ConversationNotFoundException si se indicó una conversación que no existe o es de otro explorador
     * @throws pe.foodly.messaging.domain.exceptions.ConversationClosedException si la conversación está cerrada
     */
    @Transactional
    public SendMessageResult handle(SendMessageCommand command) {
        Conversation conversation = obtainConversation(command);
        List<Message> history = previousMessages(conversation);

        Message message = conversation.addExplorerMessage(command.content());
        conversations.save(conversation);
        tokenCache.put(message.getId(), command.token());

        events.publishEvent(new MessageAcceptedEvent(
                message.getId(),
                command.content(),
                new ConversationContext(conversation.getId(), command.location(), history)));
        return new SendMessageResult(conversation.getId(), message.getId());
    }

    private Conversation obtainConversation(SendMessageCommand command) {
        if (command.conversationId() != null) {
            // Inexistente o de otro explorador: el mismo error, sin revelar si existe.
            return conversations.findById(command.conversationId())
                    .filter(conversation -> conversation.getExplorerId().equals(command.explorerId()))
                    .orElseThrow(() -> new ConversationNotFoundException(command.conversationId()));
        }
        return conversations.findActiveByExplorerId(command.explorerId())
                .orElseGet(() -> Conversation.start(command.explorerId()));
    }

    private List<Message> previousMessages(Conversation conversation) {
        List<Message> all = conversation.getMessages();
        return List.copyOf(all.subList(Math.max(0, all.size() - HISTORY_LIMIT), all.size()));
    }
}
