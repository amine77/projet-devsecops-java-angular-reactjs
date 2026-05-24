import {
  Component, OnInit, inject, signal, computed, ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TaskService } from '@core/services/task.service';
import { AuthService } from '@core/auth/auth.service';
import {
  Task, TaskStatus, TASK_STATUS_LABELS, TASK_STATUS_COLORS,
  PRIORITY_LABELS, PRIORITY_COLORS
} from '@core/models/task.model';

/**
 * ══════════════════════════════════════════════════════════════
 *  COMPOSANT — TaskListComponent
 * ══════════════════════════════════════════════════════════════
 *
 *  Page principale : liste des tâches avec filtrage par statut.
 *
 *  STANDALONE COMPONENT (Angular 14+) :
 *  → standalone: true → pas besoin de NgModule
 *  → imports: [] → déclare les dépendances directement dans le composant
 *  → C'est le modèle recommandé depuis Angular 17
 *
 *  SIGNALS ANGULAR 20 :
 *  → signal<T>() : valeur réactive (équivalent à useState en React)
 *  → computed(() => ...) : valeur dérivée recalculée automatiquement
 *  → Avantage vs Observables : pas de subscribe, syntaxe synchrone
 *
 *  CHANGEDETECTIONSTRATEGY.ONPUSH :
 *  → Angular ne re-rend le composant que quand :
 *    - Un @Input change (référence)
 *    - Un Observable émet (async pipe)
 *    - Un Signal change
 *    - changeDetectorRef.markForCheck() est appelé
 *  → Beaucoup plus performant que la stratégie Default (vérification à chaque tick)
 *  → Possible uniquement avec des données immuables et des Signals/Observables
 *
 *  GESTION D'ÉTAT LOCALE (sans NgRx) :
 *  → tasks : signal contenant la liste des tâches
 *  → loading : signal boolean pour l'état de chargement
 *  → error : signal pour les messages d'erreur
 *  → filterStatus : signal pour le filtre actif
 *  → filteredTasks : computed → dérivé de tasks + filterStatus
 *
 *  POURQUOI PAS NgRx STORE ICI ?
 *  → NgRx est utile pour un état global complexe (panier e-commerce, etc.)
 *  → Pour une liste de tâches paginée avec filtres locaux, Signals suffisent
 *  → Plus simple, moins de boilerplate, même performance
 * ══════════════════════════════════════════════════════════════
 */
