import axios from 'axios';
import type {
  Task, CreateTaskRequest, UpdateTaskRequest, RejectTaskRequest, TaskStatus
} from '@types/task.types';

/**
 * ══════════════════════════════════════════════════════════════
 *  API CLIENT — task.api.ts (React)
 * ══════════════════════════════════════════════════════════════
 *
 *  Encapsule les appels HTTP vers l'API /api/tasks.
 *  Utilise Axios au lieu de fetch() natif pour :
 *  → Interception automatique des erreurs HTTP (status !== 2xx)
 *  → Gestion du Content-Type automatique
 *  → Annulation de requêtes (AbortController)
 *  → Plus de lisibilité que fetch().then(r => r.json())
 *
 *  DIFFÉRENCE AVEC ANGULAR :
 *  → Angular : HttpClient (Observables, injectable)
 *  → React   : Axios + TanStack Query (Promises, hooks)
 *
 *  JWT INTERCEPTOR :
 *  → Configuré dans axios.defaults (voir auth.context.tsx)
 *  → L'intercepteur Axios ajoute Authorization: Bearer <token>
 *  → Équivalent de jwtInterceptor.ts côté Angular
 *
 *  BASE URL :
 *  → En développement : Vite proxifie /api vers localhost:8080
 *  → En production : variable d'environnement VITE_API_URL
 */

// Instance Axios configurée
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? '/api',
  headers: { 'Content-Type': 'application/json' },
});

// ══════════════════════════════════════════════════════════
//  COMMANDES
// ══════════════════════════════════════════════════════════

/** POST /api/tasks → retourne l'ID de la tâche créée */
export async function createTask(request: CreateTaskRequest): Promise<string> {
  const response = await apiClient.post('/tasks', request, { maxRedirects: 0 });
  // Extraire l'UUID depuis le Location header
  const location = response.headers['location'] ?? '';
  return location.split('/').pop() ?? '';
}

/** PUT /api/tasks/{id} → 204 */
export async function updateTask(id: string, request: UpdateTaskRequest): Promise<void> {
  await apiClient.put(`/tasks/${id}`, request);
}

/** DELETE /api/tasks/{id} → 204 */
export async function deleteTask(id: string): Promise<void> {
  await apiClient.delete(`/tasks/${id}`);
}

/** PUT /api/tasks/{id}/validate → 200 */
export async function validateTask(id: string): Promise<void> {
  await apiClient.put(`/tasks/${id}/validate`);
}

/** PUT /api/tasks/{id}/reject → 200 (RG-06 : motif obligatoire) */
export async function rejectTask(id: string, request: RejectTaskRequest): Promise<void> {
  await apiClient.put(`/tasks/${id}/reject`, request);
}

/** PUT /api/tasks/{id}/done → 200 */
export async function markAsDone(id: string): Promise<void> {
  await apiClient.put(`/tasks/${id}/done`);
}

// ══════════════════════════════════════════════════════════
//  REQUÊTES
// ══════════════════════════════════════════════════════════

/** GET /api/tasks/me → tâches du GESTIONNAIRE connecté */
export async function getMyTasks(): Promise<Task[]> {
  const { data } = await apiClient.get<Task[]>('/tasks/me');
  return data;
}

/** GET /api/tasks?teamId={uuid} → tâches de l'équipe */
export async function getTeamTasks(teamId: string): Promise<Task[]> {
  const { data } = await apiClient.get<Task[]>('/tasks', { params: { teamId } });
  return data;
}

/** GET /api/tasks/{id} */
export async function getTaskById(id: string): Promise<Task> {
  const { data } = await apiClient.get<Task>(`/tasks/${id}`);
  return data;
}

/** GET /api/tasks/overdue?teamId={uuid} */
export async function getOverdueTasks(teamId: string): Promise<Task[]> {
  const { data } = await apiClient.get<Task[]>('/tasks/overdue', { params: { teamId } });
  return data;
}

/** GET /api/tasks/by-status?status={status} */
export async function getTasksByStatus(status: TaskStatus): Promise<Task[]> {
  const { data } = await apiClient.get<Task[]>('/tasks/by-status', { params: { status } });
  return data;
}
