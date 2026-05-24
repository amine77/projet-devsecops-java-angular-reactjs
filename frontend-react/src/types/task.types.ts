/**
 * ══════════════════════════════════════════════════════════════
 *  TYPES TypeScript — task.types.ts (React)
 * ══════════════════════════════════════════════════════════════
 *
 *  Mêmes types que côté Angular — miroir des DTOs Java.
 *  On pourrait les partager dans un package npm commun (monorepo Nx),
 *  mais pour la simplicité pédagogique, ils sont dupliqués ici.
 */

export type TaskStatus = 'A_FAIRE' | 'VALIDEE' | 'REJETEE' | 'DONE';
export type Priority   = 'BASSE' | 'NORMALE' | 'HAUTE' | 'URGENTE';
export type UserRole   = 'GESTIONNAIRE' | 'MANAGER' | 'SUPER_ADMINISTRATEUR';

export interface Task {
  readonly id: string;
  readonly title: string;
  readonly description: string | null;
  readonly status: TaskStatus;
  readonly priority: Priority;
  readonly dueDate: string | null;
  readonly overdue: boolean;
  readonly ownerId: string;
  readonly teamId: string;
  readonly rejectionReason: string | null;
  readonly rejectedBy: string | null;
  readonly rejectedAt: string | null;
  readonly validatedBy: string | null;
  readonly validatedAt: string | null;
  readonly doneBy: string | null;
  readonly doneAt: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
  readonly version: number;
}

export interface CreateTaskRequest {
  title: string;
  description?: string;
  priority: Priority;
  dueDate?: string;
}

export type UpdateTaskRequest = CreateTaskRequest;

export interface RejectTaskRequest {
  rejectionReason: string;
}

// ── Helpers ──────────────────────────────────────────────────

export const STATUS_LABELS: Record<TaskStatus, string> = {
  A_FAIRE: 'À faire',
  VALIDEE: 'Validée',
  REJETEE: 'Rejetée',
  DONE:    'Terminée',
};

export const PRIORITY_LABELS: Record<Priority, string> = {
  BASSE:   'Basse',
  NORMALE: 'Normale',
  HAUTE:   'Haute',
  URGENTE: 'Urgente',
};

export const STATUS_BADGE_CLASS: Record<TaskStatus, string> = {
  A_FAIRE: 'bg-blue-100 text-blue-800',
  VALIDEE: 'bg-green-100 text-green-800',
  REJETEE: 'bg-red-100 text-red-800',
  DONE:    'bg-gray-100 text-gray-600',
};

export const PRIORITY_BADGE_CLASS: Record<Priority, string> = {
  BASSE:   'bg-slate-100 text-slate-600',
  NORMALE: 'bg-yellow-100 text-yellow-700',
  HAUTE:   'bg-orange-100 text-orange-700',
  URGENTE: 'bg-red-100 text-red-700',
};
