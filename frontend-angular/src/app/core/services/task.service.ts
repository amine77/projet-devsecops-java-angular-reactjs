import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '@env/environment';
import {
  Task,
  CreateTaskRequest,
  UpdateTaskRequest,
  RejectTaskRequest,
  TaskStatus,
} from '@core/models/task.model';

/**
 * ══════════════════════════════════════════════════════════════
 *  SERVICE — TaskService
 * ══════════════════════════════════════════════════════════════
 *
 *  Encapsule tous les appels HTTP vers l'API /api/tasks.
 *
 *  RESPONSABILITÉS :
 *  → Construire les requêtes HTTP (URL, headers, body)
 *  → Typer les réponses (Task, Task[], void)
 *  → Le JWT est injecté automatiquement par jwtInterceptor
 *  → Les erreurs sont interceptées par errorInterceptor
 *
 *  CE QUE CE SERVICE NE FAIT PAS :
 *  → Pas de gestion d'état (les composants/signals font ça)
 *  → Pas de logique métier (le backend est la source de vérité)
 *  → Pas de cache (géré par le backend via Redis)
 *
 *  OBSERVABLES vs PROMISES :
 *  → HttpClient retourne des Observables
 *  → Avantages : annulables, composables avec rxjs, gestion d'erreur standardisée
 *  → Les composants utilisent async pipe ou subscribe()
 *
 *  PATTERN DE RETOUR :
 *  → GET → Observable<Task[]> ou Observable<Task>
 *  → POST → Observable<void> (le Location header est dans la réponse HTTP brute)
 *  → PUT/DELETE → Observable<void>
 *
 *  NOTE SUR L'ID DANS POST :
 *  → POST /api/tasks retourne 201 + Location header
 *  → Si on a besoin de l'ID, on peut utiliser response.headers.get('Location')
 *    via { observe: 'response' } dans les options HttpClient
 * ══════════════════════════════════════════════════════════════
 */
@Injectable({ providedIn: 'root' })
export class TaskService {

  private readonly http   = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  // ══════════════════════════════════════════════════════════
  //  COMMANDES (écriture)
  // ══════════════════════════════════════════════════════════

  /**
   * Crée une tâche.
   * POST /api/tasks → 201 Created + Location header
   *
   * On retourne la réponse complète pour accéder au Location header
   * et extraire l'ID de la tâche créée.
   */
  createTask(request: CreateTaskRequest): Observable<string> {
    return new Observable(observer => {
      this.http.post(
        `${this.apiUrl}/tasks`,
        request,
        { observe: 'response' }
      ).subscribe({
        next: response => {
          // Extraire l'UUID depuis le Location header : .../api/tasks/{uuid}
          const location = response.headers.get('Location') ?? '';
          const id = location.split('/').pop() ?? '';
          observer.next(id);
          observer.complete();
        },
        error: err => observer.error(err),
      });
    });
  }

  /**
   * Modifie une tâche.
   * PUT /api/tasks/{id} → 204 No Content
   */
  updateTask(id: string, request: UpdateTaskRequest): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/tasks/${id}`, request);
  }

  /**
   * Supprime une tâche.
   * DELETE /api/tasks/{id} → 204 No Content
   * RG-05 : uniquement si A_FAIRE
   */
  deleteTask(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/tasks/${id}`);
  }

  /**
   * Valide une tâche : A_FAIRE → VALIDEE.
   * PUT /api/tasks/{id}/validate → 200 OK
   */
  validateTask(id: string): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/tasks/${id}/validate`, {});
  }

  /**
   * Rejette une tâche avec motif obligatoire.
   * PUT /api/tasks/{id}/reject → 200 OK
   * RG-06 : motif obligatoire
   */
  rejectTask(id: string, request: RejectTaskRequest): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/tasks/${id}/reject`, request);
  }

  /**
   * Marque une tâche comme terminée.
   * PUT /api/tasks/{id}/done → 200 OK
   */
  markAsDone(id: string): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/tasks/${id}/done`, {});
  }

  // ══════════════════════════════════════════════════════════
  //  REQUÊTES (lecture)
  // ══════════════════════════════════════════════════════════

  /**
   * Tâches du GESTIONNAIRE connecté.
   * GET /api/tasks/me → 200 OK + liste JSON
   * Utilise le Cache-Aside côté serveur (Redis).
   */
  getMyTasks(): Observable<Task[]> {
    return this.http.get<Task[]>(`${this.apiUrl}/tasks/me`);
  }

  /**
   * Tâches d'une équipe (vue Manager).
   * GET /api/tasks?teamId={uuid} → 200 OK + liste JSON
   */
  getTeamTasks(teamId: string): Observable<Task[]> {
    const params = new HttpParams().set('teamId', teamId);
    return this.http.get<Task[]>(`${this.apiUrl}/tasks`, { params });
  }

  /**
   * Détail d'une tâche.
   * GET /api/tasks/{id} → 200 OK + tâche JSON
   */
  getTaskById(id: string): Observable<Task> {
    return this.http.get<Task>(`${this.apiUrl}/tasks/${id}`);
  }

  /**
   * Tâches en retard pour une équipe.
   * GET /api/tasks/overdue?teamId={uuid}
   */
  getOverdueTasks(teamId: string): Observable<Task[]> {
    const params = new HttpParams().set('teamId', teamId);
    return this.http.get<Task[]>(`${this.apiUrl}/tasks/overdue`, { params });
  }

  /**
   * Tâches filtrées par statut.
   * GET /api/tasks/by-status?status={status}
   */
  getTasksByStatus(status: TaskStatus): Observable<Task[]> {
    const params = new HttpParams().set('status', status);
    return this.http.get<Task[]>(`${this.apiUrl}/tasks/by-status`, { params });
  }
}
