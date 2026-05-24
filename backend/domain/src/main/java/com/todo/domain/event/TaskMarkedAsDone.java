package com.todo.domain.event;

import com.todo.domain.model.TaskId;
import com.todo.domain.model.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement : une tâche vient d'être marquée Done (terminée).
 *
 * Consommateurs :
 * - SES → email au Gestionnaire "Votre tâche est terminée"
 * - Lambda TaskArchiver → archive les tâches Done > 30 jours (piloté par EventBridge, pas cet event)
 * - MongoDB Event Store → métriques de complétion (délai création → Done)
 */
public record TaskMarkedAsDone(
        String eventId,
        TaskId taskId,
        UserId doneBy,
        UserId ownerId,
        Instant occurredAt
) implements DomainEvent {

    public static TaskMarkedAsDone of(TaskId taskId, UserId doneBy, UserId ownerId) {
        return new TaskMarkedAsDone(
                UUID.randomUUID().toString(),
                taskId, doneBy, ownerId,
                Instant.now()
        );
    }

    @Override
    public String eventType() {
        return "TASK_MARKED_AS_DONE";
    }
}
