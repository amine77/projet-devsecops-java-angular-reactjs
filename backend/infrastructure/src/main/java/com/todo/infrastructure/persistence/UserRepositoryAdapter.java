package com.todo.infrastructure.persistence;

import com.todo.domain.model.User;
import com.todo.domain.model.UserId;
import com.todo.domain.port.out.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * ══════════════════════════════════════════════════════════════
 *  ADAPTATEUR — UserRepositoryAdapter
 * ══════════════════════════════════════════════════════════════
 *
 *  Implémente UserRepository (port domaine) via Spring Data JPA.
 *
 *  FLUX D'AUTHENTIFICATION :
 *  1. Client envoie une requête avec un Bearer token JWT
 *  2. Spring Security valide la signature JWT (via JWKS de Keycloak/Cognito)
 *  3. JwtToUserConverter extrait le 'sub' claim du JWT
 *  4. findByKeycloakId(sub) → charge l'utilisateur local
 *  5. Le UserId local est injecté dans les Commands/Queries
 *
 *  PREMIÈRE CONNEXION :
 *  Si l'utilisateur n'existe pas en base (findByKeycloakId retourne empty),
 *  JwtToUserConverter peut créer l'entrée à la volée (lazy provisioning)
 *  ou retourner une erreur selon la politique de l'application.
 * ══════════════════════════════════════════════════════════════
 */
@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId userId) {
        return jpaRepository.findById(userId.value())
                .map(UserJpaEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByKeycloakId(String keycloakId) {
        return jpaRepository.findByKeycloakId(keycloakId)
                .map(UserJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(User user) {
        jpaRepository.save(UserJpaEntity.fromDomain(user));
    }
}
