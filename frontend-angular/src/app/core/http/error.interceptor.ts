import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '@core/auth/auth.service';
import { ApiError } from '@core/models/api-error.model';

/**
 * ══════════════════════════════════════════════════════════════
 *  INTERCEPTEUR HTTP — ErrorInterceptor
 * ══════════════════════════════════════════════════════════════
 *
 *  Gestion centralisée des erreurs HTTP.
 *
 *  CODES TRAITÉS :
 *  → 401 Unauthorized : token expiré ou absent → forcer le login
 *  → 403 Forbidden    : droits insuffisants → page d'erreur
 *  → 404 Not Found    : ressource introuvable → toast d'erreur
 *  → 409 Conflict     : optimistic locking → message spécifique
 *  → 422 Unprocessable: erreur métier (statut invalide, motif absent...)
 *  → 500              : erreur serveur → log + toast générique
 *
 *  PATTERN rxjs CATCHERROR :
 *  → catchError intercepte les erreurs dans le flux Observable
 *  → throwError relaie l'erreur formatée aux composants
 *  → Les composants peuvent afficher le message d'erreur ou l'ignorer
 *
 *  TOASTS (Phase 5) :
 *  → Pour l'instant : console.error
 *  → Phase 5 : intégrer Angular Material Snackbar ou ngx-toastr
 * ══════════════════════════════════════════════════════════════
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const router      = inject(Router);
  const authService = inject(AuthService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Extraire le body RFC 7807 si disponible
      const apiError: ApiError | null = error.error ?? null;
      const message = apiError?.detail ?? apiError?.title ?? error.message;

      switch (error.status) {
        case 401:
          // Token expiré ou invalide → relogin
          console.warn('Session expirée — reconnexion requise');
          authService.logout();
          break;

        case 403:
          console.warn('Action non autorisée :', message);
          router.navigate(['/forbidden']);
          break;

        case 404:
          console.warn('Ressource introuvable :', message);
          // Laisser le composant gérer (ex: afficher un état vide)
          break;

        case 409:
          // Optimistic locking : la tâche a été modifiée par quelqu'un d'autre
          console.warn('Conflit de modification — rechargez la page :', message);
          break;

        case 422:
          // Erreur métier (motif manquant, transition interdite...)
          console.warn('Erreur métier :', message);
          break;

        default:
          if (error.status >= 500) {
            console.error('Erreur serveur :', message);
          }
          break;
      }

      // Relayer l'erreur formatée aux composants
      return throwError(() => ({
        status: error.status,
        message,
        apiError,
      }));
    })
  );
};
