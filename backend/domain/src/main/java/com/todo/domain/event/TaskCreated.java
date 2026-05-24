package com.todo.domain.event;

import com.todo.domain.model.Priority;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.TeamId;
import com.todo.domain.model.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement : une tâche vient d'être créée par un Gestionnaire.
 *
 * Consommateurs :
 * - KafkaEventPublisher → topic "task-events" (Event Store MongoDB)
 * - SesNotificationAdapter → email au Manager (nouvelle tâche dans son équipe)
 *
 * Note : on n'envoie pas tous les champs de Task, seulement ce qui est
 * pertinent pour les consommateurs (évite le couplage sur la structure interne).
 */
public record TaskCreated(
        String eventId,
        TaskId taskId,
        String title,
        Priority priority,
        UserId ownerId,
        TeamId teamId,
        Instant occurredAt
) implements DomainEvent {

    /** Factory method — génère automatiquement l'eventId et l'horodatage. */
    public static TaskCreated of(TaskId taskId, String title, Priority priority,
                                  UserId ownerId, TeamId teamId) {
        return new TaskCreated(
                UUID.randomUUID().toString(),
                taskId, title, priority, ownerId, teamId,
                Instant.now()
        );
    }

    @Override
    public String eventType() {
        return "TASK_CREATED";
    }
}
