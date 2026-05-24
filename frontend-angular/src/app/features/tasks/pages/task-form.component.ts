import {
  Component, OnInit, inject, signal, ChangeDetectionStrategy
} from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl
} from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Observable, switchMap, of } from 'rxjs';
import { TaskService } from '@core/services/task.service';
import { Task, Priority, CreateTaskRequest } from '@core/models/task.model';

/**
 * ══════════════════════════════════════════════════════════════
 *  COMPOSANT — TaskFormComponent
 * ══════════════════════════════════════════════════════════════
 *
 *  Formulaire de création ET de modification d'une tâche.
 *  Un seul composant pour les deux cas (routé différemment).
 *
 *  REACTIVE FORMS (Angular) :
 *  → FormBuilder : construit le FormGroup avec les validations
 *  → Validators.required, Validators.maxLength : validation côté client
 *  → formGroup.valid : état global du formulaire
 *  → formControl.errors : erreurs de validation par champ
 *
 *  POURQUOI REACTIVE FORMS ET PAS TEMPLATE-DRIVEN ?
 *  → Reactive Forms : code TypeScript first, testable unitairement
 *  → Template-driven : logique dans le template HTML (moins maintenable)
 *  → Reactive Forms obligatoire dès qu'on a de la logique conditionnelle
 *
 *  ROUTING :
 *  → /tasks/new         : création (taskId = null)
 *  → /tasks/:id/edit    : modification (charge la tâche existante)
 *
 *  DOUBLE USAGE (create vs edit) :
 *  → ngOnInit détecte si un :id est présent dans la route
 *  → Si oui : charge la tâche et pre-remplit le formulaire
 *  → Si non : formulaire vide
 *  → onSubmit() appelle createTask() ou updateTask() selon le contexte
 * ══════════════════════════════════════════════════════════════
 */
