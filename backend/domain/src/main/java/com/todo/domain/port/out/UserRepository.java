package com.todo.domain.port.out;

import com.todo.domain.model.TeamId;
import com.todo.domain.model.User;
import com.todo.domain.model.UserId;
import com.todo.domain.model.UserRole;

import java.util.List;
import java.util.Optional;

/**
 * PORT SORTANT — UserRepository
 *
 * Les utilisateurs sont synchronisés depuis Keycloak/Cognito.
 * L'adaptateur OidcUserSynchronizer (infrastructure) crée/met à jour
 * les utilisateurs en base lors de chaque connexion JWT.
 */
public interface UserRepository {

    void save(User user);

    Optional<User> findById(UserId id);

    /**
     * Recherche par keycloakId (claim "sub" du JWT).
     * Utilisé à chaque requête authentifiée pour charger l'utilisateur courant.
     */
    Optional<User> findByKeycloakId(String keycloakId);

    Optional<User> findByUsername(String username);

    /** Membres d'une équipe — pour les statistiques Manager. */
    List<User> findByTeam(TeamId teamId);

    /** Tous les utilisateurs d'un rôle — pour l'administration Super-Admin. */
    List<User> findByRole(UserRole role);

    List<User> findAll();
}
