import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CommonModule } from '@angular/common';

/** Page d'erreur générique — 403, 404 */
@Component({
  selector: 'app-error-page',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <div class="min-h-screen flex items-center justify-center bg-gray-50">
      <div class="text-center">
        <p class="text-6xl font-bold text-gray-300 mb-4">{{ code }}</p>
        <p class="text-xl font-semibold text-gray-700 mb-2">
          {{ code === 403 ? 'Accès refusé' : 'Page introuvable' }}
        </p>
        <p class="text-gray-500 mb-6">{{ message }}</p>
        <a routerLink="/tasks"
           class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm">
          Retour à l'accueil
        </a>
      </div>
    </div>
  `,
})
export class ErrorPageComponent {
  private readonly route = inject(ActivatedRoute);
  readonly code    = this.route.snapshot.data['code'] ?? 404;
  readonly message = this.route.snapshot.data['message'] ?? 'Une erreur est survenue.';
}
