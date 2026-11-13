package pe.foodly.messaging.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.foodly.messaging.domain.model.aggregates.Conversation;
import pe.foodly.messaging.domain.model.enums.ConversationStatus;
import pe.foodly.messaging.domain.model.valueobjects.ConversationId;
import pe.foodly.messaging.domain.model.valueobjects.ExplorerId;
import pe.foodly.messaging.domain.repositories.IConversationRepository;

/** Implementa {@link IConversationRepository} con Spring Data JPA sobre MySQL (5.6.4). */
@Repository
public class ConversationRepository implements IConversationRepository {

    private final ConversationJpaRepository jpa;
    private final ConversationMapper mapper;

    ConversationRepository(ConversationJpaRepository jpa, ConversationMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    /** Upsert del agregado completo: la conversación y todos sus mensajes. */
    @Override
    @Transactional
    public void save(Conversation conversation) {
        jpa.save(mapper.toEntity(conversation));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findById(ConversationId id) {
        return jpa.findById(id.toString()).map(mapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Conversation> findActiveByExplorerId(ExplorerId id) {
        return jpa.findFirstByExplorerIdAndStatusOrderByLastMessageAtDesc(
                        id.toString(), ConversationStatus.ACTIVE.name())
                .map(mapper::toDomain);
    }

    /** Todas las conversaciones del explorador, la de actividad más reciente primero. */
    @Override
    @Transactional(readOnly = true)
    public List<Conversation> findAllByExplorerId(ExplorerId id) {
        return jpa.findAllByExplorerIdOrderByLastMessageAtDescStartedAtDesc(id.toString()).stream()
                .map(mapper::toDomain)
                .toList();
    }
}
