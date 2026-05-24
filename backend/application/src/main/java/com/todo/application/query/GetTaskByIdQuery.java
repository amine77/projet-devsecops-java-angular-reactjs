package com.todo.application.query;

import com.todo.domain.model.TaskId;
import com.todo.domain.model.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * ══════════════════════════════════════════════════════════════
 *  QUERY — GetTaskByIdQuery
 * ══════════════════════════════════════════════════════════════
 *
 *  Récupère le détail d'une tâche spécifique.
 *  Les droits d'accès sont vérifiés dans checkCanAccess() :
 *  → Le GESTIONNAIRE peut voir sa propre tâche
 *  → Le MANAGER peut voir toutes les tâches de son équipe
 *  → Le SUPER_ADMIN peut voir toutes les tâches
 * ══════════════════════════════════════════════════════════════
 */
public record GetTaskByIdQuery(
        @NotNull TaskId taskId,
        @NotNull UserId actorId
) {}
