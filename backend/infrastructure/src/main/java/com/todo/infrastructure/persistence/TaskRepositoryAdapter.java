package com.todo.infrastructure.persistence;

import com.todo.domain.model.Task;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.TaskStatus;
import com.todo.domain.model.TeamId;
import com.todo.domain.model.UserId;
import com.todo.domain.port.out.TaskRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * ══════════════════════════════════════════════════════════════
 *  ADAPTATEUR — TaskRepositoryAdapter
 * ══════════════════════════════════════════════════════════════
 *
 *  C'est l'ADAPTATEUR SORTANT pour la persistence.
 *  Il implémente le PORT SORTANT TaskRepository (défini dans le domaine)
 *  en utilisant Spring Data JPA + PostgreSQL.
 *
 *  POSITION DANS L'HEXAGONE :
 *  ┌──────────────────────────────────────────────────────────┐
 *  │  DOMAINE                                                 │
 *  │  TaskRepository (port out) ← interface                   │
 *  └──────────────────┬───────────────────────────────────────┘
 *                     │ implements
 *  ┌──────────────────▼───────────────────────────────────────┐
 *  │  INFRASTRUCTURE                                          │
 *  │  TaskRepositoryAdapter (adaptateur sortant)              │
 *  │      ↓ uses                                              │
 *  │  TaskJpaRepository (Spring Data JPA)                     │
 *  │      ↓ SQL                                               │
 *  │  PostgreSQL                                              │
 *  └──────────────────────────────────────────────────────────┘
 *
 *  RESPONSABILITÉS :
 *  1. Convertir TaskId/TeamId/UserId (Value Objects) ↔ UUID (bruts JPA)
 *  2. Convertir Task (agrégat domaine) ↔ TaskJpaEntity (entité JPA)
 *  3. Déléguer les requêtes à TaskJpaRepository
 *
 *  @Transactional :
 *  → Les transactions sont gérées ICI, pas dans le domaine
 *  → Règle d'or : la gestion de transaction est une préoccupation technique
 *    qui appartient à la couche infrastructure
 *  → readOnly = true sur les méthodes de lecture : optimisation Hibernate
 *    (pas de dirty checking, snapshot des entités désactivé)
 * ══════════════════════════════════════════════════════════════
 */
@Repository // Marque ce Bean comme adaptateur de persistence, active la traduction des exceptions JPA
public class TaskRepositoryAdapter implements TaskRepository {

    /** Spring Data JPA — injecté par Spring Boot auto-configuration */
    private final TaskJpaRepository jpaRepository;

    public TaskRepositoryAdapter(TaskJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    // ══════════════════════════════════════════════════════════
    //  OPÉRATIONS D'ÉCRITURE (avec transaction de modification)
    // ══════════════════════════════════════════════════════════

    /**
     * Sauvegarde ou met à jour une tâche.
     *
     * JPA MERGE vs PERSIST :
     * → jpaRepository.save() fait un merge si l'entité existe (UPDATE)
     *   ou un persist si elle n'existe pas (INSERT)
     * → La détection "nouvelle entité ?" se fait par l'ID (non null → merge)
     *
     * OPTIMISTIC LOCKING :
     * → Si la version de l'entité ne correspond pas à la DB :
     *   JPA lève OptimisticLockException (runtime)
     * → Spring la traduit en ObjectOptimisticLockingFailureException
     * → Le contrôleur REST la traduit en HTTP 409 Conflict
     */
    @Override
    @Transactional
    public void save(Task task) {
        // Conversion domaine → JPA, puis délégation
        jpaRepository.save(TaskJpaEntity.fromDomain(task));
    }

    @Override
    @Transactional
    public void deleteById(TaskId taskId) {
        jpaRepository.deleteById(taskId.value());
    }

    // ══════════════════════════════════════════════════════════
    //  OPÉRATIONS DE LECTURE (readOnly pour optimisation)
    // ══════════════════════════════════════════════════════════

    /**
     * Cherche par ID, retourne Optional.empty() si absent.
     * Le domaine lève TaskNotFoundException si l'Optional est vide.
     *
     * readOnly = true :
     * → Hibernate désactive le dirty checking (tracking des changements)
     * → Postgres peut router vers un read replica si configuré
     * → Légèrement plus rapide (pas de snapshot des entités)
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<Task> findById(TaskId taskId) {
        return jpaRepository.findById(taskId.value())
                .map(TaskJpaEntity::toDomain); // Conversion JPA → domaine
    }

    @Override
    @Transactional(readOnly = true)
    public List<Task> findByOwner(UserId ownerId) {
        return jpaRepository.findByOwnerId(ownerId.value())
                .stream()
                .map(TaskJpaEntity::toDomain)
                .toList(); // Java 21 : toList() retourne une liste immuable
    }

    @Override
    @Transactional(readOnly = true)
    public List<Task> findByTeam(TeamId teamId) {
        return jpaRepository.findByTeamId(teamId.value())
                .stream()
                .map(TaskJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Task> findByTeamAndStatus(TeamId teamId, TaskStatus status) {
        return jpaRepository.findByTeamIdAndStatus(teamId.value(), status)
                .stream()
                .map(TaskJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Task> findAll() {
        return jpaRepository.findAll()
                .stream()
                .map(TaskJpaEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Task> findOverdue() {
        // On passe LocalDate.now() ici (couche infrastructure = monde réel)
        // Le domaine ne devrait jamais appeler LocalDate.now() directement
        // (difficile à tester et couplage au temps système)
        return jpaRepository.findOverdue(LocalDate.now())
                .stream()
                .map(TaskJpaEntity::toDomain)
                .toList();
    }
}
