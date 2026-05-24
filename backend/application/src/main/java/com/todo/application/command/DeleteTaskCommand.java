package com.todo.application.command;

import com.todo.domain.model.TaskId;
import com.todo.domain.model.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * ══════════════════════════════════════════════════════════════
 *  COMMAND — DeleteTaskCommand
 * ══════════════════════════════════════════════════════════════
 *
 *  Suppression d'une tâche.
 *
 *  RÈGLE MÉTIER :
 *  → RG-05 : suppression uniquement si statut = A_FAIRE
 *    → vérifiée dans TaskDomainService.deleteTask() via task.status().isDeletable()
 *  → RG-03 : GESTIONNAIRE ne peut supprimer QUE ses tâches
 *
 *  POURQUOI UNE COMMAND AVEC SEULEMENT 2 CHAMPS ?
 *  → Même si c'est minimal, l'utilisation d'une Command :
 *    - Uniformise l'API du Handler (toujours un record en entrée)
 *    - Facilite l'audit/logging (on peut logger la Command entière)
 *    - Permet d'ajouter un champ plus tard (ex: raison de suppression)
 *      sans changer la signature du Handler
 * ══════════════════════════════════════════════════════════════
 */
public record DeleteTaskCommand(

        /** ID de la tâche à supprimer — extrait du path param /tasks/{id}. */
        @NotNull TaskId taskId,

        /** ID de l'acteur — extrait du JWT. */
        @NotNull UserId actorId

) {}
