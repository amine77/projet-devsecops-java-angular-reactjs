import React, { createContext, useContext, useEffect, useMemo } from 'react';
import { useAuth } from 'react-oidc-context';
import { apiClient } from '@api/task.api';
import type { UserRole } from '@types/task.types';

/**
 * ══════════════════════════════════════════════════════════════
 *  CONTEXT D'AUTHENTIFICATION — AuthContext.tsx (React)
 * ══════════════════════════════════════════════════════════════
 *
 *  Fournit les informations de l'utilisateur connecté à toute
 *  l'application via React Context + hook personnalisé.
 *
 *  DIFFÉRENCE AVEC ANGULAR :
 *  → Angular  : AuthService (@Injectable) + Signals
 *  → React    : Context API + useAuth() de react-oidc-context
 *
 *  REACT-OIDC-CONTEXT :
 *  → Wrapper React autour de oidc-client-ts
 *  → Gère le flux PKCE (Proof Key for Code Exchange)
 *  → Stocke les tokens en sessionStorage (configurable)
 *  → Rafraîchit automatiquement les tokens (silent renew)
 *
 *  INJECTION DU TOKEN JWT :
 *  → L'intercepteur Axios est configuré ici via useEffect
 *  → Quand user.access_token change, on met à jour l'intercepteur
 *  → Équivalent de jwtInterceptor.ts côté Angular
 */

// ── Type de l'utilisateur connecté ─────────────────────────────

export interface CurrentUser {
  readonly id: string;        // keycloakId = sub claim JWT
  readonly email: string;
  readonly name: string;
  readonly role: UserRole;
}

// ── Valeur exposée par le Context ───────────────────────────────

interface AuthContextValue {
  /** Utilisateur connecté ou null si non authentifié */
  currentUser: CurrentUser | null;
  /** true pendant le chargement initial de l'authentification */
  isLoading: boolean;
  /** true si l'utilisateur est authentifié */
  isAuthenticated: boolean;

  // Rôles calculés — élimine les comparaisons de string dans les composants
  isGestionnaire: boolean;
  isManager: boolean;
  isSuperAdmin: boolean;

  // Permissions métier — miroir de UserRole.can*() côté Java
  canCreateTask: boolean;
  canValidateTask: boolean;
  canManageUsers: boolean;

  /** Initie le flux PKCE vers Keycloak */
  login: () => void;
  /** Déconnexion + suppression des tokens */
  logout: () => void;
}

// ── Création du Context ─────────────────────────────────────────

const AuthContext = createContext<AuthContextValue | null>(null);

// ── Provider ────────────────────────────────────────────────────

interface AuthProviderInnerProps {
  children: React.ReactNode;
}

/**
 * AuthProviderInner : à utiliser DANS le provider OIDC de react-oidc-context.
 * Nécessite AuthProvider (qui encapsule OidcProvider) pour fonctionner.
 */
export function AuthProviderInner({ children }: AuthProviderInnerProps) {
  const auth = useAuth(); // Hook de react-oidc-context

  /**
   * Intercepteur Axios : injecte le Bearer token dans chaque requête.
   *
   * useEffect avec [auth.user] comme dépendance — se relance seulement
   * quand le token change (login, logout, silent renew).
   *
   * ATTENTION : on enregistre un nouvel intercepteur et on supprime l'ancien
   * pour éviter l'accumulation d'intercepteurs en mémoire (memory leak).
   */
  useEffect(() => {
    const interceptorId = apiClient.interceptors.request.use((config) => {
      const token = auth.user?.access_token;
      if (token) {
        config.headers['Authorization'] = `Bearer ${token}`;
      }
      return config;
    });

    // Cleanup : supprime l'intercepteur quand le token change ou au démontage
    return () => {
      apiClient.interceptors.request.eject(interceptorId);
    };
  }, [auth.user?.access_token]);

  /**
   * Extraction du rôle depuis les claims JWT.
   *
   * Keycloak inclut les rôles dans : token.realm_access.roles[]
   * Cognito inclut les groupes dans : token['cognito:groups'][]
   *
   * On parse le token JWT manuellement (base64url decode de la payload).
   * react-oidc-context ne décode pas automatiquement les claims customs.
   */
  const currentUser = useMemo((): CurrentUser | null => {
    if (!auth.isAuthenticated || !auth.user) return null;

    const profile = auth.user.profile;

    // Extraction du rôle
    let role: UserRole = 'GESTIONNAIRE'; // Rôle par défaut

    // Keycloak : realm_access.roles
    const realmRoles = (profile as Record<string, unknown>)['realm_access'] as
      | { roles?: string[] }
      | undefined;

    if (realmRoles?.roles) {
      if (realmRoles.roles.includes('SUPER_ADMINISTRATEUR')) role = 'SUPER_ADMINISTRATEUR';
      else if (realmRoles.roles.includes('MANAGER')) role = 'MANAGER';
      else if (realmRoles.roles.includes('GESTIONNAIRE')) role = 'GESTIONNAIRE';
    }

    // Cognito fallback : cognito:groups
    const cognitoGroups = (profile as Record<string, unknown>)['cognito:groups'] as
      | string[]
      | undefined;

    if (cognitoGroups) {
      if (cognitoGroups.includes('SUPER_ADMINISTRATEUR')) role = 'SUPER_ADMINISTRATEUR';
      else if (cognitoGroups.includes('MANAGER')) role = 'MANAGER';
    }

    return {
      id: profile.sub ?? '',
      email: profile.email ?? '',
      name: profile.name ?? profile.preferred_username ?? '',
      role,
    };
  }, [auth.isAuthenticated, auth.user]);

  // Calcul des droits — évite les vérifications répétées dans les composants
  const isGestionnaire = currentUser?.role === 'GESTIONNAIRE';
  const isManager = currentUser?.role === 'MANAGER';
  const isSuperAdmin = currentUser?.role === 'SUPER_ADMINISTRATEUR';

  const value: AuthContextValue = {
    currentUser,
    isLoading: auth.isLoading,
    isAuthenticated: auth.isAuthenticated,

    isGestionnaire,
    isManager,
    isSuperAdmin,

    // Permissions métier — miroir des règles Java RG-01/RG-04
    canCreateTask: isGestionnaire || isSuperAdmin,
    canValidateTask: isManager || isSuperAdmin,
    canManageUsers: isSuperAdmin,

    login: () => auth.signinRedirect(),
    logout: () => auth.signoutRedirect({ post_logout_redirect_uri: window.location.origin }),
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

// ── Hook personnalisé ────────────────────────────────────────────

/**
 * useAuthContext() — à utiliser dans tous les composants qui ont besoin
 * des infos d'authentification.
 *
 * Lève une erreur si utilisé hors du Provider (facilite le debugging).
 *
 * Usage :
 *   const { currentUser, isManager, canValidateTask } = useAuthContext();
 */
export function useAuthContext(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuthContext() doit être utilisé dans un AuthProvider');
  }
  return ctx;
}
