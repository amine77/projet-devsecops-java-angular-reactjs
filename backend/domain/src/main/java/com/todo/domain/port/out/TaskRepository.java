package com.todo.domain.port.out;

import com.todo.domain.model.Task;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.TaskStatus;
import com.todo.domain.model.TeamId;
import com.todo.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * ══════════════════════════════════════════════════════════════
 *  PORT SORTANT — TaskRepository
 * ══════════════════════════════════════════════════════════════
 *
 *  Interface définie dans le DOMAINE — implémentée dans l'INFRASTRUCTURE.
 *
 *  C'est le cœur de l'Architecture Hexagonale (Ports & Adapters) :
 *  le domaine définit CE DONT IL A BESOIN (cette interface),
 *  sans savoir COMMENT c'est implémenté (PostgreSQL, in-memory, etc.).
 *
 *  En phase de test : on injecte un TaskRepositoryInMemory (mock simple).
 *  En local : TaskJpaRepository (PostgreSQL Minikube).
 *  En AWS   : même TaskJpaRepository (RDS PostgreSQL).
 *
 *  Bénéfice clé : on peut tester toute la logique domaine SANS Spring,
 *  SANS base de données, SANS Docker — tests instantanés.
 * ══════════════════════════════════════════════════════════════
 */
public interface TaskRepository {

    /**
     * Persiste une tâche (création ou mise à jour).
     *
     * Utilise save() pour les deux opérations (upsert sémantique).
     * L'infrastructure détecte si c'est un INSERT ou UPDATE via JPA.
     */
    void save(Task task);

    /**
     * Recherche une tâche par son ID.
     *
     * Retourne Optional.empty() si introuvable — jamais null.
     * L'appelant (domain service) décide quoi faire si absent
     * (en général : lever TaskNotFoundException).
     */
    Optional<Task> findById(TaskId id);

    /**
     * Tâches d'un Gestionnaire (RG-03 : il ne voit que les siennes).
     *
     * @param ownerId ID du Gestionnaire
     * @return liste ordonnée par date de création décroissante
     */
    List<Task> findByOwner(UserId ownerId);

    /**
     * Tâches d'une équipe — pour le tableau de bord Manager (RG-04).
     *
     * @param teamId ID de l'équipe
     * @return toutes les tâches de l'équipe, tous statuts confondus
     */
    List<Task> findByTeam(TeamId teamId);

    /**
     * Tâches d'une équipe filtrées par statut.
     *
     * Cas d'usage : Manager voit la "file d'action" (seulement A_FAIRE).
     */
    List<Task> findByTeamAndStatus(TeamId teamId, TaskStatus status);

    /**
     * Toutes les tâches — réservé au Super-Administrateur.
     * Attention : paginer en production (non géré ici pour simplifier Phase 2).
     */
    List<Task> findAll();

    /**
     * Suppression physique — uniquement pour les tâches en A_FAIRE (RG-05).
     * La vérification du statut est faite dans TaskDomainService avant l'appel.
     */
    void deleteById(TaskId id);

    /**
     * Tâches en retard (dueDate dépassée, statut != DONE).
     * Utilisé par les rappels automatiques au Manager.
     */
    List<Task> findOverdue();
}