@Component({
  selector: 'app-task-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="container mx-auto p-6">

      <!-- ── En-tête ─────────────────────────────────────────── -->
      <div class="flex items-center justify-between mb-6">
        <div>
          <h1 class="text-2xl font-bold text-gray-900">
            {{ isGestionnaire() ? 'Mes tâches' : 'Tâches de l\'équipe' }}
          </h1>
          <p class="text-sm text-gray-500 mt-1">
            {{ filteredTasks().length }} tâche(s) affichée(s)
            @if (overdueCount() > 0) {
              <span class="ml-2 text-red-600 font-medium">
                ⚠️ {{ overdueCount() }} en retard
              </span>
            }
          </p>
        </div>

        <!-- Bouton Créer (GESTIONNAIRE uniquement) -->
        @if (canCreateTask()) {
          <a routerLink="/tasks/new"
             class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700
                    transition-colors text-sm font-medium">
            + Nouvelle tâche
          </a>
        }
      </div>

      <!-- ── Filtres par statut ──────────────────────────────── -->
      <div class="flex gap-2 mb-6 overflow-x-auto">
        <button
          (click)="setFilter(null)"
          [class.bg-gray-900]="filterStatus() === null"
          [class.text-white]="filterStatus() === null"
          [class.bg-gray-100]="filterStatus() !== null"
          class="px-3 py-1.5 rounded-full text-sm font-medium transition-colors whitespace-nowrap">
          Tous ({{ tasks().length }})
        </button>

        @for (status of statuses; track status) {
          <button
            (click)="setFilter(status)"
            [class.ring-2]="filterStatus() === status"
            [class]="getStatusColorClass(status)"
            class="px-3 py-1.5 rounded-full text-sm font-medium transition-all whitespace-nowrap">
            {{ getStatusLabel(status) }} ({{ countByStatus(status) }})
          </button>
        }
      </div>

      <!-- ── État de chargement ──────────────────────────────── -->
      @if (loading()) {
        <div class="flex justify-center items-center h-48">
          <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600"></div>
        </div>
      }

      <!-- ── Message d'erreur ────────────────────────────────── -->
      @if (error()) {
        <div class="bg-red-50 border border-red-200 rounded-lg p-4 mb-4">
          <p class="text-red-700 text-sm">{{ error() }}</p>
          <button (click)="reload()" class="mt-2 text-red-600 text-sm underline">
            Réessayer
          </button>
        </div>
      }

      <!-- ── Liste des tâches ────────────────────────────────── -->
      @if (!loading() && !error()) {
        @if (filteredTasks().length === 0) {
          <div class="text-center py-16 text-gray-400">
            <p class="text-4xl mb-3">📋</p>
            <p class="text-lg font-medium">Aucune tâche trouvée</p>
            <p class="text-sm">
              @if (filterStatus()) { Aucune tâche avec le statut "{{ getStatusLabel(filterStatus()!) }}" }
              @else if (canCreateTask()) { Créez votre première tâche ! }
            </p>
          </div>
        } @else {
          <div class="grid gap-3">
            @for (task of filteredTasks(); track task.id) {
              <div
                [class.border-red-300]="task.overdue"
                [class.bg-red-50]="task.overdue"
                class="bg-white border rounded-xl p-4 shadow-sm hover:shadow-md
                       transition-shadow cursor-pointer"
                [routerLink]="['/tasks', task.id]">

                <!-- En-tête de la carte -->
                <div class="flex items-start justify-between gap-3">
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center gap-2 flex-wrap mb-1">
                      <!-- Badge statut -->
                      <span [class]="getStatusColorClass(task.status)"
                            class="px-2 py-0.5 rounded-full text-xs font-medium">
                        {{ getStatusLabel(task.status) }}
                      </span>
                      <!-- Badge priorité -->
                      <span [class]="getPriorityColorClass(task.priority)"
                            class="px-2 py-0.5 rounded-full text-xs font-medium">
                        {{ getPriorityLabel(task.priority) }}
                      </span>
                      <!-- Badge retard -->
                      @if (task.overdue) {
                        <span class="px-2 py-0.5 bg-red-100 text-red-700 rounded-full
                                     text-xs font-medium">
                          ⚠️ En retard
                        </span>
                      }
                    </div>
                    <h3 class="font-semibold text-gray-900 truncate">{{ task.title }}</h3>
                    @if (task.description) {
                      <p class="text-sm text-gray-500 mt-0.5 line-clamp-1">{{ task.description }}</p>
                    }
                  </div>

                  <!-- Date d'échéance -->
                  @if (task.dueDate) {
                    <span [class.text-red-600]="task.overdue"
                          [class.text-gray-400]="!task.overdue"
                          class="text-xs whitespace-nowrap">
                      📅 {{ task.dueDate | date:'dd/MM/yy' }}
                    </span>
                  }
                </div>

                <!-- Motif de rejet -->
                @if (task.status === 'REJETEE' && task.rejectionReason) {
                  <div class="mt-2 p-2 bg-red-50 border border-red-100 rounded-lg">
                    <p class="text-xs text-red-700">
                      <strong>Motif :</strong> {{ task.rejectionReason }}
                    </p>
                  </div>
                }
              </div>
            }
          </div>
        }
      }
    </div>
  `,
})
export class TaskListComponent implements OnInit {

  // ── Services injectés ────────────────────────────────────────
  private readonly taskService = inject(TaskService);
  private readonly authService = inject(AuthService);

  // ── Signals — état local ─────────────────────────────────────
  readonly tasks        = signal<Task[]>([]);
  readonly loading      = signal(false);
  readonly error        = signal<string | null>(null);
  readonly filterStatus = signal<TaskStatus | null>(null);

  // ── Computed — dérivés réactifs ──────────────────────────────

  /** Tâches filtrées par statut actif */
  readonly filteredTasks = computed(() => {
    const status = this.filterStatus();
    return status ? this.tasks().filter(t => t.status === status) : this.tasks();
  });

  /** Nombre de tâches en retard */
  readonly overdueCount = computed(() => this.tasks().filter(t => t.overdue).length);

  // ── Computed depuis l'utilisateur connecté ───────────────────
  readonly isGestionnaire = this.authService.isGestionnaire;
  readonly canCreateTask  = this.authService.canCreateTask;

  // ── Constantes ───────────────────────────────────────────────
  readonly statuses: TaskStatus[] = ['A_FAIRE', 'VALIDEE', 'REJETEE', 'DONE'];

  // ── Cycle de vie ─────────────────────────────────────────────

  ngOnInit(): void {
    this.loadTasks();
  }

  reload(): void {
    this.loadTasks();
  }

  setFilter(status: TaskStatus | null): void {
    this.filterStatus.set(status);
  }

  countByStatus(status: TaskStatus): number {
    return this.tasks().filter(t => t.status === status).length;
  }

  getStatusLabel(status: TaskStatus): string {
    return TASK_STATUS_LABELS[status];
  }

  getStatusColorClass(status: TaskStatus): string {
    return TASK_STATUS_COLORS[status];
  }

  getPriorityLabel(priority: string): string {
    return PRIORITY_LABELS[priority as keyof typeof PRIORITY_LABELS] ?? priority;
  }

  getPriorityColorClass(priority: string): string {
    return PRIORITY_COLORS[priority as keyof typeof PRIORITY_COLORS] ?? '';
  }

  // ── Chargement des tâches ────────────────────────────────────

  private loadTasks(): void {
    this.loading.set(true);
    this.error.set(null);

    /**
     * Choix de l'endpoint selon le rôle :
     * → GESTIONNAIRE : /api/tasks/me (ses propres tâches)
     * → MANAGER      : /api/tasks?teamId=... (son équipe)
     * → SUPER_ADMIN  : /api/tasks?teamId=... ou /api/tasks (tout)
     */
    const user = this.authService.currentUser();
    const source$ = user?.role === 'GESTIONNAIRE'
      ? this.taskService.getMyTasks()
      : this.taskService.getTeamTasks(user?.teamId ?? '');

    source$.subscribe({
      next: tasks => {
        this.tasks.set(tasks);
        this.loading.set(false);
      },
      error: err => {
        this.error.set(err.message ?? 'Erreur lors du chargement des tâches');
        this.loading.set(false);
      },
    });
  }
}
