package com.todo.application.handler;

import com.todo.application.command.DeleteTaskCommand;
import com.todo.domain.port.in.TaskUseCase;
import jakarta.validation.Valid;

/**
 * ══════════════════════════════════════════════════════════════
 *  APPLICATION HANDLER — DeleteTaskHandler
 * ══════════════════════════════════════════════════════════════
 *
 *  Traite la suppression d'une tâche.
 *
 *  RÈGLE RG-05 :
 *  → Suppression uniquement si statut = A_FAIRE
 *  → Vérifiée dans TaskDomainService via task.status().isDeletable()
 *  → Si violation → UnauthorizedActionException → HTTP 403
 *
 *  SUPPRESSION ET FICHIERS ATTACHÉS :
 *  → Phase 3 : si la tâche a des pièces jointes, DeleteTaskHandler devra
 *    aussi appeler FileStoragePort.delete() pour chaque pièce jointe.
 *  → Pour l'instant, le domaine gère uniquement la suppression de la tâche.
 *  → TODO Phase 3 : ajouter la suppression des pièces jointes S3/local
 *
 *  RETOUR VOID → HTTP 204 No Content
 * ══════════════════════════════════════════════════════════════
 */
public class DeleteTaskHandler {

    private final TaskUseCase taskUseCase;

    public DeleteTaskHandler(TaskUseCase taskUseCase) {
        this.taskUseCase = taskUseCase;
    }

    public void handle(@Valid DeleteTaskCommand command) {
        taskUseCase.deleteTask(command.taskId(), command.actorId());
    }
}
