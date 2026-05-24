import { Injectable, inject, signal, computed } from '@angular/core';
import { OAuthService, AuthConfig } from 'angular-oauth2-oidc';
import { Router } from '@angular/router';
import { environment } from '@env/environment';
import { CurrentUser, UserRole } from '@core/models/user.model';

/**
 * ══════════════════════════════════════════════════════════════
 *  SERVICE — AuthService
 * ══════════════════════════════════════════════════════════════
 *
 *  Gère l'authentification OIDC (OpenID Connect) avec Keycloak/Cognito.
 *
 *  BIBLIOTHÈQUE : angular-oauth2-oidc
 *  → Gère le flow Authorization Code + PKCE
 *  → PKCE (Proof Key for Code Exchange) : sécurise le flow pour les SPAs
 *    → Le client génère un code_verifier aléatoire
 *    → Envoie un code_challenge (hash SHA-256) à Keycloak
 *    → Keycloak vérifie que le code_verifier correspond au code_challenge
 *    → Évite les attaques CSRF sur le flow OAuth2
 *
 *  FLOW COMPLET :
 *  1. initCodeFlow()       : redirige vers Keycloak (login page)
 *  2. Keycloak authentifie l'utilisateur
 *  3. Redirect vers redirectUri avec code=XYZ
 *  4. angular-oauth2-oidc échange le code contre un access_token + id_token
 *  5. Les tokens sont stockés dans sessionStorage (configurable)
 *  6. currentUser est extrait des claims du id_token
 *
 *  ANGULAR SIGNALS (nouveauté Angular 16+) :
 *  → signal() : état réactif local (remplace BehaviorSubject pour les états simples)
 *  → computed() : valeur dérivée d'un ou plusieurs signals
 *  → Avantage vs Observable : syntaxe synchrone plus simple, pas de subscribe/unsubscribe
 *  → Plus performant : Angular détecte les changements sans zone.js (signal-based)
 *
 *  STANDALONE SERVICES (Angular 14+) :
 *  → providedIn: 'root' → singleton dans toute l'application
 *  → Pas besoin de déclarer dans un NgModule
 *
 *  @Injectable({ providedIn: 'root' }) :
 *  → Angular crée une seule instance pour toute l'app (pattern Singleton)
 *  → Détruit quand l'application se ferme
 * ══════════════════════════════════════════════════════════════
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  // Injection de dépendances avec la nouvelle syntaxe inject() (Angular 14+)
  // Avantage vs constructeur : fonctionne même hors d'un constructeur (ex: fonctions)
  private readonly oauthService = inject(OAuthService);
  private readonly router       = inject(Router);

  // ── Signals — état réactif ───────────────────────────────────

  /**
   * Signal writable : l'utilisateur connecté ou null si non authentifié.
   * Les composants lisent ce signal de manière réactive.
   * private(set) : lecture publique, écriture privée (encapsulation)
   */
  private readonly _currentUser = signal<CurrentUser | null>(null);

  /** Signal public en lecture seule */
  readonly currentUser = this._currentUser.asReadonly();

  /**
   * Computed signal : dérivé de _currentUser.
   * Angular recalcule automatiquement quand _currentUser change.
   * Pas de subscribe/unsubscribe — mémorisation automatique.
   */
  readonly isAuthenticated = computed(() => this._currentUser() !== null);
  readonly isGestionnaire  = computed(() => this._currentUser()?.role === 'GESTIONNAIRE');
  readonly isManager        = computed(() => this._currentUser()?.role === 'MANAGER');
  readonly isSuperAdmin     = computed(() => this._currentUser()?.role === 'SUPER_ADMINISTRATEUR');
  readonly canCreateTask    = computed(() =>
    this._currentUser()?.role === 'GESTIONNAIRE' ||
    this._currentUser()?.role === 'SUPER_ADMINISTRATEUR'
  );
  readonly canValidateTask  = computed(() =>
    this._currentUser()?.role === 'MANAGER' ||
    this._currentUser()?.role === 'SUPER_ADMINISTRATEUR'
  );

  constructor() {
    this.configureOAuth();
    this.loadUser();
  }

  // ── Initialisation ────────────────────────────────────────────

  /**
   * Configure angular-oauth2-oidc avec les paramètres de l'environnement.
   *
   * loadDiscoveryDocumentAndTryLogin() :
   * → Charge le "discovery document" OIDC (/.well-known/openid-configuration)
   * → Contient les endpoints : authorization, token, userinfo, jwks_uri...
   * → Puis tente un login silencieux si un code est présent dans l'URL
   *   (cas du retour depuis Keycloak après authentification)
   */
  async configureOAuth(): Promise<void> {
    const authConfig: AuthConfig = {
      issuer:              environment.oidc.issuer,
      clientId:            environment.oidc.clientId,
      redirectUri:         environment.oidc.redirectUri,
      postLogoutRedirectUri: environment.oidc.postLogoutRedirectUri,
      scope:               environment.oidc.scope,
      responseType:        environment.oidc.responseType,
      showDebugInformation: environment.oidc.showDebugInformation,
      // PKCE activé automatiquement quand responseType='code'
      useSilentRefresh:    false, // pas d'iframe de refresh (moins compatibles CSP stricts)
      sessionChecksEnabled:false,
    };

    this.oauthService.configure(authConfig);

    try {
      // Charge le discovery document PUIS traite le callback si présent
      await this.oauthService.loadDiscoveryDocumentAndTryLogin();
      this.loadUser();

      // Si l'utilisateur est connecté, démarrer le refresh automatique des tokens
      if (this.oauthService.hasValidAccessToken()) {
        this.oauthService.setupAutomaticSilentRefresh();
      }
    } catch (error) {
      console.error('Erreur OIDC :', error);
    }
  }

  /**
   * Déclenche le flow Authorization Code + PKCE.
   * Redirige l'utilisateur vers la page de login Keycloak.
   */
  login(): void {
    this.oauthService.initCodeFlow();
  }

  /**
   * Déconnexion :
   * 1. Révoque le token côté Keycloak (end_session_endpoint)
   * 2. Supprime les tokens locaux (sessionStorage)
   * 3. Redirige vers postLogoutRedirectUri (la page d'accueil)
   */
  logout(): void {
    this._currentUser.set(null);
    this.oauthService.logOut();
  }

  /**
   * Retourne l'access token JWT pour les requêtes HTTP.
   * Utilisé par JwtInterceptor.
   */
  getAccessToken(): string {
    return this.oauthService.getAccessToken();
  }

  // ── Helpers privés ────────────────────────────────────────────

  /**
   * Extrait les informations de l'utilisateur depuis les claims du JWT.
   *
   * STRUCTURE DES CLAIMS KEYCLOAK :
   * Le id_token contient les informations de profil.
   * Les rôles sont dans realm_access.roles (Keycloak)
   * ou cognito:groups (Cognito).
   */
  private loadUser(): void {
    if (!this.oauthService.hasValidAccessToken()) {
      this._currentUser.set(null);
      return;
    }

    const claims = this.oauthService.getIdentityClaims() as Record<string, unknown>;
    if (!claims) {
      this._currentUser.set(null);
      return;
    }

    // Extraire le rôle depuis realm_access.roles (Keycloak)
    // ou cognito:groups (Cognito)
    const role = this.extractRole(claims);
    if (!role) {
      console.warn('Aucun rôle métier trouvé dans le JWT');
      this._currentUser.set(null);
      return;
    }

    this._currentUser.set({
      sub:               claims['sub'] as string,
      preferredUsername: (claims['preferred_username'] ?? claims['cognito:username']) as string,
      email:             claims['email'] as string,
      firstName:         claims['given_name'] as string ?? '',
      lastName:          claims['family_name'] as string ?? '',
      role,
      teamId:            (claims['teamId'] ?? claims['custom:teamId']) as string ?? '',
      unitId:            (claims['unitId'] ?? claims['custom:unitId']) as string ?? '',
    });
  }

  /**
   * Extrait le rôle métier depuis les claims JWT.
   * Compatible Keycloak (realm_access.roles) et Cognito (cognito:groups).
   */
  private extractRole(claims: Record<string, unknown>): UserRole | null {
    const KNOWN_ROLES: UserRole[] = ['GESTIONNAIRE', 'MANAGER', 'SUPER_ADMINISTRATEUR'];

    // Keycloak : realm_access.roles
    const realmAccess = claims['realm_access'] as { roles?: string[] } | undefined;
    if (realmAccess?.roles) {
      const found = realmAccess.roles.find(r => KNOWN_ROLES.includes(r as UserRole));
      if (found) return found as UserRole;
    }

    // Cognito : cognito:groups
    const cognitoGroups = claims['cognito:groups'] as string[] | undefined;
    if (cognitoGroups) {
      const found = cognitoGroups.find(g => KNOWN_ROLES.includes(g as UserRole));
      if (found) return found as UserRole;
    }

    return null;
  }
}
