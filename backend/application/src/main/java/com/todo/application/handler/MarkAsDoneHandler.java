package com.todo.application.handler;

import com.todo.application.command.MarkAsDoneCommand;
import com.todo.domain.port.in.TaskUseCase;
import jakarta.validation.Valid;

/**
 * ══════════════════════════════════════════════════════════════
 *  APPLICATION HANDLER — MarkAsDoneHandler
 * ══════════════════════════════════════════════════════════════
 *
 *  Clôture définitive d'une tâche : A_FAIRE ou VALIDEE → DONE
 *
 *  ÉTAT TERMINAL :
 *  → DONE est le seul état sans transition sortante (TaskStatus.isTerminal())
 *  → Une fois DONE, une tâche ne peut plus être modifiée
 *  → Elle reste en base pour l'historique et les métriques
 *
 *  CAS D'USAGE TYPIQUE :
 *  → Le GESTIONNAIRE complète le travail → GESTIONNAIRE notifie son MANAGER
 *  → Le MANAGER vérifie → marque DONE (ou peut passer directement si fait)
 *
 *  ÉVÉNEMENT PUBLIÉ : TaskMarkedAsDone
 *  → Déclenche une notification in-app et un email au GESTIONNAIRE
 *  → Peut alimenter des métriques (temps de traitement moyen, vélocité équipe)
 * ══════════════════════════════════════════════════════════════
 */
public class MarkAsDoneHandler {

    private final TaskUseCase taskUseCase;

    public MarkAsDoneHandler(TaskUseCase taskUseCase) {
        this.taskUseCase = taskUseCase;
    }

    public void handle(@Valid MarkAsDoneCommand command) {
        taskUseCase.markAsDone(command.taskId(), command.actorId());
    }
}
