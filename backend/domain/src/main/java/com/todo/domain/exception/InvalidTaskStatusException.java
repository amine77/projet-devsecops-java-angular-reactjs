package com.todo.domain.exception;

import com.todo.domain.model.TaskId;
import com.todo.domain.model.TaskStatus;

/**
 * Levée quand on tente une transition de statut invalide.
 *
 * Exemples :
 * - Valider une tâche déjà DONE → invalide
 * - Rejeter une tâche VALIDEE  → invalide (seul A_FAIRE peut être rejeté)
 *
 * Les transitions valides sont définies dans TaskStatus.canTransitionTo().
 * Traduite en HTTP 422 (Unprocessable Entity) par la couche infrastructure.
 */
public class InvalidTaskStatusException extends DomainException {

    private final TaskId taskId;
    private final TaskStatus currentStatus;
    private final String attemptedAction;

    public InvalidTaskStatusException(TaskId taskId, TaskStatus currentStatus, String attemptedAction) {
        super("Action '%s' impossible sur la tâche %s en statut '%s'"
                .formatted(attemptedAction, taskId, currentStatus));
        this.taskId = taskId;
        this.currentStatus = currentStatus;
        this.attemptedAction = attemptedAction;
    }

    public TaskId taskId()           { return taskId; }
    public TaskStatus currentStatus(){ return currentStatus; }
    public String attemptedAction()  { return attemptedAction; }
}
