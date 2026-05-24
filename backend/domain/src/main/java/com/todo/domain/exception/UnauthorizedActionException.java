package com.todo.domain.exception;

import com.todo.domain.model.TaskId;
import com.todo.domain.model.UserId;

/**
 * ══════════════════════════════════════════════════════════════
 *  Levée quand un utilisateur tente une action hors de son périmètre.
 * ══════════════════════════════════════════════════════════════
 *
 *  Deux cas distincts :
 *
 *  1. RBAC (rôle) : un Gestionnaire tente de valider une tâche → HTTP 403
 *     → Le rôle n'autorise pas cette action
 *
 *  2. Périmètre de données : un Manager tente d'agir sur la tâche
 *     d'une autre équipe → HTTP 403
 *     → Le rôle est correct mais les données sont hors périmètre
 *
 *  Pourquoi ne pas renvoyer HTTP 404 dans le cas 2 ?
 *  → Sécurité par l'obscurité : révéler qu'une tâche "existe mais
 *    vous n'y avez pas accès" est une fuite d'information.
 *    Cependant, dans ce projet pédagogique on utilise 403 pour
 *    la clarté des logs. En production on utiliserait 404.
 * ══════════════════════════════════════════════════════════════
 */
public class UnauthorizedActionException extends DomainException {

    private final UserId actorId;
    private final TaskId taskId;
    private final String action;

    public UnauthorizedActionException(UserId actorId, TaskId taskId, String action) {
        super("Action non autorisée : l'utilisateur %s ne peut pas effectuer '%s' sur la tâche %s"
                .formatted(actorId, action, taskId));
        this.actorId = actorId;
        this.taskId = taskId;
        this.action = action;
    }

    public UserId actorId() { return actorId; }
    public TaskId taskId()  { return taskId; }
    public String action()  { return action; }
}
