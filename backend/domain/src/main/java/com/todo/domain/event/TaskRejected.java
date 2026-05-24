package com.todo.domain.event;

import com.todo.domain.model.TaskId;
import com.todo.domain.model.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement : une tâche vient d'être rejetée par un Manager.
 *
 * Le motif de rejet est inclus dans l'event pour que le consommateur
 * SES puisse l'inclure dans l'email au Gestionnaire sans re-charger la tâche.
 *
 * Consommateurs :
 * - SES → email au Gestionnaire avec le motif de rejet (RG-06)
 * - MongoDB Event Store → historique des rejets (analyse des patterns)
 */
public record TaskRejected(
        String eventId,
        TaskId taskId,
        UserId rejectedBy,
        UserId ownerId,
        String rejectionReason,  // inclus directement — évite un aller-retour en BDD
        Instant occurredAt
) implements DomainEvent {

    public static TaskRejected of(TaskId taskId, UserId rejectedBy,
                                   UserId ownerId, String rejectionReason) {
        return new TaskRejected(
                UUID.randomUUID().toString(),
                taskId, rejectedBy, ownerId, rejectionReason,
                Instant.now()
        );
    }

    @Override
    public String eventType() {
        return "TASK_REJECTED";
    }
}
