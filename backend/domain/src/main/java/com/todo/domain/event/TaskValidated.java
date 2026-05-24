package com.todo.domain.event;

import com.todo.domain.model.TaskId;
import com.todo.domain.model.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement : une tâche vient d'être validée par un Manager.
 *
 * Consommateurs :
 * - SES → email au Gestionnaire "Votre tâche a été validée"
 * - MongoDB Event Store → historique des décisions Manager
 */
public record TaskValidated(
        String eventId,
        TaskId taskId,
        UserId validatedBy,
        UserId ownerId,    // pour router la notification au bon Gestionnaire
        Instant occurredAt
) implements DomainEvent {

    public static TaskValidated of(TaskId taskId, UserId validatedBy, UserId ownerId) {
        return new TaskValidated(
                UUID.randomUUID().toString(),
                taskId, validatedBy, ownerId,
                Instant.now()
        );
    }

    @Override
    public String eventType() {
        return "TASK_VALIDATED";
    }
}
