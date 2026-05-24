import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Guard fonctionnel Angular 15+ : vérifie que l'utilisateur est authentifié.
 * Si non → déclenche le login OIDC.
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router      = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  // Pas authentifié → lancer le flow OIDC (redirect vers Keycloak)
  authService.login();
  return false;
};
