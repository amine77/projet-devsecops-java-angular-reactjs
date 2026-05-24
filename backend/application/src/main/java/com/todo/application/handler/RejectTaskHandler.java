package com.todo.application.handler;

import com.todo.application.command.RejectTaskCommand;
import com.todo.domain.port.in.TaskUseCase;
import jakarta.validation.Valid;

/**
 * ══════════════════════════════════════════════════════════════
 *  APPLICATION HANDLER — RejectTaskHandler
 * ══════════════════════════════════════════════════════════════
 *
 *  Traite le rejet d'une tâche : A_FAIRE → REJETEE
 *
 *  FLOW DU MOTIF DE REJET (RG-06) :
 *
 *  1. Client HTTP envoie :
 *     PUT /tasks/{id}/reject
 *     Body: { "rejectionReason": "Budget insuffisant" }
 *
 *  2. TaskController construit RejectTaskCommand avec le motif
 *
 *  3. RejectTaskHandler.handle() → @Valid vérifie @NotBlank
 *     → Si motif absent : HTTP 400 Bad Request avant d'appeler le domaine
 *
 *  4. TaskUseCase.rejectTask() → TaskDomainService → task.reject(reason)
 *     → Le domaine vérifie AUSSI le motif (défense en profondeur)
 *     → Si absent : RejectionReasonRequiredException → HTTP 422
 *
 *  5. L'event TaskRejected contient le motif
 *     → Email au GESTIONNAIRE : "Votre tâche 'X' a été rejetée : Budget insuffisant"
 *
 *  CONSÉQUENCE POUR LE GESTIONNAIRE :
 *  → La tâche passe en REJETEE avec le motif visible dans l'UI
 *  → La prochaine modification (UpdateTaskCommand) la remet en A_FAIRE (RG-07)
 *    et efface le motif (Task.update() clear rejectionReason)
 * ══════════════════════════════════════════════════════════════
 */
public class RejectTaskHandler {

    private final TaskUseCase taskUseCase;

    public RejectTaskHandler(TaskUseCase taskUseCase) {
        this.taskUseCase = taskUseCase;
    }

    public void handle(@Valid RejectTaskCommand command) {
        taskUseCase.rejectTask(command.taskId(), command.rejectionReason(), command.actorId());
    }
}
