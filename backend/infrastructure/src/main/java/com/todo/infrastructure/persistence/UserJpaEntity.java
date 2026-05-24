package com.todo.infrastructure.persistence;

import com.todo.domain.model.TeamId;
import com.todo.domain.model.UnitId;
import com.todo.domain.model.User;
import com.todo.domain.model.UserId;
import com.todo.domain.model.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * ══════════════════════════════════════════════════════════════
 *  JPA ENTITY — UserJpaEntity
 * ══════════════════════════════════════════════════════════════
 *
 *  Représentation persistée de l'utilisateur.
 *
 *  RELATION AVEC KEYCLOAK / COGNITO :
 *  → L'utilisateur est créé dans Keycloak/Cognito (source de vérité IAM)
 *  → A la première connexion, un enregistrement est créé ici avec le keycloakId
 *  → Le keycloakId = sub du JWT (claim standard OpenID Connect)
 *
 *  CHAMPS OPTIONNELS (nullable) :
 *  → teamId et unitId sont null pour le SUPER_ADMINISTRATEUR (accès global)
 *  → active = false pour les comptes désactivés (soft-delete)
 * ══════════════════════════════════════════════════════════════
 */
@Entity
@Table(name = "users", schema = "public")
public class UserJpaEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    /**
     * Sub du JWT Keycloak/Cognito.
     * Unique — utilisé pour retrouver l'utilisateur local à partir du token.
     * Index unique créé dans V1__init_schema.sql : idx_users_keycloak_id
     */
    @Column(name = "keycloak_id", nullable = false, unique = true, length = 100)
    private String keycloakId;

    /** Nom de connexion unique — login de l'utilisateur. */
    @Column(nullable = false, length = 100, unique = true)
    private String username;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 150, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    /**
     * Nullable : le SUPER_ADMINISTRATEUR n'a pas d'équipe (accès global).
     * Les GESTIONNAIRE et MANAGER ont toujours une équipe (RG-01/RG-02).
     */
    @Column(name = "team_id", columnDefinition = "uuid")
    private UUID teamId;

    /** Nullable : idem que teamId pour le SUPER_ADMINISTRATEUR. */
    @Column(name = "unit_id", columnDefinition = "uuid")
    private UUID unitId;

    /** false = compte désactivé sans suppression (conservation de l'historique). */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserJpaEntity() {}

    // ══════════════════════════════════════════════════════════
    //  CONVERSIONS
    // ══════════════════════════════════════════════════════════

    /**
     * Conversion JPA Entity → Domain Record.
     *
     * ORDRE DES ARGUMENTS (critique — correspond au record User) :
     * UserId, keycloakId, username, email, firstName, lastName,
     * UserRole, TeamId, UnitId, boolean active, Instant createdAt, Instant updatedAt
     */
    public User toDomain() {
        var now = Instant.now();
        return new User(
                new UserId(id),
                keycloakId,
                username,
                email,
                firstName,
                lastName,
                role,
                // teamId null pour SUPER_ADMINISTRATEUR
                teamId != null ? new TeamId(teamId) : null,
                unitId != null ? new UnitId(unitId) : null,
                active,
                // Valeurs par défaut si la colonne n'était pas encore remplie (migration)
                createdAt != null ? createdAt : now,
                updatedAt != null ? updatedAt : now
        );
    }

    /** Conversion Domain Record → JPA Entity (pour save). */
    public static UserJpaEntity fromDomain(User user) {
        UserJpaEntity e = new UserJpaEntity();
        e.id         = user.id().value();
        e.keycloakId = user.keycloakId();
        e.username   = user.username();
        e.firstName  = user.firstName();
        e.lastName   = user.lastName();
        e.email      = user.email();
        e.role       = user.role();
        // null pour SUPER_ADMINISTRATEUR
        e.teamId     = user.teamId()  != null ? user.teamId().value()  : null;
        e.unitId     = user.unitId()  != null ? user.unitId().value()  : null;
        e.active     = user.active();
        e.createdAt  = user.createdAt();
        e.updatedAt  = user.updatedAt();
        return e;
    }
}
