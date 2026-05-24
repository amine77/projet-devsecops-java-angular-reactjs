import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { RouterOutlet, RouterLink, Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { AuthService } from './core/auth/auth.service';

/**
 * ══════════════════════════════════════════════════════════════
 *  COMPOSANT RACINE — AppComponent
 * ══════════════════════════════════════════════════════════════
 *
 *  Shell de l'application : navigation + router-outlet.
 *
 *  NAVBAR :
 *  → Logo + liens de navigation selon le rôle
 *  → Bouton Connexion / Déconnexion
 *  → Affichage du nom de l'utilisateur connecté
 *
 *  ROUTER-OUTLET :
 *  → Placeholder où Angular affiche le composant de la route active
 *  → La navigation change le contenu sans rechargement de page (SPA)
 * ══════════════════════════════════════════════════════════════
 */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, CommonModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="min-h-screen bg-gray-50">

      <!-- ── Navigation ──────────────────────────────────────── -->
      <nav class="bg-white border-b border-gray-200 px-6 py-3">
        <div class="container mx-auto flex items-center justify-between">

          <!-- Logo -->
          <a routerLink="/" class="flex items-center gap-2">
            <span class="text-xl">✅</span>
            <span class="font-bold text-gray-900 text-sm">Todo Enterprise</span>
          </a>

          <!-- Liens (selon le rôle) -->
          <div class="flex items-center gap-4">
            @if (authService.isAuthenticated()) {

              <!-- GESTIONNAIRE : voir ses tâches -->
              @if (authService.isGestionnaire()) {
                <a routerLink="/tasks"
                   class="text-sm text-gray-600 hover:text-gray-900 transition-colors">
                  Mes tâches
                </a>
              }

              <!-- MANAGER : tableau de bord + tâches équipe -->
              @if (authService.isManager()) {
                <a routerLink="/dashboard"
                   class="text-sm text-gray-600 hover:text-gray-900 transition-colors">
                  Tableau de bord
                </a>
                <a routerLink="/tasks"
                   class="text-sm text-gray-600 hover:text-gray-900 transition-colors">
                  Tâches équipe
                </a>
              }

              <!-- SUPER_ADMIN : tout -->
              @if (authService.isSuperAdmin()) {
                <a routerLink="/dashboard"
                   class="text-sm text-gray-600 hover:text-gray-900 transition-colors">
                  Dashboard
                </a>
                <a routerLink="/tasks"
                   class="text-sm text-gray-600 hover:text-gray-900 transition-colors">
                  Toutes les tâches
                </a>
              }

              <!-- Utilisateur + déconnexion -->
              <div class="flex items-center gap-3 ml-4 pl-4 border-l border-gray-200">
                <div class="text-right">
                  <p class="text-xs font-medium text-gray-800 leading-none">
                    {{ authService.currentUser()?.firstName }} {{ authService.currentUser()?.lastName }}
                  </p>
                  <p class="text-xs text-gray-400 mt-0.5">
                    {{ getRoleLabel(authService.currentUser()?.role) }}
                  </p>
                </div>
                <button
                  (click)="authService.logout()"
                  class="text-xs text-gray-500 hover:text-red-600 transition-colors">
                  Déconnexion
                </button>
              </div>

            } @else {
              <!-- Non connecté -->
              <button
                (click)="authService.login()"
                class="px-4 py-1.5 bg-blue-600 text-white rounded-lg text-sm
                       hover:bg-blue-700 transition-colors">
                Connexion
              </button>
            }
          </div>
        </div>
      </nav>

      <!-- ── Contenu de la route active ──────────────────────── -->
      <main>
        <router-outlet />
      </main>
    </div>
  `,
})
export class AppComponent {
  readonly authService = inject(AuthService);

  getRoleLabel(role?: string): string {
    const labels: Record<string, string> = {
      GESTIONNAIRE:          'Gestionnaire',
      MANAGER:               'Manager',
      SUPER_ADMINISTRATEUR:  'Super Admin',
    };
    return role ? (labels[role] ?? role) : '';
  }
}
