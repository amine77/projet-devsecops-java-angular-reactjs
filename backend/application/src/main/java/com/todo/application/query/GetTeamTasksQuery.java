package com.todo.application.query;

import com.todo.domain.model.TeamId;
import com.todo.domain.model.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * ══════════════════════════════════════════════════════════════
 *  QUERY — GetTeamTasksQuery
 * ══════════════════════════════════════════════════════════════
 *
 *  Récupère les tâches d'une équipe — vue Manager.
 *
 *  DROITS D'ACCÈS (vérifiés dans le domaine) :
 *  → MANAGER : uniquement son équipe (teamId == actor.teamId())
 *  → SUPER_ADMIN : toutes les équipes
 *  → GESTIONNAIRE : pas autorisé → UnauthorizedActionException
 *
 *  CACHE :
 *  → Les tâches d'équipe sont cachées par teamId dans Redis
 *  → TTL : 5 minutes (configurable dans application.yml)
 *  → Invalidation : à chaque modification d'une tâche de l'équipe
 *    via cachePort.evict(taskId, ownerId, teamId)
 * ══════════════════════════════════════════════════════════════
 */
public record GetTeamTasksQuery(
        @NotNull TeamId teamId,
        @NotNull UserId actorId
) {}
