package com.todo.domain.exception;

import com.todo.domain.model.TaskId;

/**
 * Levée quand une tâche est introuvable dans le repository.
 *
 * La couche infrastructure (@ControllerAdvice) traduit cette exception
 * en HTTP 404 avec un corps JSON standardisé.
 *
 * Exemple de réponse HTTP générée :
 * {
 *   "error": "TASK_NOT_FOUND",
 *   "message": "Tâche introuvable : 123e4567-e89b-12d3-a456-426614174000",
 *   "status": 404
 * }
 */
public class TaskNotFoundException extends DomainException {

    private final TaskId taskId;

    public TaskNotFoundException(TaskId taskId) {
        super("Tâche introuvable : " + taskId);
        this.taskId = taskId;
    }

    public TaskId taskId() {
        return taskId;
    }
}
