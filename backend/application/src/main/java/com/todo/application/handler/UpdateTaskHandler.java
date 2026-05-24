package com.todo.application.handler;

import com.todo.application.command.UpdateTaskCommand;
import com.todo.domain.port.in.TaskUseCase;
import jakarta.validation.Valid;

/**
 * ══════════════════════════════════════════════════════════════
 *  APPLICATION HANDLER — UpdateTaskHandler
 * ══════════════════════════════════════════════════════════════
 *
 *  Traite la modification d'une tâche existante.
 *
 *  RÈGLES APPLIQUÉES (dans le domaine, pas ici) :
 *  → RG-05 : modification uniquement A_FAIRE ou REJETEE
 *  → RG-03 : GESTIONNAIRE ne peut modifier QUE ses tâches
 *  → RG-07 : REJETEE → A_FAIRE automatiquement lors d'une modification
 *
 *  RETOUR VOID :
 *  → Une modification n'a pas besoin de retourner la tâche mise à jour.
 *  → Le contrôleur retournera HTTP 204 No Content.
 *  → Si le client a besoin des données fraîches, il refait un GET /tasks/{id}.
 *  → Ce pattern "command = void, query = data" est le CQRS de base.
 * ══════════════════════════════════════════════════════════════
 */
public class UpdateTaskHandler {

    private final TaskUseCase taskUseCase;

    public UpdateTaskHandler(TaskUseCase taskUseCase) {
        this.taskUseCase = taskUseCase;
    }

    public void handle(@Valid UpdateTaskCommand command) {
        taskUseCase.updateTask(
                command.taskId(),
                command.title(),
                command.description(),
                command.priority(),
                command.dueDate(),
                command.actorId()
        );
    }
}
