package com.todo.application.command;

import com.todo.domain.model.Priority;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.UserId;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * ══════════════════════════════════════════════════════════════
 *  COMMAND — UpdateTaskCommand
 * ══════════════════════════════════════════════════════════════
 *
 *  Modification d'une tâche existante.
 *
 *  RÈGLES MÉTIER RAPPELÉES :
 *  → RG-05 : modification possible uniquement en A_FAIRE ou REJETEE
 *  → RG-03 : GESTIONNAIRE ne peut modifier QUE ses propres tâches
 *  → RG-07 : si la tâche est REJETEE, la modification la remet en A_FAIRE
 *
 *  Ces règles sont enforced dans TaskDomainService.updateTask()
 *  et Task.update() — PAS ici. La Command est juste le vecteur de données.
 *
 *  DIFFÉRENCE AVEC CreateTaskCommand :
 *  → On inclut taskId (la tâche cible)
 *  → L'actorId est toujours extrait du JWT (jamais du client)
 * ══════════════════════════════════════════════════════════════
 */
public record UpdateTaskCommand(

        /** ID de la tâche à modifier — extrait du path param REST (/tasks/{id}). */
        @NotNull
        TaskId taskId,

        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200, message = "Le titre ne peut pas dépasser 200 caractères")
        String title,

        @Size(max = 2000, message = "La description ne peut pas dépasser 2000 caractères")
        String description,

        @NotNull(message = "La priorité est obligatoire")
        Priority priority,

        @FutureOrPresent(message = "La date d'échéance doit être dans le futur ou aujourd'hui")
        LocalDate dueDate,

        /** ID de l'acteur — extrait du JWT, jamais du body HTTP. */
        @NotNull
        UserId actorId

) {}
