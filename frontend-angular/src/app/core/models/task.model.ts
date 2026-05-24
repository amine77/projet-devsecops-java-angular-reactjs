/**
 * ══════════════════════════════════════════════════════════════
 *  MODÈLES TypeScript — task.model.ts
 * ══════════════════════════════════════════════════════════════
 *
 *  Ces interfaces sont le MIROIR EXACT des DTOs Java côté backend :
 *  → TaskResponse.java   → Task (interface de lecture)
 *  → TaskRequest.java    → CreateTaskRequest / UpdateTaskRequest
 *  → RejectRequest.java  → RejectTaskRequest
 *
 *  CONVENTION TYPESCRIPT :
 *  → interfaces pour les données (pas de classes — plus léger)
 *  → type aliases pour les unions et les enums
 *  → readonly pour les données en lecture (immutabilité)
 *
 *  ENUMS → TYPE UNIONS :
 *  On utilise des type unions plutôt que des enums TypeScript car :
 *  → Plus léger dans le bundle (pas d'objet généré en JS)
 *  → Plus idiomatique en TypeScript moderne
 *  → Compatible avec le JSON retourné par l'API (strings)
 */

// ── Enums (correspondance exacte avec les enums Java) ───────────

/** Statuts possibles d'une tâche — machine d'état backend */
export type TaskStatus = 'A_FAIRE' | 'VALIDEE' | 'REJETEE' | 'DONE';

/** Niveaux de priorité — BASSE < NORMALE < HAUTE < URGENTE */
export type Priority = 'BASSE' | 'NORMALE' | 'HAUTE' | 'URGENTE';

/** Rôles utilisateur — RBAC */
export type UserRole = 'GESTIONNAIRE' | 'MANAGER' | 'SUPER_ADMINISTRATEUR';

// ── Lecture (réponse de l'API) ───────────────────────────────────

/**
 * Tâche telle que retournée par GET /api/tasks/{id}.
 * Correspond exactement à TaskResponse.java côté backend.
 */
export interface Task {
  readonly id: string;           // UUID
  readonly title: string;
  readonly description: string | null;
  readonly status: TaskStatus;
  readonly priority: Priority;
  readonly dueDate: string | null;     // ISO 8601 : "2026-06-30"
  readonly overdue: boolean;           // calculé côté serveur

  readonly ownerId: string;      // UUID du GESTIONNAIRE
  readonly teamId: string;       // UUID de l'équipe

  // Données de rejet (null sauf si REJETEE)
  readonly rejectionReason: string | null;
  readonly rejectedBy: string | null;
  readonly rejectedAt: string | null;  // ISO 8601 datetime

  // Données de validation (null sauf si VALIDEE ou DONE)
  readonly validatedBy: string | null;
  readonly validatedAt: string | null;

  // Données de clôture (null sauf si DONE)
  readonly doneBy: string | null;
  readonly doneAt: string | null;

  // Audit
  readonly createdAt: string;    // ISO 8601 datetime
  readonly updatedAt: string;
  readonly version: number;      // optimistic locking — renvoyer dans PUT
}

// ── Écriture (corps des requêtes HTTP) ───────────────────────────

/**
 * Corps de POST /api/tasks (création).
 * actorId N'EST PAS inclus — il est extrait du JWT côté serveur.
 */
export interface CreateTaskRequest {
  title: string;
  description?: string;
  priority: Priority;
  dueDate?: string;  // ISO 8601 : "2026-06-30"
}

/** Corps de PUT /api/tasks/{id} (modification). */
export type UpdateTaskRequest = CreateTaskRequest;

/** Corps de PUT /api/tasks/{id}/reject (rejet). */
export interface RejectTaskRequest {
  rejectionReason: string;
}

// ── Helpers / utilitaires ────────────────────────────────────────

/** Labels français pour l'affichage dans l'UI */
export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  A_FAIRE: 'À faire',
  VALIDEE: 'Validée',
  REJETEE: 'Rejetée',
  DONE: 'Terminée',
};

export const PRIORITY_LABELS: Record<Priority, string> = {
  BASSE: 'Basse',
  NORMALE: 'Normale',
  HAUTE: 'Haute',
  URGENTE: 'Urgente',
};

/** Couleurs Tailwind/CSS selon le statut */
export const TASK_STATUS_COLORS: Record<TaskStatus, string> = {
  A_FAIRE: 'bg-blue-100 text-blue-800',
  VALIDEE: 'bg-green-100 text-green-800',
  REJETEE: 'bg-red-100 text-red-800',
  DONE:    'bg-gray-100 text-gray-600',
};

export const PRIORITY_COLORS: Record<Priority, string> = {
  BASSE:   'bg-slate-100 text-slate-600',
  NORMALE: 'bg-yellow-100 text-yellow-700',
  HAUTE:   'bg-orange-100 text-orange-700',
  URGENTE: 'bg-red-100 text-red-700',
};

/** Vérifie si une transition de statut est possible (miroir de TaskStatus.canTransitionTo()) */
export function canTransitionTo(current: TaskStatus, target: TaskStatus): boolean {
  const transitions: Record<TaskStatus, TaskStatus[]> = {
    A_FAIRE: ['VALIDEE', 'REJETEE', 'DONE'],
    VALIDEE: ['DONE'],
    REJETEE: ['A_FAIRE'], // RG-07 : la modification remet en A_FAIRE
    DONE:    [],
  };
  return transitions[current].includes(target);
}
