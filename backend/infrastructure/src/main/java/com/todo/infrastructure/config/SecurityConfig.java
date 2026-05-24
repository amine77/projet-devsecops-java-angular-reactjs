package com.todo.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════
 *  CONFIGURATION SÉCURITÉ — SecurityConfig
 * ══════════════════════════════════════════════════════════════
 *
 *  Configure Spring Security pour :
 *  1. OAuth2 Resource Server (validation JWT)
 *  2. CORS (Angular/React en développement)
 *  3. CSRF désactivé (API stateless — pas de sessions)
 *  4. Autorisation par rôle (RBAC)
 *
 *  ────────────────────────────────────────────────────────────
 *  FLUX D'AUTHENTIFICATION JWT :
 *  ────────────────────────────────────────────────────────────
 *
 *  Client (Angular/React)
 *      │  1. POST /auth/login → Keycloak/Cognito
 *      │  2. Keycloak retourne un access_token JWT signé
 *      ▼
 *  Spring Boot (ce service)
 *      │  3. Client envoie : Authorization: Bearer <access_token>
 *      │  4. Spring Security intercepte la requête
 *      │  5. JwtDecoder valide la signature (via JWKS endpoint de Keycloak)
 *      │     → Keycloak: http://localhost:8180/realms/todo/protocol/openid-connect/certs
 *      │     → Cognito: https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json
 *      │  6. Si valide → JwtAuthenticationConverter extrait les rôles
 *      │  7. SecurityContext rempli avec Authentication
 *      │  8. JwtToUserConverter mappe le 'sub' → UserId local
 *      ▼
 *  TaskController (endpoint protégé)
 *
 *  ────────────────────────────────────────────────────────────
 *  STATELESS (SessionCreationPolicy.STATELESS) :
 *  → Aucune session HTTP côté serveur
 *  → Chaque requête porte son propre JWT
 *  → Horizontal scaling sans sticky sessions
 *  → Compatible avec Virtual Threads (Java 21)
 *
 *  @EnableMethodSecurity :
 *  → Active @PreAuthorize et @PostAuthorize sur les méthodes
 *  → Permet un RBAC fin au niveau méthode (ex: @PreAuthorize("hasRole('MANAGER')"))
 *  → Alternative au RBAC dans SecurityFilterChain (plus granulaire)
 * ══════════════════════════════════════════════════════════════
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * Configuration principale de la chaîne de filtres Spring Security.
     *
     * ORDRE DES RÈGLES : du plus spécifique au plus général.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // ── CSRF : désactivé pour les APIs REST stateless ──────────
                // CSRF est utile pour les applications avec sessions et cookies.
                // Notre API utilise des JWT dans le header Authorization → pas de CSRF.
                .csrf(csrf -> csrf.disable())

                // ── CORS : autoriser les frontends locaux ───────────────────
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // ── SESSION : stateless (pas de HttpSession côté serveur) ───
                .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // ── AUTORISATION PAR ENDPOINT ────────────────────────────
                .authorizeHttpRequests(auth -> auth

                    // ── Endpoints publics (pas de JWT requis) ──────────────
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/actuator/prometheus").permitAll() // Prometheus scrape
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll() // Swagger UI

                    // ── Endpoints de lecture : tous les rôles authentifiés ─
                    .requestMatchers(HttpMethod.GET, "/api/tasks/**").authenticated()

                    // ── Création : GESTIONNAIRE ou SUPER_ADMIN ─────────────
                    // Note : on vérifie aussi dans le domaine (defense in depth)
                    .requestMatchers(HttpMethod.POST, "/api/tasks").hasAnyRole(
                        "GESTIONNAIRE", "SUPER_ADMINISTRATEUR"
                    )

                    // ── Modification : GESTIONNAIRE ou SUPER_ADMIN ──────────
                    .requestMatchers(HttpMethod.PUT, "/api/tasks/{id}").hasAnyRole(
                        "GESTIONNAIRE", "SUPER_ADMINISTRATEUR"
                    )

                    // ── Actions Manager : MANAGER ou SUPER_ADMIN ───────────
                    .requestMatchers(HttpMethod.PUT,
                        "/api/tasks/*/validate",
                        "/api/tasks/*/reject",
                        "/api/tasks/*/done"
                    ).hasAnyRole("MANAGER", "SUPER_ADMINISTRATEUR")

                    // ── Suppression : GESTIONNAIRE ou SUPER_ADMIN ──────────
                    .requestMatchers(HttpMethod.DELETE, "/api/tasks/{id}").hasAnyRole(
                        "GESTIONNAIRE", "SUPER_ADMINISTRATEUR"
                    )

                    // ── Toute autre requête → authentification obligatoire ──
                    .anyRequest().authenticated()
                )

                // ── OAUTH2 RESOURCE SERVER (validation JWT) ────────────────
                // Spring Security valide automatiquement le JWT via le JWKS endpoint
                // configuré dans application.yml :
                //   spring.security.oauth2.resourceserver.jwt.issuer-uri
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())
                    )
                )

                .build();
    }

    /**
     * Convertisseur JWT → Authentication Spring Security.
     *
     * PAR DÉFAUT, Spring Security extrait les rôles depuis le claim "scope" ou "scp".
     * Keycloak et Cognito utilisent des structures différentes :
     *
     * KEYCLOAK JWT :
     *   "realm_access": { "roles": ["GESTIONNAIRE", "offline_access"] }
     *   "resource_access": { "todo-api": { "roles": ["GESTIONNAIRE"] } }
     *
     * COGNITO JWT :
     *   "cognito:groups": ["GESTIONNAIRE"]
     *
     * → JwtToUserConverter (déclaré séparément) gère le mapping complet
     * → Ici, on configure juste le claim source des rôles
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        // Le mapping des rôles Keycloak → GrantedAuthority est dans JwtToUserConverter
        // Pour la configuration Spring Security (@PreAuthorize), on préfixe avec ROLE_
        // Ex: "GESTIONNAIRE" dans Keycloak → GrantedAuthority "ROLE_GESTIONNAIRE"
        return converter;
    }

    /**
     * Configuration CORS pour les frontends en développement.
     *
     * CORS (Cross-Origin Resource Sharing) :
     * → Le navigateur bloque les requêtes HTTP d'une origine vers une autre
     *   par défaut (sécurité du navigateur).
     * → Ex: Angular sur localhost:4200 → Spring Boot sur localhost:8080 → BLOQUÉ
     * → CORS permet au serveur de dire "j'accepte les requêtes de ces origines"
     *
     * ORIGINES AUTORISÉES :
     * → localhost:4200 : Angular dev server
     * → localhost:3000 : React dev server (Vite)
     * → localhost:3001 : React dev server alternatif
     *
     * EN PRODUCTION :
     * → Remplacer par les domaines réels (configurés en variable d'environnement)
     * → Ne JAMAIS utiliser "*" en production si le cookie/JWT est dans le header
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Origines autorisées (frontends locaux)
        config.setAllowedOrigins(List.of(
            "http://localhost:4200",   // Angular
            "http://localhost:3000",   // React
            "http://localhost:3001"    // React (Vite alt)
        ));

        // Méthodes HTTP autorisées
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        // Headers autorisés (Authorization pour le JWT)
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));

        // Exposer le header Location pour les 201 Created (POST /tasks → Location: /tasks/{id})
        config.setExposedHeaders(List.of("Location"));

        // Autoriser les credentials (pour les cookies si besoin, non utilisés ici)
        config.setAllowCredentials(true);

        // Durée de cache du preflight (OPTIONS) — 1 heure
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
