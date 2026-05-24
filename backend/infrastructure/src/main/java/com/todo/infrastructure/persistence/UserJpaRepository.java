package com.todo.infrastructure.persistence;

import com.todo.domain.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ══════════════════════════════════════════════════════════════
 *  SPRING DATA JPA — UserJpaRepository
 * ══════════════════════════════════════════════════════════════
 *
 *  Accès aux utilisateurs en base.
 *
 *  CONVENTION DE NOMMAGE SPRING DATA :
 *  → findBy{Champ}    : génère automatiquement un SELECT WHERE champ = ?
 *  → findAll()        : hérité de JpaRepository → SELECT * FROM users
 *
 *  PERFORMANCES :
 *  → keycloak_id : index unique (V1__init_schema.sql) → O(log n)
 *  → email       : index unique → O(log n)
 *  → username    : index unique → O(log n)
 *  → team_id     : index non-unique → O(log n) + résultats
 *  → role        : pas d'index (faible cardinalité) → seq scan acceptable
 * ══════════════════════════════════════════════════════════════
 */
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    /** Trouve un utilisateur par son sub JWT (claim "sub"). */
    Optional<UserJpaEntity> findByKeycloakId(String keycloakId);

    /** Lookup par username — pour l'administration et la recherche. */
    Optional<UserJpaEntity> findByUsername(String username);

    /** Lookup par email — pour les notifications et l'administration. */
    Optional<UserJpaEntity> findByEmail(String email);

    /**
     * Membres d'une équipe.
     * Spring Data génère : SELECT * FROM users WHERE team_id = ?
     * Utilisé par getTeamTasks → statistiques Manager.
     */
    List<UserJpaEntity> findByTeamId(UUID teamId);

    /**
     * Utilisateurs par rôle — pour l'administration Super-Admin.
     * Spring Data génère : SELECT * FROM users WHERE role = ?
     */
    List<UserJpaEntity> findByRole(UserRole role);

    // findAll() est fourni par JpaRepository<UserJpaEntity, UUID>
}
