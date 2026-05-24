package com.todo.domain.exception;

import com.todo.domain.model.TaskId;

/**
 * Levée quand on tente de rejeter une tâche sans fournir de motif.
 *
 * Correspond à la règle métier RG-06 :
 * "Le motif de rejet est obligatoire lors d'un rejet."
 *
 * Cette exception est levée dans Task.reject() si le motif est null/vide.
 * Traduite en HTTP 400 (Bad Request) par la couche infrastructure.
 */
public class RejectionReasonRequiredException extends DomainException {

    private final TaskId taskId;

    public RejectionReasonRequiredException(TaskId taskId) {
        super("Motif de rejet obligatoire pour la tâche %s (RG-06)".formatted(taskId));
        this.taskId = taskId;
    }

    public TaskId taskId() {
        return taskId;
    }
}
