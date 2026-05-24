package com.todo.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * ══════════════════════════════════════════════════════════════
 *  ENTITY — User (Utilisateur de l'application)
 * ══════════════════════════════════════════════════════════════
 *
 *  L'utilisateur est synchronisé depuis Keycloak/Cognito.
 *  Son identifiant primaire dans notre domaine est son UserId,
 *  mais on stocke aussi le keycloakId (claim "sub" du JWT)
 *  pour faire la correspondance lors de l'authentification.
 *
 *  Note : User est une Entity (a une identité), pas un Value Object.
 *  Deux User avec le même username mais des IDs différents
 *  ne sont PAS le même utilisateur.
 * ══════════════════════════════════════════════════════════════
 */
public record User(

        /** ID interne (UUID généré par notre application). */
        UserId id,

        /**
         * ID Keycloak/Cognito — correspond au claim "sub" du JWT.
         * Utilisé pour retrouver l'utilisateur à partir du token JWT
         * dans OidcUserSynchronizer (couche infrastructure).
         */
        String keycloakId,

        /** Nom d'utilisateur unique (login). */
        String username,

        /** Email unique — utilisé pour les notifications SES. */
        String email,

        String firstName,
        String lastName,

        /** Rôle dans l'application — mappé depuis les realm roles Keycloak. */
        UserRole role,

        /**
         * Équipe du Gestionnaire/Manager.
         * null pour le SUPER_ADMINISTRATEUR (accès global, pas d'équipe).
         */
        TeamId teamId,

        /**
         * Unité de gestion.
         * null pour le SUPER_ADMINISTRATEUR.
         */
        UnitId unitId,

        /** false = compte désactivé (sans suppression — l'historique est conservé). */
        boolean active,

        Instant createdAt,
        Instant updatedAt

) {

    public User {
        Objects.requireNonNull(id,         "id est obligatoire");
        Objects.requireNonNull(keycloakId, "keycloakId est obligatoire");
        Objects.requireNonNull(username,   "username est obligatoire");
        Objects.requireNonNull(email,      "email est obligatoire");
        Objects.requireNonNull(role,       "role est obligatoire");

        if (username.isBlank()) {
            throw new IllegalArgumentException("username ne peut pas être vide");
        }

        // Un Gestionnaire/Manager doit appartenir à une équipe (RG-01, RG-02)
        // Le SUPER_ADMINISTRATEUR n'appartient à aucune équipe
        if ((role == UserRole.GESTIONNAIRE || role == UserRole.MANAGER) && teamId == null) {
            throw new IllegalArgumentException(
                "Un %s doit appartenir à une équipe (RG-01/RG-02)".formatted(role)
            );
        }
    }

    // ── Factory Methods ─────────────────────────────────────────

    /**
     * Crée un Gestionnaire.
     * RG-01 : un Gestionnaire appartient à une et une seule unité.
     */
    public static User createGestionnaire(
            String keycloakId, String username, String email,
            String firstName, String lastName,
            TeamId teamId, UnitId unitId
    ) {
        var now = Instant.now();
        return new User(
                UserId.generate(), keycloakId, username, email,
                firstName, lastName,
                UserRole.GESTIONNAIRE, teamId, unitId,
                true, now, now
        );
    }

    /**
     * Crée un Manager.
     * RG-02 : un Manager est responsable d'une et une seule équipe.
     */
    public static User createManager(
            String keycloakId, String username, String email,
            String firstName, String lastName,
            TeamId teamId, UnitId unitId
    ) {
        var now = Instant.now();
        return new User(
                UserId.generate(), keycloakId, username, email,
                firstName, lastName,
                UserRole.MANAGER, teamId, unitId,
                true, now, now
        );
    }

    /** Crée un Super-Administrateur (sans équipe ni unité). */
    public static User createSuperAdmin(
            String keycloakId, String username, String email,
            String firstName, String lastName
    ) {
        var now = Instant.now();
        return new User(
                UserId.generate(), keycloakId, username, email,
                firstName, lastName,
                UserRole.SUPER_ADMINISTRATEUR, null, null,
                true, now, now
        );
    }

    // ── Query Methods ────────────────────────────────────────────

    /** @return true si cet utilisateur peut agir sur les tâches de l'équipe donnée */
    public boolean canActOnTeam(TeamId targetTeamId) {
        // Super-Admin : accès global (RG implicite)
        if (role.hasGlobalAccess()) return true;
        // Manager/Gestionnaire : seulement leur propre équipe
        return targetTeamId.equals(this.teamId);
    }

    /** @return Nom complet formaté pour les notifications */
    public String fullName() {
        if (firstName == null && lastName == null) return username;
        if (firstName == null) return lastName;
        if (lastName == null) return firstName;
        return firstName + " " + lastName;
    }
}
