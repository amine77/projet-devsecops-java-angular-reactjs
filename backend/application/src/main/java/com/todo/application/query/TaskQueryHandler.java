package com.todo.application.query;

import com.todo.domain.model.Task;
import com.todo.domain.model.TaskStatus;
import com.todo.domain.model.TeamId;
import com.todo.domain.model.UserId;
import com.todo.domain.port.in.TaskUseCase;
import jakarta.validation.Valid;

import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════
 *  APPLICATION QUERY HANDLER — TaskQueryHandler
 * ══════════════════════════════════════════════════════════════
 *
 *  Regroupe TOUS les cas d'usage de lecture dans un seul handler.
 *
 *  POURQUOI UN SEUL HANDLER POUR LES QUERIES ?
 *  → Les queries sont sans effet de bord (elles ne modifient rien)
 *  → Un handler de lecture par requête serait du boilerplate inutile
 *  → Ce handler peut être étendu avec de la pagination, du tri, des filtres
 *    sans impacter les Commands
 *
 *  DIFFÉRENCE AVEC LES COMMAND HANDLERS :
 *  → Les Command Handlers retournent void ou un ID
 *  → Les Query Handlers retournent des données (List<Task>, Task...)
 *
 *  NOTE SUR Task COMME TYPE DE RETOUR :
 *  → On retourne l'objet domaine Task directement à ce niveau.
 *  → C'est la couche infrastructure (contrôleur REST) qui le convertit
 *    en TaskResponse DTO (JSON).
 *  → Pourquoi ne pas convertir ici ? La couche application ne connaît
 *    pas les DTOs HTTP — c'est une responsabilité infrastructure.
 *
 *  ÉVOLUTION FUTURE (CQRS avancé) :
 *  → On pourrait avoir un TaskReadModel (projection dénormalisée)
 *    distinct de l'agrégat Task utilisé pour les Commands.
 *  → Exemple : TaskSummaryView (id, title, status, priorityLabel)
 *    stocké dans une vue PostgreSQL ou un index Redis.
 * ══════════════════════════════════════════════════════════════
 */
public class TaskQueryHandler {

    private final TaskUseCase taskUseCase;

    public TaskQueryHandler(TaskUseCase taskUseCase) {
        this.taskUseCase = taskUseCase;
    }

    /**
     * Tâches du GESTIONNAIRE connecté.
     * Utilise le Cache-Aside via CachePort (Redis → PostgreSQL).
     */
    public List<Task> getMyTasks(@Valid GetMyTasksQuery query) {
        return taskUseCase.getMyTasks(query.actorId());
    }

    /**
     * Tâches d'une équipe — tableau de bord Manager.
     * Vérifie que l'acteur a accès à l'équipe demandée.
     */
    public List<Task> getTeamTasks(@Valid GetTeamTasksQuery query) {
        return taskUseCase.getTeamTasks(query.teamId(), query.actorId());
    }

    /**
     * Détail d'une tâche avec vérification des droits d'accès.
     */
    public Task getTaskById(@Valid GetTaskByIdQuery query) {
        return taskUseCase.getTaskById(query.taskId(), query.actorId());
    }

    /**
     * Tâches en A_FAIRE pour la "file d'action" du Manager.
     * Pas de cache — données fraîches nécessaires pour la prise de décision.
     */
    public List<Task> getPendingTasksForTeam(TeamId teamId, UserId actorId) {
        return taskUseCase.getPendingTasksForTeam(teamId, actorId);
    }

    /**
     * Tâches en retard pour l'équipe — alertes Manager.
     * Filtrage par teamId fait dans le domaine (Task.belongsToTeam()).
     */
    public List<Task> getOverdueTasksForTeam(TeamId teamId, UserId actorId) {
        return taskUseCase.getOverdueTasksForTeam(teamId, actorId);
    }

    /**
     * Toutes les tâches — SUPER_ADMIN uniquement.
     * Vérification du rôle dans le domaine (actor.role().hasGlobalAccess()).
     */
    public List<Task> getAllTasks(UserId actorId) {
        return taskUseCase.getAllTasks(actorId);
    }

    /**
     * Filtre par statut pour les tableaux de bord.
     * Le comportement varie selon le rôle (voir TaskDomainService.getTasksByStatus()).
     */
    public List<Task> getTasksByStatus(TaskStatus status, UserId actorId) {
        return taskUseCase.getTasksByStatus(status, actorId);
    }
}
