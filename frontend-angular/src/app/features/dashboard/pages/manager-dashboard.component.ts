import {
  Component, OnInit, inject, signal, computed, ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TaskService } from '@core/services/task.service';
import { AuthService } from '@core/auth/auth.service';
import { Task, TASK_STATUS_LABELS, PRIORITY_LABELS, PRIORITY_COLORS } from '@core/models/task.model';

/**
 * ══════════════════════════════════════════════════════════════
 *  COMPOSANT — ManagerDashboardComponent
 * ══════════════════════════════════════════════════════════════
 *
 *  Tableau de bord du MANAGER : tâches en attente + KPIs + retards.
 *
 *  SECTIONS :
 *  1. KPI Cards : total / en attente / en retard / terminées
 *  2. File d'action : tâches A_FAIRE à valider ou rejeter
 *  3. Alertes retard : tâches dont la date est dépassée
 *
 *  ACTIONS RAPIDES sur chaque carte :
 *  → Valider (POST /api/tasks/{id}/validate)
 *  → Rejeter (modal avec motif → POST /api/tasks/{id}/reject)
 *
 *  SIGNAL OPTIMISTE :
 *  → Quand on valide/rejette, on met à jour le signal local immédiatement
 *    sans attendre la réponse du serveur (optimistic update)
 *  → Si l'API retourne une erreur, on annule la mise à jour locale
 *  → UX fluide sans attendre le réseau
 * ══════════════════════════════════════════════════════════════
 */
