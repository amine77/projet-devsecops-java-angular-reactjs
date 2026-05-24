package com.todo.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * ══════════════════════════════════════════════════════════════
 *  SPRING DATA JPA — UserJpaRepository
 * ══════════════════════════════════════════════════════════════
 *
 *  Accès aux utilisateurs en base.
 *
 *  findByKeycloakId :
 *  → Méthode clé pour l'authentification
 *  → Appelée à chaque requête par JwtToUserConverter
 *  → L'index unique sur keycloak_id (V1__init_schema.sql) garantit
 *    une performance en O(1) (lookup par index unique)
 * ══════════════════════════════════════════════════════════════
 */
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {

    /** Trouve un utilisateur par son sub JWT. L'index unique rend ce lookup très rapide. */
    Optional<UserJpaEntity> findByKeycloakId(String keycloakId);

    /** Lookup par email — utile pour les APIs d'administration. */
    Optional<UserJpaEntity> findByEmail(String email);
}
