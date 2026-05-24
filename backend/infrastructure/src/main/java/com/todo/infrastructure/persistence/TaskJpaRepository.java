package com.todo.infrastructure.persistence;

import com.todo.domain.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * ══════════════════════════════════════════════════════════════
 *  SPRING DATA JPA — TaskJpaRepository
 * ══════════════════════════════════════════════════════════════
 *
 *  RÔLE : Interface Spring Data JPA pour l'accès à la table `tasks`.
 *
 *  SPRING DATA MAGIC :
 *  → Spring génère automatiquement l'implémentation à partir de l'interface
 *  → Pas de SQL pour les requêtes simples (findBy...)
 *  → Nom de méthode = requête JPQL implicite :
 *    findByOwnerId → SELECT t FROM TaskJpaEntity t WHERE t.ownerId = ?
 *
 *  DIFFÉRENCE AVEC TaskRepository (port domaine) :
 *  → TaskRepository est l'interface du DOMAINE (UUID TaskId, UUID TeamId, etc.)
 *  → TaskJpaRepository est l'interface SPRING DATA (UUID bruts)
 *  → TaskRepositoryAdapter fait le pont entre les deux
 *
 *  POURQUOI NE PAS FAIRE IMPLÉMENTER TaskRepository À TaskJpaRepository ?
 *  → Spring Data exige d'étendre JpaRepository<T, ID>
 *  → Si on implémentait aussi TaskRepository, on mélangerait les niveaux
 *  → L'adaptateur TaskRepositoryAdapter fait la séparation proprement
 *
 *  REQUÊTES PERSONNALISÉES (@Query) :
 *  → Pour les requêtes complexes que Spring Data ne peut pas dériver du nom
 *  → @Query utilise JPQL (Java Persistence Query Language) par défaut
 *  → Pour du SQL natif : @Query(nativeQuery = true, ...)
 * ══════════════════════════════════════════════════════════════
 */
public interface TaskJpaRepository extends JpaRepository<TaskJpaEntity, UUID> {

    /**
     * Tâches d'un propriétaire (GESTIONNAIRE).
     * Équivalent JPQL : SELECT t FROM TaskJpaEntity t WHERE t.ownerId = :ownerId
     */
    List<TaskJpaEntity> findByOwnerId(UUID ownerId);

    /**
     * Tâches d'une équipe (MANAGER).
     */
    List<TaskJpaEntity> findByTeamId(UUID teamId);

    /**
     * Tâches d'une équipe filtrées par statut.
     * Utilisé pour la "file d'action" (getPendingTasksForTeam → status=A_FAIRE).
     */
    List<TaskJpaEntity> findByTeamIdAndStatus(UUID teamId, TaskStatus status);

    /**
     * Tâches en retard : dueDate dans le passé ET statut non terminal.
     *
     * JPQL (pas SQL natif) pour rester indépendant du dialecte DB.
     * :today = LocalDate.now() injecté par l'adaptateur.
     *
     * OPTIMISATION : l'index partiel sur due_date (V1__init_schema.sql) accélère
     * cette requête car seuls les statuts actifs ont des échéances à surveiller.
     */
    @Query("""
        SELECT t FROM TaskJpaEntity t
        WHERE t.dueDate < :today
          AND t.status NOT IN ('DONE')
        ORDER BY t.dueDate ASC
        """)
    List<TaskJpaEntity> findOverdue(@Param("today") LocalDate today);
}
