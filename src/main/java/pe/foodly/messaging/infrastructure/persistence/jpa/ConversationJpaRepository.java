package pe.foodly.messaging.infrastructure.persistence.jpa;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA bajo {@link ConversationRepository}. Trae los mensajes en la misma consulta. */
interface ConversationJpaRepository extends JpaRepository<ConversationEntity, String> {

    @Override
    @EntityGraph(attributePaths = "messages")
    Optional<ConversationEntity> findById(String id);

    @EntityGraph(attributePaths = "messages")
    Optional<ConversationEntity> findFirstByExplorerIdAndStatusOrderByLastMessageAtDesc(
            String explorerId, String status);

    @EntityGraph(attributePaths = "messages")
    List<ConversationEntity> findAllByExplorerIdOrderByLastMessageAtDescStartedAtDesc(String explorerId);
}
