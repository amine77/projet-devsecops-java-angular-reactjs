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
 *  → Exemple : "7f432e8b-1234-5678-abcd-ef0123456789"
 *
 *  POURQUOI DUPLIQUER LES UTILISATEURS ?
 *  → On a besoin des User pour les vérifications métier (rôle, équipe)
 *  → On ne veut pas appeler Keycloak à chaque requête (latence + dépendance)
 *  → Solution : stocker la "projection" métier de l'utilisateur en local
 *  → La synchronisation se fait via un webhook Keycloak (Phase 4) ou
 *    à la première connexion (JwtToUserConverter)
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

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 150, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Column(name = "team_id", nullable = false, columnDefinition = "uuid")
    private UUID teamId;

    @Column(name = "unit_id", nullable = false, columnDefinition = "uuid")
    private UUID unitId;

    protected UserJpaEntity() {}

    /** Conversion JPA → domaine */
    public User toDomain() {
        return new User(
                new UserId(id),
                keycloakId,
                firstName,
                lastName,
                email,
                role,
                new TeamId(teamId),
                new UnitId(unitId)
        );
    }

    /** Conversion domaine → JPA */
    public static UserJpaEntity fromDomain(User user) {
        UserJpaEntity e = new UserJpaEntity();
        e.id         = user.id().value();
        e.keycloakId = user.keycloakId();
        e.firstName  = user.firstName();
        e.lastName   = user.lastName();
        e.email      = user.email();
        e.role       = user.role();
        e.teamId     = user.teamId().value();
        e.unitId     = user.unitId().value();
        return e;
    }
}
