package pe.foodly.messaging.infrastructure.persistence.jpa;

import java.util.List;
import org.springframework.stereotype.Component;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.entities.Message;
import pe.foodly.messaging.domain.model.enums.ConversationStatus;
import pe.foodly.messaging.domain.model.enums.SenderType;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.model.valueobjects.HuariqueId;
import pe.foodly.messaging.domain.model.valueobjects.MessageContent;
import pe.foodly.messaging.domain.model.valueobjects.MessageId;
import pe.foodly.messaging.domain.model.valueobjects.ReservationId;

/** Traduce entre el agregado {@link Conversation} y sus entidades JPA, en ambos sentidos. */
@Component
class ConversationMapper {

    ConversationEntity toEntity(Conversation conversation) {
        ConversationEntity entity = new ConversationEntity(
                conversation.getId().toString(),
                conversation.getExplorerId().toString(),
                conversation.getStatus().name(),
                conversation.getStartedAt(),
                conversation.getLastMessageAt());
        conversation.getMessages().forEach(message -> entity.addMessage(toEntity(message)));
        return entity;
    }

    Conversation toDomain(ConversationEntity entity) {
        List<Message> messages = entity.getMessages().stream().map(this::toDomain).toList();
        return Conversation.restore(
                ConversationId.of(entity.getId()),
                ExplorerId.of(entity.getExplorerId()),
                ConversationStatus.valueOf(entity.getStatus()),
                entity.getStartedAt(),
                entity.getLastMessageAt(),
                messages);
    }

    private MessageEntity toEntity(Message message) {
        return new MessageEntity(
                message.getId().toString(),
                message.getSender().name(),
                message.getContent().text(),
                message.getIntent().map(intent -> intent.type().name()).orElse(null),
                IntentDataJson.write(message.getIntent().orElse(null), message.getInReplyToMessageId().orElse(null)),
                message.getHuariqueId().map(HuariqueId::toString).orElse(null),
                message.getReservationId().map(ReservationId::toString).orElse(null),
                message.getSentAt());
    }

    private Message toDomain(MessageEntity entity) {
        return new Message(
                MessageId.of(entity.getId()),
                SenderType.valueOf(entity.getSender()),
                // El máximo configurable se valida al entrar; al leer se acepta lo que ya se guardó.
                MessageContent.of(entity.getContent(), Math.max(entity.getContent().length(), 1)),
                IntentDataJson.readIntent(entity.getIntentType(), entity.getIntentData()).orElse(null),
                entity.getHuariqueId() == null ? null : HuariqueId.of(entity.getHuariqueId()),
                entity.getReservationId() == null ? null : ReservationId.of(entity.getReservationId()),
                entity.getSentAt(),
                IntentDataJson.readInReplyTo(entity.getIntentData()).orElse(null));
    }
}
