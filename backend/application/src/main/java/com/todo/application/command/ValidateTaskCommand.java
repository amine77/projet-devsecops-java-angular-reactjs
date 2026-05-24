package com.todo.application.command;

import com.todo.domain.model.TaskId;
import com.todo.domain.model.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * ══════════════════════════════════════════════════════════════
 *  COMMAND — ValidateTaskCommand
 * ══════════════════════════════════════════════════════════════
 *
 *  Validation d'une tâche : A_FAIRE → VALIDEE
 *
 *  RÈGLES MÉTIER :
 *  → Seul MANAGER (son équipe) ou SUPER_ADMIN peut valider
 *  → La tâche doit être en statut A_FAIRE (sinon InvalidTaskStatusException)
 *  → Déclenche un Domain Event TaskValidated
 *    → KafkaEventPublisher → SES envoie un email au GESTIONNAIRE
 *
 *  FLOW COMPLET :
 *  PUT /tasks/{id}/validate
 *    → TaskController extrait actorId du JWT
 *    → construit ValidateTaskCommand
 *    → délègue à ValidateTaskHandler
 *    → qui appelle taskUseCase.validateTask()
 *    → TaskDomainService orchestre la transition et publie l'event
 * ══════════════════════════════════════════════════════════════
 */
public record ValidateTaskCommand(

        @NotNull TaskId taskId,
        @NotNull UserId actorId

) {}
