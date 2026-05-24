import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';

/**
 * ══════════════════════════════════════════════════════════════
 *  ROUTAGE — app.routes.ts
 * ══════════════════════════════════════════════════════════════
 *
 *  Définit la navigation de l'application.
 *
 *  LAZY LOADING (loadComponent) :
 *  → Chaque route charge son composant de manière asynchrone
 *  → Le bundle initial est minimal (temps de chargement réduit)
 *  → Angular divise automatiquement le code en chunks séparés (code splitting)
 *  → Exemple : TaskListComponent ne sera chargé que quand l'utilisateur
 *    navigue vers /tasks — pas au démarrage de l'application
 *
 *  GUARDS :
 *  → authGuard : vérifie que l'utilisateur est connecté
 *  → roleGuard : vérifie que le rôle correspond
 *
 *  CANACTIVATEFN (Angular 15+) :
 *  → Guards fonctionnels (simples fonctions) au lieu de classes
 *  → Inject() fonctionne dans les guards fonctionnels
 * ══════════════════════════════════════════════════════════════
 */
export const routes: Routes = [
  // Redirection par défaut
  { path: '', redirectTo: '/tasks', pathMatch: 'full' },

  // Callback OIDC (après login Keycloak)
  { path: 'auth/callback', redirectTo: '/tasks', pathMatch: 'full' },

  // ── Routes Gestionnaire (liste et formulaire de tâches) ─────
  {
    path: 'tasks',
    canActivate: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/tasks/pages/task-list.component').then(m => m.TaskListComponent),
      },
      {
        path: 'new',
        canActivate: [() => roleGuard(['GESTIONNAIRE', 'SUPER_ADMINISTRATEUR'])],
        loadComponent: () =>
          import('./features/tasks/pages/task-form.component').then(m => m.TaskFormComponent),
      },
      {
        path: ':id/edit',
        canActivate: [() => roleGuard(['GESTIONNAIRE', 'SUPER_ADMINISTRATEUR'])],
        loadComponent: () =>
          import('./features/tasks/pages/task-form.component').then(m => m.TaskFormComponent),
      },
    ],
  },

  // ── Tableau de bord Manager ──────────────────────────────────
  {
    path: 'dashboard',
    canActivate: [authGuard, () => roleGuard(['MANAGER', 'SUPER_ADMINISTRATEUR'])],
    loadComponent: () =>
      import('./features/dashboard/pages/manager-dashboard.component')
        .then(m => m.ManagerDashboardComponent),
  },

  // ── Pages d'erreur ──────────────────────────────────────────
  {
    path: 'forbidden',
    loadComponent: () =>
      import('./shared/components/error-page.component').then(m => m.ErrorPageComponent),
    data: { code: 403, message: 'Vous n\'avez pas les droits pour accéder à cette page.' },
  },

  // Route inconnue → 404
  {
    path: '**',
    loadComponent: () =>
      import('./shared/components/error-page.component').then(m => m.ErrorPageComponent),
    data: { code: 404, message: 'Page introuvable.' },
  },
];
