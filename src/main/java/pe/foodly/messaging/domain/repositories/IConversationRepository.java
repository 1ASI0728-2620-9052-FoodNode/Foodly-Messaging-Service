package pe.foodly.messaging.domain.repositories;

import java.util.List;
import java.util.Optional;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;

/** Contrato de persistencia de {@link Conversation}, sin depender de JPA (5.6.1, 5.6.7.1). */
public interface IConversationRepository {

    void save(Conversation conversation);

    Optional<Conversation> findById(ConversationId id);

    /** Conversación ACTIVE del explorador, si la tiene. */
    Optional<Conversation> findActiveByExplorerId(ExplorerId id);

    List<Conversation> findAllByExplorerId(ExplorerId id);
}
