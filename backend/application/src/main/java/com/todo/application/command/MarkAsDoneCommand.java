package com.todo.application.command;

import com.todo.domain.model.TaskId;
import com.todo.domain.model.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * ══════════════════════════════════════════════════════════════
 *  COMMAND — MarkAsDoneCommand
 * ══════════════════════════════════════════════════════════════
 *
 *  Marquer une tâche comme terminée : A_FAIRE ou VALIDEE → DONE
 *
 *  PARTICULARITÉ : DONE est un état terminal.
 *  → Aucune transition n'est possible depuis DONE (TaskStatus.isTerminal())
 *  → C'est un état définitif — on ne peut pas "re-ouvrir" une tâche DONE
 *    (dans cette version ; une Phase future pourrait ajouter ce cas)
 *
 *  TRANSITIONS AUTORISÉES (machine d'état dans TaskStatus) :
 *    A_FAIRE → DONE  ✓  (le Manager/SuperAdmin décide que c'est terminé)
 *    VALIDEE → DONE  ✓  (après validation, on la clôture)
 *    REJETEE → DONE  ✗  (impossible — doit d'abord être corrigée)
 * ══════════════════════════════════════════════════════════════
 */
public record MarkAsDoneCommand(

        @NotNull TaskId taskId,
        @NotNull UserId actorId

) {}