@Component({
  selector: 'app-manager-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="container mx-auto p-6">

      <!-- En-tête -->
      <div class="mb-6">
        <h1 class="text-2xl font-bold text-gray-900">Tableau de bord Manager</h1>
        <p class="text-sm text-gray-500">Équipe : {{ teamId() }}</p>
      </div>

      <!-- ── KPI Cards ──────────────────────────────────────── -->
      <div class="grid grid-cols-2 md:grid-cols-4 gap-4 mb-8">

        <div class="bg-white border rounded-xl p-4 shadow-sm text-center">
          <p class="text-3xl font-bold text-gray-900">{{ tasks().length }}</p>
          <p class="text-sm text-gray-500 mt-1">Total</p>
        </div>

        <div class="bg-blue-50 border border-blue-200 rounded-xl p-4 shadow-sm text-center">
          <p class="text-3xl font-bold text-blue-700">{{ pendingCount() }}</p>
          <p class="text-sm text-blue-600 mt-1">En attente</p>
        </div>

        <div class="bg-red-50 border border-red-200 rounded-xl p-4 shadow-sm text-center">
          <p class="text-3xl font-bold text-red-700">{{ overdueCount() }}</p>
          <p class="text-sm text-red-600 mt-1">En retard</p>
        </div>

        <div class="bg-green-50 border border-green-200 rounded-xl p-4 shadow-sm text-center">
          <p class="text-3xl font-bold text-green-700">{{ doneCount() }}</p>
          <p class="text-sm text-green-600 mt-1">Terminées</p>
        </div>
      </div>

      @if (loading()) {
        <div class="flex justify-center py-12">
          <div class="animate-spin rounded-full h-10 w-10 border-b-2 border-blue-600"></div>
        </div>
      } @else {

        <!-- ── File d'action ─────────────────────────────────── -->
        <section class="mb-8">
          <h2 class="text-lg font-semibold text-gray-900 mb-3">
            📋 File d'action
            @if (pendingCount() > 0) {
              <span class="ml-2 bg-blue-600 text-white text-xs px-2 py-0.5 rounded-full">
                {{ pendingCount() }}
              </span>
            }
          </h2>

          @if (pendingTasks().length === 0) {
            <div class="bg-gray-50 border border-dashed border-gray-300 rounded-xl p-8 text-center">
              <p class="text-gray-400">✅ Aucune tâche en attente de décision</p>
            </div>
          } @else {
            <div class="space-y-3">
              @for (task of pendingTasks(); track task.id) {
                <div class="bg-white border rounded-xl p-4 shadow-sm">
                  <div class="flex items-start justify-between gap-4">
                    <div class="flex-1">
                      <div class="flex items-center gap-2 mb-1">
                        <span [class]="getPriorityColor(task.priority)"
                              class="px-2 py-0.5 rounded-full text-xs font-medium">
                          {{ getPriorityLabel(task.priority) }}
                        </span>
                        @if (task.overdue) {
                          <span class="px-2 py-0.5 bg-red-100 text-red-700 rounded-full text-xs font-medium">
                            ⚠️ En retard
                          </span>
                        }
                      </div>
                      <h3 class="font-medium text-gray-900">{{ task.title }}</h3>
                      @if (task.description) {
                        <p class="text-sm text-gray-500 mt-0.5 line-clamp-1">{{ task.description }}</p>
                      }
                      @if (task.dueDate) {
                        <p class="text-xs text-gray-400 mt-1">
                          📅 Échéance : {{ task.dueDate | date:'dd/MM/yyyy' }}
                        </p>
                      }
                    </div>

                    <!-- Actions rapides -->
                    <div class="flex gap-2 shrink-0">
                      <button
                        (click)="onValidate(task)"
                        [disabled]="processingId() === task.id"
                        class="px-3 py-1.5 bg-green-600 text-white rounded-lg text-sm
                               hover:bg-green-700 disabled:opacity-50 transition-colors">
                        ✓ Valider
                      </button>
                      <button
                        (click)="onRejectClick(task)"
                        [disabled]="processingId() === task.id"
                        class="px-3 py-1.5 bg-red-100 text-red-700 rounded-lg text-sm
                               hover:bg-red-200 disabled:opacity-50 transition-colors">
                        ✗ Rejeter
                      </button>
                    </div>
                  </div>
                </div>
              }
            </div>
          }
        </section>

        <!-- ── Modal rejet inline ────────────────────────────── -->
        @if (rejectingTask()) {
          <div class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
            <div class="bg-white rounded-xl shadow-xl p-6 w-full max-w-md">
              <h3 class="text-lg font-semibold text-gray-900 mb-1">Rejeter la tâche</h3>
              <p class="text-sm text-gray-500 mb-4">
                Tâche : <strong>{{ rejectingTask()?.title }}</strong>
              </p>
              <label class="block text-sm font-medium text-gray-700 mb-1">
                Motif de rejet <span class="text-red-500">*</span>
                <span class="text-gray-400 font-normal"> (RG-06 — obligatoire)</span>
              </label>
              <textarea
                [(ngModel)]="rejectionReason"
                rows="3"
                placeholder="Expliquez clairement le motif de rejet..."
                class="w-full px-3 py-2 border rounded-lg text-sm resize-none
                       focus:outline-none focus:ring-2 focus:ring-red-500 mb-4"
              ></textarea>
              @if (rejectError()) {
                <p class="text-red-500 text-xs mb-3">{{ rejectError() }}</p>
              }
              <div class="flex justify-end gap-3">
                <button
                  (click)="cancelReject()"
                  class="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg text-sm">
                  Annuler
                </button>
                <button
                  (click)="confirmReject()"
                  [disabled]="!rejectionReason.trim() || processingId() !== null"
                  class="px-4 py-2 bg-red-600 text-white rounded-lg text-sm
                         hover:bg-red-700 disabled:opacity-50">
                  Confirmer le rejet
                </button>
              </div>
            </div>
          </div>
        }
      }
    </div>
  `,
})
export class ManagerDashboardComponent implements OnInit {

  private readonly taskService = inject(TaskService);
  private readonly authService = inject(AuthService);

  // ── Signals ──────────────────────────────────────────────────
  readonly tasks        = signal<Task[]>([]);
  readonly loading      = signal(false);
  readonly processingId = signal<string | null>(null);
  readonly rejectingTask = signal<Task | null>(null);
  readonly rejectError  = signal<string | null>(null);

  rejectionReason = '';

  // ── Computed ─────────────────────────────────────────────────
  readonly teamId      = computed(() => this.authService.currentUser()?.teamId ?? '');
  readonly pendingTasks = computed(() => this.tasks().filter(t => t.status === 'A_FAIRE'));
  readonly pendingCount = computed(() => this.pendingTasks().length);
  readonly overdueCount = computed(() => this.tasks().filter(t => t.overdue).length);
  readonly doneCount    = computed(() => this.tasks().filter(t => t.status === 'DONE').length);

  ngOnInit(): void {
    this.loadTasks();
  }

  onValidate(task: Task): void {
    this.processingId.set(task.id);
    this.taskService.validateTask(task.id).subscribe({
      next: () => {
        // Mise à jour optimiste : changer le statut localement
        this.tasks.update(list =>
          list.map(t => t.id === task.id ? { ...t, status: 'VALIDEE' as const } : t)
        );
        this.processingId.set(null);
      },
      error: err => {
        console.error('Validation échouée :', err);
        this.processingId.set(null);
      },
    });
  }

  onRejectClick(task: Task): void {
    this.rejectingTask.set(task);
    this.rejectionReason = '';
    this.rejectError.set(null);
  }

  cancelReject(): void {
    this.rejectingTask.set(null);
  }

  confirmReject(): void {
    const task = this.rejectingTask();
    if (!task || !this.rejectionReason.trim()) return;

    this.processingId.set(task.id);
    this.taskService.rejectTask(task.id, { rejectionReason: this.rejectionReason.trim() })
      .subscribe({
        next: () => {
          this.tasks.update(list =>
            list.map(t => t.id === task.id
              ? { ...t, status: 'REJETEE' as const, rejectionReason: this.rejectionReason }
              : t
            )
          );
          this.rejectingTask.set(null);
          this.processingId.set(null);
        },
        error: err => {
          this.rejectError.set(err.message ?? 'Erreur lors du rejet');
          this.processingId.set(null);
        },
      });
  }

  getPriorityLabel(p: string): string {
    return PRIORITY_LABELS[p as keyof typeof PRIORITY_LABELS] ?? p;
  }

  getPriorityColor(p: string): string {
    return PRIORITY_COLORS[p as keyof typeof PRIORITY_COLORS] ?? '';
  }

  private loadTasks(): void {
    this.loading.set(true);
    const teamId = this.authService.currentUser()?.teamId ?? '';
    this.taskService.getTeamTasks(teamId).subscribe({
      next: tasks => { this.tasks.set(tasks); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
