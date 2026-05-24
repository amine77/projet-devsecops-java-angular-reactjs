import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimations } from '@angular/platform-browser/animations';
import { OAuthModule } from 'angular-oauth2-oidc';
import { importProvidersFrom } from '@angular/core';
import { routes } from './app.routes';
import { jwtInterceptor } from './core/http/jwt.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';

/**
 * ══════════════════════════════════════════════════════════════
 *  CONFIGURATION APPLICATION — app.config.ts
 * ══════════════════════════════════════════════════════════════
 *
 *  Point central de configuration Angular 20 — mode standalone.
 *
 *  ÉVOLUTION DEPUIS ANGULAR 15 :
 *  → Avant Angular 15 : NgModule (AppModule) obligatoire
 *  → Depuis Angular 15 : ApplicationConfig (standalone) recommandé
 *  → Plus de AppModule → configuration fonctionnelle et composition
 *
 *  provideZoneChangeDetection({ eventCoalescing: true }) :
 *  → Réduit le nombre de cycles de détection de changements
 *  → "eventCoalescing" : groupe les événements en un seul cycle
 *  → Meilleur si utilisé avec ChangeDetectionStrategy.OnPush
 *
 *  withComponentInputBinding() :
 *  → Permet de lier les paramètres de route directement aux @Input
 *  → Ex: /tasks/:id → @Input() id: string dans le composant
 *  → Plus besoin d'inject(ActivatedRoute) pour lire les params
 *
 *  withInterceptors([...]) :
 *  → Intercepteurs fonctionnels (Angular 15+)
 *  → L'ordre compte : jwt d'abord, puis error
 * ══════════════════════════════════════════════════════════════
 */
export const appConfig: ApplicationConfig = {
  providers: [
    // ── Détection de changements optimisée ──────────────────────
    provideZoneChangeDetection({ eventCoalescing: true }),

    // ── Routeur avec input binding ──────────────────────────────
    provideRouter(routes, withComponentInputBinding()),

    // ── HTTP Client avec intercepteurs ──────────────────────────
    provideHttpClient(
      withInterceptors([jwtInterceptor, errorInterceptor])
    ),

    // ── Animations (pour les transitions, modals...) ────────────
    provideAnimations(),

    // ── OIDC (angular-oauth2-oidc — module legacy à importer) ───
    // angular-oauth2-oidc utilise encore le format NgModule
    importProvidersFrom(
      OAuthModule.forRoot({
        resourceServer: {
          // Pas d'auto-injection du token ici — on utilise jwtInterceptor
          sendAccessToken: false,
        },
      })
    ),
  ],
};
