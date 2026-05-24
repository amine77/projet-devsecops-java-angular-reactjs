package com.todo.infrastructure.config;

import com.todo.domain.model.UserId;
import com.todo.domain.port.out.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ══════════════════════════════════════════════════════════════
 *  COMPOSANT SÉCURITÉ — JwtToUserConverter
 * ══════════════════════════════════════════════════════════════
 *
 *  RÔLE : Fait le pont entre le JWT Keycloak/Cognito et le modèle
 *  utilisateur local (User entity en base PostgreSQL).
 *
 *  CE QUE FAIT CE COMPOSANT :
 *  1. Extrait le claim 'sub' du JWT (= keycloakId)
 *  2. Charge l'utilisateur local via UserRepository
 *  3. Extrait les rôles Keycloak du JWT et les convertit en GrantedAuthority
 *  4. Fournit une méthode utilitaire getCurrentUserId() pour les contrôleurs
 *
 *  STRUCTURE DU JWT KEYCLOAK :
 *  ┌─────────────────────────────────────────────────────────┐
 *  │  {                                                      │
 *  │    "sub": "7f432e8b-1234-5678-abcd-ef0123456789",  ←── keycloakId │
 *  │    "preferred_username": "gestionnaire1",               │
 *  │    "email": "gest1@todo.com",                           │
 *  │    "realm_access": {                                    │
 *  │      "roles": ["GESTIONNAIRE", "offline_access"]  ←── rôles │
 *  │    },                                                   │
 *  │    "teamId": "11111111-1111-1111-1111-111111111111", ←── claim custom │
 *  │    "unitId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"    │
 *  │  }                                                      │
 *  └─────────────────────────────────────────────────────────┘
 *
 *  STRUCTURE DU JWT COGNITO :
 *  ┌─────────────────────────────────────────────────────────┐
 *  │  {                                                      │
 *  │    "sub": "7f432e8b-...",                               │
 *  │    "cognito:username": "gestionnaire1",                 │
 *  │    "cognito:groups": ["GESTIONNAIRE"],  ←── rôles       │
 *  │    "custom:teamId": "11111111-..."                      │
 *  │  }                                                      │
 *  └─────────────────────────────────────────────────────────┘
 *
 *  DÉTECTION AUTOMATIQUE DU PROFIL (Keycloak vs Cognito) :
 *  → On détecte le type de JWT par la présence de "realm_access"
 *    (Keycloak) ou "cognito:groups" (Cognito)
 * ══════════════════════════════════════════════════════════════
 */
@Component
public class JwtToUserConverter {

    private static final Logger log = LoggerFactory.getLogger(JwtToUserConverter.class);

    private final UserRepository userRepository;

    public JwtToUserConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Extrait l'UserId local à partir du JWT de la requête courante.
     *
     * USAGE DANS LES CONTRÔLEURS :
     * Au lieu d'injecter @AuthenticationPrincipal partout,
     * le contrôleur appelle getCurrentUserId() qui fait le lookup.
     *
     * @param jwt le token JWT validé par Spring Security
     * @return UserId de l'utilisateur en base, ou lève une exception si introuvable
     */
    public UserId extractUserId(Jwt jwt) {
        // 1. Extraire le 'sub' claim (identifiant unique OpenID Connect)
        String keycloakId = jwt.getSubject(); // claim "sub"

        // 2. Chercher l'utilisateur local par son keycloakId
        return userRepository.findByKeycloakId(keycloakId)
                .map(user -> user.id())
                .orElseThrow(() -> {
                    log.error("Utilisateur non trouvé pour keycloakId={}", keycloakId);
                    // 403 Forbidden — l'utilisateur est authentifié mais pas connu en base
                    // (peut arriver si le compte Keycloak existe mais pas encore provisionné)
                    return new org.springframework.security.access.AccessDeniedException(
                        "Utilisateur non provisionné : " + keycloakId
                    );
                });
    }

    /**
     * Extrait les GrantedAuthority depuis le JWT Keycloak ou Cognito.
     *
     * KEYCLOAK : claim "realm_access.roles" (liste de strings)
     * COGNITO  : claim "cognito:groups" (liste de strings)
     *
     * Spring Security préfixe les rôles avec "ROLE_" pour @PreAuthorize("hasRole('MANAGER')")
     * → "MANAGER" dans JWT → "ROLE_MANAGER" comme GrantedAuthority
     *
     * @param jwt le token JWT
     * @return collection de GrantedAuthority pour Spring Security
     */
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        // ── Keycloak : realm_access.roles ──────────────────────────
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            List<String> roles = (List<String>) realmAccess.get("roles");
            roles.stream()
                    // Filtrer les rôles Keycloak internes (offline_access, uma_authorization...)
                    .filter(role -> role.equals("GESTIONNAIRE")
                            || role.equals("MANAGER")
                            || role.equals("SUPER_ADMINISTRATEUR"))
                    // Spring Security préfixe avec ROLE_ pour hasRole()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .forEach(authorities::add);
        }

        // ── Cognito : cognito:groups ────────────────────────────────
        List<String> cognitoGroups = jwt.getClaim("cognito:groups");
        if (cognitoGroups != null) {
            cognitoGroups.stream()
                    .filter(g -> g.equals("GESTIONNAIRE")
                            || g.equals("MANAGER")
                            || g.equals("SUPER_ADMINISTRATEUR"))
                    .map(g -> new SimpleGrantedAuthority("ROLE_" + g))
                    .forEach(authorities::add);
        }

        if (authorities.isEmpty()) {
            log.warn("Aucun rôle métier trouvé dans le JWT pour sub={}", jwt.getSubject());
        }

        return authorities;
    }
}
