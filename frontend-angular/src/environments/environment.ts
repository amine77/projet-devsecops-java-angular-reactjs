/**
 * ══════════════════════════════════════════════════════════════
 *  ENVIRONNEMENT — Development (local)
 * ══════════════════════════════════════════════════════════════
 *
 *  Utilisé par : ng serve (défaut) et ng build --configuration development
 *
 *  KEYCLOAK en local :
 *  → docker-compose.yml expose Keycloak sur localhost:8180
 *  → Realm "todo" avec 4 utilisateurs (gestionnaire1, gestionnaire2, manager1, superadmin)
 *  → Client "todo-spa" configuré pour implicit/PKCE flow
 *  → Tous les mots de passe : "password"
 *
 *  DIFFÉRENCE AVEC environment.prod.ts :
 *  → apiUrl pointe vers localhost:8080 (Spring Boot local)
 *  → Keycloak pointe vers localhost:8180 (container local)
 *  → production: false → logs activés, source maps disponibles
 */
export const environment = {
  production: false,

  /** URL de base de l'API Spring Boot */
  apiUrl: 'http://localhost:8080/api',

  /** Configuration OIDC Keycloak (profil local) */
  oidc: {
    issuer: 'http://localhost:8180/realms/todo',
    clientId: 'todo-spa',
    redirectUri: window.location.origin + '/auth/callback',
    postLogoutRedirectUri: window.location.origin,
    scope: 'openid profile email',
    /** PKCE (Proof Key for Code Exchange) — plus sécurisé que l'implicit flow */
    responseType: 'code',
    showDebugInformation: true, // logs OIDC dans la console
  },
};
