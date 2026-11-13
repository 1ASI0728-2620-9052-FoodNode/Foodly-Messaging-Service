package pe.foodly.messaging.application.queryhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.foodly.messaging.application.exceptions.ConversationNotFoundException;
import pe.foodly.messaging.application.queries.GetConversationsByExplorerQuery;
import pe.foodly.messaging.application.queries.GetMessagesByConversationQuery;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.repositories.IConversationRepository;

@ExtendWith(MockitoExtension.class)
class QueryHandlersTest {

    private static final ExplorerId EXPLORER = ExplorerId.of("11111111-1111-1111-1111-111111111111");
    private static final ExplorerId OTHER = ExplorerId.of("99999999-9999-9999-9999-999999999999");

    @Mock
    private IConversationRepository conversations;

    @Test
    void getConversationsDevuelveLasDelExplorador() {
        List<Conversation> mine = List.of(Conversation.start(EXPLORER), Conversation.start(EXPLORER));
        when(conversations.findAllByExplorerId(EXPLORER)).thenReturn(mine);

        List<Conversation> result = new GetConversationsByExplorerQueryHandler(conversations)
                .handle(new GetConversationsByExplorerQuery(EXPLORER));

        assertThat(result).isEqualTo(mine);
    }

    @Test
    void getMessagesDevuelveLosMensajesDeUnaConversacionPropia() {
        Conversation conversation = Conversation.start(EXPLORER);
        Message first = conversation.addExplorerMessage(MessageContent.of("hola"));
        Message second = conversation.addLiaReply(MessageContent.of("¡hola!"), null);
        when(conversations.findById(conversation.getId())).thenReturn(Optional.of(conversation));

        List<Message> result = new GetMessagesByConversationQueryHandler(conversations)
                .handle(new GetMessagesByConversationQuery(conversation.getId(), EXPLORER));

        assertThat(result).containsExactly(first, second);
    }

    @Test
    void getMessagesDaNotFoundSiLaConversacionNoExiste() {
        ConversationId unknown = ConversationId.newId();
        when(conversations.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new GetMessagesByConversationQueryHandler(conversations)
                        .handle(new GetMessagesByConversationQuery(unknown, EXPLORER)))
                .isInstanceOf(ConversationNotFoundException.class);
    }

    @Test
    void getMessagesDaElMismoNotFoundSiLaConversacionEsDeOtroExplorador() {
        Conversation foreign = Conversation.start(OTHER);
        foreign.addExplorerMessage(MessageContent.of("secreto"));
        when(conversations.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> new GetMessagesByConversationQueryHandler(conversations)
                        .handle(new GetMessagesByConversationQuery(foreign.getId(), EXPLORER)))
                .isInstanceOf(ConversationNotFoundException.class);
    }
}
