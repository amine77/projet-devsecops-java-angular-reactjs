package com.todo.domain.model;

/**
 * ══════════════════════════════════════════════════════════════
 *  ENUM — TaskStatus (cycle de vie d'une tâche)
 * ══════════════════════════════════════════════════════════════
 *
 *  Les transitions autorisées sont documentées ici et ENFORCED
 *  dans TaskDomainService. Le domaine est la source de vérité.
 *
 *  Diagramme de transitions (voir FUNCTIONAL-MODEL.md §3) :
 *
 *    Gestionnaire crée
 *         │
 *         ▼
 *    ┌─────────┐
 *    │ A_FAIRE │ ◄──────────────────────── (après modification par Gestionnaire)
 *    └────┬────┘
 *         │
 *    Manager décide :
 *         ├──► valider  ──► VALIDEE ──► Done ──► DONE
 *         ├──► rejeter  ──► REJETEE ──► (Gestionnaire modifie) ──► A_FAIRE
 *         └──► done     ──────────────────────────────────────► DONE
 *
 *  Règles clés :
 *  - Seul le Manager/Super-Admin change le statut (RG-08)
 *  - DONE est terminal : lecture seule pour tous (RG-09)
 *  - REJETEE nécessite un motif obligatoire (RG-06)
 * ══════════════════════════════════════════════════════════════
 */
public enum TaskStatus {

    /**
     * État initial — créée par le Gestionnaire.
     * Actions possibles : modifier, supprimer (Gestionnaire), valider/rejeter/done (Manager).
     */
    A_FAIRE,

    /**
     * Approuvée par le Manager.
     * Action possible : done (Manager). Plus modifiable par le Gestionnaire.
     */
    VALIDEE,

    /**
     * Refusée par le Manager avec un motif obligatoire.
     * Le Gestionnaire peut modifier la tâche → elle repasse automatiquement en A_FAIRE.
     * Note : une tâche REJETEE NE PEUT PAS être supprimée (RG-05).
     */
    REJETEE,

    /**
     * État terminal — tâche complètement terminée.
     * LECTURE SEULE pour tous les rôles (RG-09).
     * La tâche est archivée par la Lambda TaskArchiver après 30 jours.
     */
    DONE;

    /**
     * Vérifie si une transition vers le statut cible est valide.
     *
     * Centralisé ici plutôt que dans le service : le statut lui-même
     * connaît ses transitions valides (cohésion).
     *
     * @param target le statut vers lequel on veut transitionner
     * @return true si la transition est autorisée
     */
    public boolean canTransitionTo(TaskStatus target) {
        return switch (this) {
            // A_FAIRE peut aller vers : VALIDEE, REJETEE, DONE
            case A_FAIRE  -> target == VALIDEE || target == REJETEE || target == DONE;
            // VALIDEE ne peut aller que vers DONE
            case VALIDEE  -> target == DONE;
            // REJETEE ne peut aller que vers A_FAIRE (après modification)
            case REJETEE  -> target == A_FAIRE;
            // DONE est terminal — aucune transition possible
            case DONE     -> false;
        };
    }

    /** @return true si la tâche est dans un état modifiable par le Gestionnaire */
    public boolean isEditableByGestionnaire() {
        // RG-05 : Gestionnaire peut modifier A_FAIRE et REJETEE
        return this == A_FAIRE || this == REJETEE;
    }

    /** @return true si la tâche peut être supprimée */
    public boolean isDeletable() {
        // RG-05 : suppression uniquement en A_FAIRE
        return this == A_FAIRE;
    }

    /** @return true si la tâche est dans un état final (lecture seule) */
    public boolean isTerminal() {
        return this == DONE;
    }
}
