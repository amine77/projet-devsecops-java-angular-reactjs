package com.todo.application.command;

import com.todo.domain.model.TaskId;
import com.todo.domain.model.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * ══════════════════════════════════════════════════════════════
 *  COMMAND — RejectTaskCommand
 * ══════════════════════════════════════════════════════════════
 *
 *  Rejet d'une tâche : A_FAIRE → REJETEE
 *
 *  RÈGLE MÉTIER RG-06 :
 *  → Le motif de rejet est OBLIGATOIRE.
 *  → Deux niveaux de validation :
 *    1. @NotBlank ici (validation HTTP avant que la Command arrive au domaine)
 *    2. task.reject() dans l'agrégat vérifie aussi le motif (défense en profondeur)
 *       → RejectionReasonRequiredException si absent
 *
 *  POURQUOI DEUX NIVEAUX ?
 *  → Le premier niveau (Bean Validation) évite de charger la DB pour
 *    une requête manifestement invalide (fail fast, bonne pratique REST).
 *  → Le second niveau (domaine) garantit l'invariant même si la Command
 *    est construite programmatiquement (hors contexte HTTP).
 *    Exemple : un consumer Kafka qui réinjecte une Command depuis un topic.
 *
 *  PERSISTANCE DU MOTIF :
 *  → Le motif est stocké dans la colonne rejection_reason de la table tasks
 *  → Il est inclus dans TaskRejected event et envoyé par email au GESTIONNAIRE
 * ══════════════════════════════════════════════════════════════
 */
public record RejectTaskCommand(

        @NotNull TaskId taskId,

        /**
         * Motif de rejet — obligatoire (RG-06).
         * Le GESTIONNAIRE voit ce motif dans son interface pour corriger sa tâche.
         * Max 500 chars pour éviter les essays mais permettre une explication utile.
         */
        @NotBlank(message = "Le motif de rejet est obligatoire (RG-06)")
        @Size(max = 500, message = "Le motif de rejet ne peut pas dépasser 500 caractères")
        String rejectionReason,

        @NotNull UserId actorId

) {}
