import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';
import { UserRole } from '@core/models/user.model';

/**
 * Guard de rôle — factory function.
 * Usage : canActivate: [() => roleGuard(['MANAGER', 'SUPER_ADMINISTRATEUR'])]
 *
 * POURQUOI UNE FACTORY ET PAS UN GUARD DIRECT ?
 * → Le guard a besoin de paramètres (les rôles autorisés)
 * → Une factory retourne un CanActivateFn avec les rôles fermés dedans (closure)
 * → Réutilisable partout dans les routes
 */
export function roleGuard(allowedRoles: UserRole[]) {
  return () => {
    const authService = inject(AuthService);
    const router      = inject(Router);

    const user = authService.currentUser();
    if (!user) {
      authService.login();
      return false;
    }

    if (allowedRoles.includes(user.role)) {
      return true;
    }

    // Rôle insuffisant → page 403
    router.navigate(['/forbidden']);
    return false;
  };
}
