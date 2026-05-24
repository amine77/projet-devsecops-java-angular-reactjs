package com.todo.domain.port.in;

import com.todo.domain.model.Priority;
import com.todo.domain.model.Task;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.TaskStatus;
import com.todo.domain.model.TeamId;
import com.todo.domain.model.UserId;

import java.time.LocalDate;
import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════
 *  PORT ENTRANT — TaskUseCase
 * ══════════════════════════════════════════════════════════════
 *
 *  Ce port définit CE QUE L'APPLICATION PEUT FAIRE avec les tâches.
 *  C'est le contrat entre la couche infrastructure (contrôleurs REST,
 *  consumers Kafka) et le domaine.
 *
 *  Implémenté par : TaskDomainService (dans domain/service/)
 *  Appelé par     : Les handlers de la couche application
 *                   (CreateTaskHandler, ValidateTaskHandler...)
 *
 *  Note sur les paramètres : on pourrait utiliser des Command objects
 *  (CreateTaskCommand, ValidateTaskCommand...) pour regrouper les params.
 *  C'est fait dans la couche application. Ici, on garde des signatures
 *  simples pour que le domaine reste lisible sans dépendances supplémentaires.
 * ══════════════════════════════════════════════════════════════
 */
public interface TaskUseCase {

    // ── Commandes Gestionnaire ──────────────────────────────────

    /**
     * Crée une nouvelle tâche au statut A_FAIRE.
     * Accessible : GESTIONNAIRE, SUPER_ADMINISTRATEUR
     *
     * @return l'ID de la tâche créée
     */
    TaskId createTask(String title, String description, Priority priority,
                      LocalDate dueDate, UserId actorId);

    /**
     * Modifie une tâche (uniquement en A_FAIRE ou REJETEE — RG-05).
     * Si REJETEE → repasse automatiquement en A_FAIRE (RG-07).
     * Accessible : GESTIONNAIRE (ses tâches), SUPER_ADMINISTRATEUR
     */
    void updateTask(TaskId taskId, String title, String description,
                    Priority priority, LocalDate dueDate, UserId actorId);

    /**
     * Supprime une tâche (uniquement en A_FAIRE — RG-05).
     * Accessible : GESTIONNAIRE (ses tâches), SUPER_ADMINISTRATEUR
     */
    void deleteTask(TaskId taskId, UserId actorId);

    // ── Commandes Manager ───────────────────────────────────────

    /**
     * Valide une tâche A_FAIRE → VALIDEE.
     * Accessible : MANAGER (son équipe), SUPER_ADMINISTRATEUR
     */
    void validateTask(TaskId taskId, UserId actorId);

    /**
     * Rejette une tâche A_FAIRE → REJETEE.
     * Le motif est OBLIGATOIRE (RG-06).
     * Accessible : MANAGER (son équipe), SUPER_ADMINISTRATEUR
     */
    void rejectTask(TaskId taskId, String rejectionReason, UserId actorId);

    /**
     * Marque une tâche comme terminée (A_FAIRE ou VALIDEE → DONE).
     * Accessible : MANAGER (son équipe), SUPER_ADMINISTRATEUR
     */
    void markAsDone(TaskId taskId, UserId actorId);

    /**
     * Ajoute un commentaire (tous les rôles, tous les statuts).
     */
    void commentTask(TaskId taskId, String content, UserId actorId);

    // ── Requêtes ────────────────────────────────────────────────

    /**
     * Tâches du Gestionnaire connecté (RG-03).
     * Accessible : GESTIONNAIRE (ses tâches), SUPER_ADMINISTRATEUR
     */
    List<Task> getMyTasks(UserId actorId);

    /**
     * Tâches d'une équipe — tableau de bord Manager (RG-04).
     * Accessible : MANAGER (son équipe), SUPER_ADMINISTRATEUR
     */
    List<Task> getTeamTasks(TeamId teamId, UserId actorId);

    /**
     * Tâches en attente de décision du Manager (A_FAIRE uniquement).
     * Utilisé pour la "file d'action" Manager.
     */
    List<Task> getPendingTasksForTeam(TeamId teamId, UserId actorId);

    /**
     * Toutes les tâches — réservé SUPER_ADMINISTRATEUR.
     */
    List<Task> getAllTasks(UserId actorId);

    /**
     * Détail d'une tâche par son ID.
     * Les droits d'accès sont vérifiés selon le rôle de l'acteur.
     */
    Task getTaskById(TaskId taskId, UserId actorId);

    /**
     * Tâches en retard pour l'équipe du Manager.
     */
    List<Task> getOverdueTasksForTeam(TeamId teamId, UserId actorId);

    /** Filtre par statut pour le tableau de bord. */
    List<Task> getTasksByStatus(TaskStatus status, UserId actorId);
}
