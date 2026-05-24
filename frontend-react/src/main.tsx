import React from 'react';
import ReactDOM from 'react-dom/client';
import { AuthProvider } from 'react-oidc-context';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import App from './App';
import './index.css';

/**
 * ══════════════════════════════════════════════════════════════
 *  POINT D'ENTRÉE — main.tsx (React)
 * ══════════════════════════════════════════════════════════════
 *
 *  Équivalent de main.ts dans Angular.
 *  Configure les providers globaux avant de monter l'application.
 *
 *  PROVIDERS GLOBAUX :
 *  1. AuthProvider (react-oidc-context)
 *     → Configure la connexion OIDC avec Keycloak
 *     → Flux PKCE (Proof Key for Code Exchange) — sécurisé pour les SPAs
 *     → Stocke les tokens en sessionStorage (pas localStorage pour la sécurité)
 *
 *  2. QueryClientProvider (TanStack Query)
 *     → Fournit le cache global des requêtes à tous les composants
 *     → Configure les comportements par défaut (retry, staleTime, etc.)
 *
 *  DIFFÉRENCE AVEC ANGULAR :
 *  → Angular : provideHttpClient() + provideRouter() dans app.config.ts
 *  → React   : Providers JSX wrappant l'arbre de composants
 *
 *  OIDC CONFIG :
 *  → authority: URL du realm Keycloak (ou Cognito)
 *  → client_id: ID du client OIDC configuré dans Keycloak
 *  → redirect_uri: URL de retour après authentification
 *  → Keycloak doit avoir ce redirect_uri dans sa liste autorisée
 *
 *  TANSTACK QUERY CONFIG :
 *  → retry: 1 → 1 seule relance automatique en cas d'erreur réseau
 *  → staleTime: 0 → par défaut, données considérées stales immédiatement
 *    (chaque composant définit son propre staleTime via useQuery)
 */

// ── Configuration OIDC / Keycloak ──────────────────────────────

const oidcConfig = {
  /** URL du realm Keycloak (à changer en prod via variable d'env) */
  authority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8180/realms/todo',

  /** Client ID configuré dans Keycloak → doit correspondre exactement */
  client_id: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'todo-spa',

  /** Redirect URI — Keycloak renvoie l'utilisateur ici après login */
  redirect_uri: import.meta.env.VITE_OIDC_REDIRECT_URI ?? window.location.origin + '/auth/callback',

  /**
   * Scopes OIDC :
   * - openid : obligatoire pour avoir un ID Token
   * - profile : nom, prénom, etc.
   * - email : adresse email
   */
  scope: 'openid profile email',

  /**
   * post_logout_redirect_uri : URL de retour après déconnexion.
   * Keycloak invalide la session SSO et redirige ici.
   */
  post_logout_redirect_uri: window.location.origin,

  /**
   * automaticSilentRenew : renouvelle le token en arrière-plan
   * avant qu'il n'expire (via iframe silencieux).
   * Évite de déconnecter l'utilisateur en plein travail.
   */
  automaticSilentRenew: true,

  /** Callback après authentification réussie — retour à la page précédente */
  onSigninCallback: () => {
    window.history.replaceState({}, document.title, window.location.pathname);
  },
};

// ── Configuration TanStack Query ───────────────────────────────

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      /**
       * retry: 1 — relance une seule fois en cas d'erreur réseau.
       * Évite les boucles infinies sur les erreurs 4xx (pas la peine de retry).
       */
      retry: 1,

      /**
       * refetchOnWindowFocus: true (défaut) — refetch quand l'onglet
       * reprend le focus. Bien pour les données qui changent souvent.
       * Peut être gênant en dev → mettre à false si besoin.
       */
      refetchOnWindowFocus: true,
    },
    mutations: {
      /** retry: 0 — ne pas relancer les mutations en cas d'erreur */
      retry: 0,
    },
  },
});

// ── Montage de l'application ───────────────────────────────────

const rootElement = document.getElementById('root');
if (!rootElement) throw new Error('Élément #root introuvable dans index.html');

ReactDOM.createRoot(rootElement).render(
  /**
   * StrictMode : En développement seulement.
   * Double-render chaque composant pour détecter les effets de bord.
   * Désactivé automatiquement en production.
   */
  <React.StrictMode>
    {/* 1. Provider OIDC — doit être le plus externe */}
    <AuthProvider {...oidcConfig}>
      {/* 2. Provider TanStack Query — accessible partout */}
      <QueryClientProvider client={queryClient}>
        <App />
      </QueryClientProvider>
    </AuthProvider>
  </React.StrictMode>
);