@Component({
  selector: 'app-task-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="container mx-auto p-6 max-w-2xl">

      <!-- En-tête -->
      <div class="mb-6">
        <a routerLink="/tasks" class="text-sm text-blue-600 hover:underline">
          ← Retour à la liste
        </a>
        <h1 class="text-2xl font-bold text-gray-900 mt-2">
          {{ isEditMode() ? 'Modifier la tâche' : 'Nouvelle tâche' }}
        </h1>
      </div>

      <!-- Formulaire -->
      <form [formGroup]="taskForm" (ngSubmit)="onSubmit()"
            class="bg-white border rounded-xl p-6 shadow-sm space-y-5">

        <!-- Titre -->
        <div>
          <label for="title" class="block text-sm font-medium text-gray-700 mb-1">
            Titre <span class="text-red-500">*</span>
          </label>
          <input
            id="title"
            type="text"
            formControlName="title"
            placeholder="Ex: Préparer le rapport mensuel"
            [class.border-red-400]="titleControl.invalid && titleControl.touched"
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none
                   focus:ring-2 focus:ring-blue-500 focus:border-transparent text-sm"
          />
          @if (titleControl.invalid && titleControl.touched) {
            <p class="text-red-500 text-xs mt-1">
              @if (titleControl.errors?.['required']) { Le titre est obligatoire }
              @else if (titleControl.errors?.['maxlength']) { Maximum 200 caractères }
            </p>
          }
        </div>

        <!-- Description -->
        <div>
          <label for="description" class="block text-sm font-medium text-gray-700 mb-1">
            Description
          </label>
          <textarea
            id="description"
            formControlName="description"
            rows="3"
            placeholder="Description optionnelle..."
            class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none
                   focus:ring-2 focus:ring-blue-500 resize-none text-sm"
          ></textarea>
        </div>

        <!-- Priorité + Date (sur la même ligne) -->
        <div class="grid grid-cols-2 gap-4">

          <!-- Priorité -->
          <div>
            <label for="priority" class="block text-sm font-medium text-gray-700 mb-1">
              Priorité <span class="text-red-500">*</span>
            </label>
            <select
              id="priority"
              formControlName="priority"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none
                     focus:ring-2 focus:ring-blue-500 text-sm bg-white">
              <option value="">-- Choisir --</option>
              @for (p of priorities; track p.value) {
                <option [value]="p.value">{{ p.label }}</option>
              }
            </select>
            @if (priorityControl.invalid && priorityControl.touched) {
              <p class="text-red-500 text-xs mt-1">La priorité est obligatoire</p>
            }
          </div>

          <!-- Date d'échéance -->
          <div>
            <label for="dueDate" class="block text-sm font-medium text-gray-700 mb-1">
              Date d'échéance
            </label>
            <input
              id="dueDate"
              type="date"
              formControlName="dueDate"
              [min]="today"
              class="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none
                     focus:ring-2 focus:ring-blue-500 text-sm"
            />
          </div>
        </div>

        <!-- Erreur globale -->
        @if (error()) {
          <div class="p-3 bg-red-50 border border-red-200 rounded-lg">
            <p class="text-red-700 text-sm">{{ error() }}</p>
          </div>
        }

        <!-- Actions -->
        <div class="flex justify-end gap-3 pt-2">
          <a routerLink="/tasks"
             class="px-4 py-2 border border-gray-300 text-gray-700 rounded-lg
                    hover:bg-gray-50 text-sm transition-colors">
            Annuler
          </a>
          <button
            type="submit"
            [disabled]="taskForm.invalid || submitting()"
            class="px-5 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700
                   disabled:opacity-50 disabled:cursor-not-allowed text-sm
                   font-medium transition-colors">
            @if (submitting()) {
              <span class="inline-flex items-center gap-2">
                <span class="animate-spin inline-block w-4 h-4 border-2 border-white
                             border-t-transparent rounded-full"></span>
                Enregistrement...
              </span>
            } @else {
              {{ isEditMode() ? 'Modifier' : 'Créer la tâche' }}
            }
          </button>
        </div>
      </form>
    </div>
  `,
})
export class TaskFormComponent implements OnInit {

  private readonly taskService = inject(TaskService);
  private readonly route       = inject(ActivatedRoute);
  private readonly router      = inject(Router);
  private readonly fb          = inject(FormBuilder);

  // ── Signals ──────────────────────────────────────────────────
  readonly isEditMode  = signal(false);
  readonly submitting  = signal(false);
  readonly error       = signal<string | null>(null);
  private taskId: string | null = null;

  // ── Formulaire Reactive ──────────────────────────────────────
  taskForm: FormGroup = this.fb.group({
    title:       ['', [Validators.required, Validators.maxLength(200)]],
    description: ['', [Validators.maxLength(2000)]],
    priority:    ['', [Validators.required]],
    dueDate:     [null],
  });

  /** Date d'aujourd'hui au format yyyy-MM-dd (pour l'attribut [min] du datepicker) */
  readonly today = new Date().toISOString().split('T')[0];

  /** Options priorité pour le <select> */
  readonly priorities: { value: Priority; label: string }[] = [
    { value: 'BASSE',   label: '🟢 Basse' },
    { value: 'NORMALE', label: '🟡 Normale' },
    { value: 'HAUTE',   label: '🟠 Haute' },
    { value: 'URGENTE', label: '🔴 Urgente' },
  ];

  // ── Raccourcis vers les contrôles ────────────────────────────
  get titleControl():    AbstractControl { return this.taskForm.get('title')!; }
  get priorityControl(): AbstractControl { return this.taskForm.get('priority')!; }

  ngOnInit(): void {
    // Détection du mode édition via le param :id dans la route
    this.route.paramMap.pipe(
      switchMap(params => {
        const id = params.get('id');
        if (id) {
          this.taskId = id;
          this.isEditMode.set(true);
          return this.taskService.getTaskById(id);
        }
        return of(null);
      })
    ).subscribe(task => {
      if (task) this.prefillForm(task);
    });
  }

  onSubmit(): void {
    if (this.taskForm.invalid || this.submitting()) return;

    this.submitting.set(true);
    this.error.set(null);

    const request: CreateTaskRequest = {
      title:       this.taskForm.value.title.trim(),
      description: this.taskForm.value.description?.trim() || undefined,
      priority:    this.taskForm.value.priority,
      dueDate:     this.taskForm.value.dueDate || undefined,
    };

    // Typage explicite Observable<string | void> : TypeScript ne peut pas
    // résoudre automatiquement le type du ternaire car createTask retourne
    // Observable<string> (l'id extrait du Location header) et updateTask
    // retourne Observable<void>. L'union string | void permet les deux.
    const action$: Observable<string | void> = this.isEditMode() && this.taskId
      ? this.taskService.updateTask(this.taskId, request)
      : this.taskService.createTask(request);

    action$.subscribe({
      next: () => {
        this.router.navigate(['/tasks']);
      },
      // err: unknown (strict mode) — on accède à .message via cast sécurisé
      error: (err: unknown) => {
        const msg = err instanceof Error ? err.message : 'Erreur lors de l\'enregistrement';
        this.error.set(msg);
        this.submitting.set(false);
      },
    });
  }

  private prefillForm(task: Task): void {
    this.taskForm.patchValue({
      title:       task.title,
      description: task.description ?? '',
      priority:    task.priority,
      dueDate:     task.dueDate,
    });
  }
}
