package com.todo.application.handler;

import com.todo.application.command.ValidateTaskCommand;
import com.todo.domain.port.in.TaskUseCase;
import jakarta.validation.Valid;

/**
 * ══════════════════════════════════════════════════════════════
 *  APPLICATION HANDLER — ValidateTaskHandler
 * ══════════════════════════════════════════════════════════════
 *
 *  Traite la validation d'une tâche : A_FAIRE → VALIDEE
 *
 *  CE QUI SE PASSE DANS LE DOMAINE (pour rappel pédagogique) :
 *
 *  1. TaskDomainService.validateTask(taskId, actorId)
 *     ↓
 *  2. loadTask(taskId) → vérifie existence
 *     loadUser(actorId) → vérifie existence
 *     ↓
 *  3. checkCanChangeStatus(actor, task)
 *     → actor.role().canChangeTaskStatus() = MANAGER ou SUPER_ADMIN ?
 *     → task.belongsToTeam(actor.teamId()) si MANAGER ?
 *     ↓
 *  4. task.validate(actorId)
 *     → vérifie canTransitionTo(VALIDEE)
 *     → retourne une NOUVELLE instance Task avec status=VALIDEE
 *     ↓
 *  5. taskRepository.save(validatedTask)
 *     cachePort.evict(...)
 *     ↓
 *  6. eventPublisher.publish(TaskValidated.of(...))
 *     → KafkaEventPublisher → topic "task-events"
 *     → Un consumer SES envoie un email au GESTIONNAIRE
 *     ↓
 *  7. notificationPort.notifyTaskValidated(validatedTask, owner)
 *     → Notification in-app via SQS → WebSocket ou polling
 *
 *  RETOUR VOID → HTTP 200 OK (ou 204 selon convention du projet)
 * ══════════════════════════════════════════════════════════════
 */
public class ValidateTaskHandler {

    private final TaskUseCase taskUseCase;

    public ValidateTaskHandler(TaskUseCase taskUseCase) {
        this.taskUseCase = taskUseCase;
    }

    public void handle(@Valid ValidateTaskCommand command) {
        taskUseCase.validateTask(command.taskId(), command.actorId());
    }
}
