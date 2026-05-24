import { HttpInterceptorFn, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '@core/auth/auth.service';
import { environment } from '@env/environment';

/**
 * ══════════════════════════════════════════════════════════════
 *  INTERCEPTEUR HTTP — JwtInterceptor
 * ══════════════════════════════════════════════════════════════
 *
 *  Injecte automatiquement le JWT Bearer token dans toutes les
 *  requêtes HTTP vers l'API backend.
 *
 *  INTERCEPTEURS FONCTIONNELS (Angular 15+) :
 *  → Syntaxe moderne : HttpInterceptorFn (une simple fonction)
 *  → Remplace l'ancienne classe HttpInterceptor (plus de boilerplate)
 *  → Enregistré dans app.config.ts via withInterceptors([jwtInterceptor])
 *
 *  POURQUOI INTERCEPTER LES REQUÊTES ?
 *  → L'API backend valide le JWT sur chaque requête (Spring Security)
 *  → Sans le header Authorization, Spring retourne 401 Unauthorized
 *  → L'intercepteur évite de gérer le token dans chaque service manuellement
 *
 *  SÉCURITÉ :
 *  → N'injecte le token QUE pour les requêtes vers apiUrl (pas vers Keycloak, etc.)
 *  → Clone la requête pour l'immuabilité (HttpRequest est immuable en Angular)
 *
 *  FLOW :
 *  HttpClient.get('/api/tasks')
 *    → jwtInterceptor intercepte
 *    → ajoute Authorization: Bearer <token>
 *    → request modifiée envoyée au backend
 *    → Spring Security valide le JWT via JWKS
 *    → Si valide → accès accordé
 */
export const jwtInterceptor: HttpInterceptorFn = (
  req: HttpRequest<unknown>,
  next: HttpHandlerFn
) => {
  const authService = inject(AuthService);

  // N'injecter le JWT que pour les requêtes vers notre API
  // (pas pour les appels vers Keycloak/Cognito ou d'autres APIs)
  if (!req.url.startsWith(environment.apiUrl)) {
    return next(req);
  }

  const token = authService.getAccessToken();

  if (!token) {
    // Pas de token → laisser passer (Spring retournera 401 si l'endpoint est protégé)
    return next(req);
  }

  // Cloner la requête et ajouter le header Authorization
  // HttpRequest est immuable → on doit cloner pour modifier
  const authenticatedReq = req.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });

  return next(authenticatedReq);
};
