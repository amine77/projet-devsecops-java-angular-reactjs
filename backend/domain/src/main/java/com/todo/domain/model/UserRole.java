package com.todo.domain.model;

/**
 * ══════════════════════════════════════════════════════════════
 *  ENUM — UserRole (3 rôles, périmètres distincts)
 * ══════════════════════════════════════════════════════════════
 *
 *  Ces rôles correspondent aux claims JWT Keycloak/Cognito.
 *  Ils sont mappés par JwtAuthenticationConverter dans Spring Security.
 *
 *  Le domaine utilise ces rôles pour les vérifications de droits SANS
 *  dépendre de Spring Security (qui est dans la couche infrastructure).
 * ══════════════════════════════════════════════════════════════
 */
public enum UserRole {

    /**
     * Opérationnel — gère uniquement SES propres tâches.
     * Ne peut pas voir les tâches de ses collègues (RG-03).
     * Ne peut pas changer le statut d'une tâche (RG-08).
     */
    GESTIONNAIRE,

    /**
     * Responsable d'équipe — valide/rejette les tâches de SON équipe.
     * Ne peut pas créer de tâches (FUNCTIONAL-MODEL §4).
     * Ne peut pas voir les tâches d'autres équipes (RG-04).
     */
    MANAGER,

    /**
     * Administrateur global — accès illimité + administration.
     * Peut effectuer toutes les actions de Gestionnaire et Manager
     * sur n'importe quelle tâche de n'importe quelle équipe.
     */
    SUPER_ADMINISTRATEUR;

    /** @return true si ce rôle peut valider/rejeter/clôturer des tâches */
    public boolean canChangeTaskStatus() {
        // Seuls MANAGER et SUPER_ADMINISTRATEUR changent les statuts (RG-08)
        return this == MANAGER || this == SUPER_ADMINISTRATEUR;
    }

    /** @return true si ce rôle peut créer des tâches */
    public boolean canCreateTask() {
        // MANAGER ne crée pas de tâches (FUNCTIONAL-MODEL §4)
        return this == GESTIONNAIRE || this == SUPER_ADMINISTRATEUR;
    }

    /** @return true si ce rôle a un accès global (toutes équipes) */
    public boolean hasGlobalAccess() {
        return this == SUPER_ADMINISTRATEUR;
    }
}
