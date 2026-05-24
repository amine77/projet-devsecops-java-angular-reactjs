import {
  useQuery,
  useMutation,
  useQueryClient,
  type UseQueryResult,
  type UseMutationResult,
} from '@tanstack/react-query';
import {
  getMyTasks,
  getTeamTasks,
  getTaskById,
  getOverdueTasks,
  getTasksByStatus,
  createTask,
  updateTask,
  deleteTask,
  validateTask,
  rejectTask,
  markAsDone,
} from '@api/task.api';
import type {
  Task, TaskStatus, CreateTaskRequest, UpdateTaskRequest, RejectTaskRequest,
} from '@types/task.types';

/**
 * ══════════════════════════════════════════════════════════════
 *  HOOKS TANSTACK QUERY — useTasks.ts (React)
 * ══════════════════════════════════════════════════════════════
 *
 *  Encapsule toutes les interactions avec l'API tasks.
 *
 *  TANSTACK QUERY vs ANGULAR HTTPCLIENT :
 *  → Angular : HttpClient retourne des Observables (RxJS)
 *    Le composant s'abonne avec `async pipe` ou `.subscribe()`
 *  → React : TanStack Query retourne des hooks qui gèrent
 *    automatiquement : loading, error, cache, refetch, stale data
 *
 *  AVANTAGES TANSTACK QUERY :
 *  → Cache automatique avec clé (QueryKey)
 *  → Déduplication des requêtes identiques
 *  → Background refetch (données fraîches sans loading spinner)
 *  → Invalidation granulaire (invalider seulement les tâches d'une équipe)
 *  → Optimistic updates (mise à jour UI avant confirmation serveur)
 *  → Retry automatique en cas d'erreur réseau
 *
 *  QUERY KEYS :
 *  → ['tasks', 'mine']            → tâches du gestionnaire connecté
 *  → ['tasks', 'team', teamId]    → tâches d'une équipe
 *  → ['tasks', 'id', id]          → une seule tâche
 *  → ['tasks', 'overdue', teamId] → tâches en retard
 *  → ['tasks', 'status', status]  → tâches par statut
 *
 *  INVALIDATION :
 *  Après chaque mutation, on invalide les queries concernées pour
 *  forcer un refetch et avoir des données à jour.
 */

// ──────────────────────────────────────────────────────────────
//  CONSTANTES — Query Keys
//  Centraliser les clés évite les typos et facilite l'invalidation.
// ──────────────────────────────────────────────────────────────

export const taskKeys = {
  all:            ['tasks']                                  as const,
  mine:           ['tasks', 'mine']                          as const,
  team:     (id: string) => ['tasks', 'team', id]            as const,
  detail:   (id: string) => ['tasks', 'id', id]              as const,
  overdue:  (id: string) => ['tasks', 'overdue', id]         as const,
  byStatus: (s: TaskStatus) => ['tasks', 'status', s]        as const,
} as const;

// ──────────────────────────────────────────────────────────────
//  QUERIES (lecture)
// ──────────────────────────────────────────────────────────────

/**
 * Récupère les tâches du gestionnaire connecté.
 * Utilisé sur la page /tasks (vue GESTIONNAIRE).
 *
 * staleTime: 30s → les données ne sont pas re-fetched avant 30s
 * Évite les requêtes inutiles lors de la navigation
 */
export function useMyTasks(): UseQueryResult<Task[]> {
  return useQuery({
    queryKey: taskKeys.mine,
    queryFn: getMyTasks,
    staleTime: 30_000,
  });
}

/**
 * Récupère les tâches d'une équipe.
 * enabled: false si teamId est vide → n'exécute pas la requête
 */
export function useTeamTasks(teamId: string): UseQueryResult<Task[]> {
  return useQuery({
    queryKey: taskKeys.team(teamId),
    queryFn: () => getTeamTasks(teamId),
    enabled: !!teamId,
    staleTime: 30_000,
  });
}

/** Récupère une tâche par son ID. */
export function useTask(id: string): UseQueryResult<Task> {
  return useQuery({
    queryKey: taskKeys.detail(id),
    queryFn: () => getTaskById(id),
    enabled: !!id,
    staleTime: 30_000,
  });
}

/** Récupère les tâches en retard pour une équipe. */
export function useOverdueTasks(teamId: string): UseQueryResult<Task[]> {
  return useQuery({
    queryKey: taskKeys.overdue(teamId),
    queryFn: () => getOverdueTasks(teamId),
    enabled: !!teamId,
    staleTime: 60_000, // Moins fréquent — les retards ne changent pas rapidement
  });
}

/** Récupère les tâches par statut. */
export function useTasksByStatus(status: TaskStatus): UseQueryResult<Task[]> {
  return useQuery({
    queryKey: taskKeys.byStatus(status),
    queryFn: () => getTasksByStatus(status),
    staleTime: 30_000,
  });
}

// ──────────────────────────────────────────────────────────────
//  MUTATIONS (écriture)
//  Chaque mutation invalide les queries concernées après succès.
// ──────────────────────────────────────────────────────────────

/**
 * Crée une nouvelle tâche.
 * onSuccess : invalide taskKeys.mine pour refetch automatique.
 */
export function useCreateTask(): UseMutationResult<string, Error, CreateTaskRequest> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createTask,
    onSuccess: () => {
      // Invalide toutes les listes de tâches → refetch au prochain rendu
      queryClient.invalidateQueries({ queryKey: taskKeys.all });
    },
  });
}

/**
 * Met à jour une tâche existante.
 * Invalide la tâche spécifique ET toutes les listes.
 */
export function useUpdateTask(): UseMutationResult<void, Error, { id: string; request: UpdateTaskRequest }> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, request }) => updateTask(id, request),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: taskKeys.detail(id) });
      queryClient.invalidateQueries({ queryKey: taskKeys.all });
    },
  });
}

/**
 * Supprime une tâche.
 * RG-05 : seules les tâches A_FAIRE peuvent être supprimées (vérifié côté serveur).
 */
export function useDeleteTask(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: deleteTask,
    onSuccess: (_, id) => {
      // Supprime du cache local la tâche effacée
      queryClient.removeQueries({ queryKey: taskKeys.detail(id) });
      queryClient.invalidateQueries({ queryKey: taskKeys.all });
    },
  });
}

/**
 * Valide une tâche (A_FAIRE → VALIDEE).
 * RG-04 : seul un MANAGER ou SUPER_ADMINISTRATEUR peut valider.
 */
export function useValidateTask(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: validateTask,
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: taskKeys.detail(id) });
      queryClient.invalidateQueries({ queryKey: taskKeys.all });
    },
  });
}

/**
 * Rejette une tâche avec un motif obligatoire.
 * RG-06 : le motif est obligatoire → validé ici ET côté serveur.
 */
export function useRejectTask(): UseMutationResult<void, Error, { id: string; request: RejectTaskRequest }> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ id, request }) => rejectTask(id, request),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: taskKeys.detail(id) });
      queryClient.invalidateQueries({ queryKey: taskKeys.all });
    },
  });
}

/**
 * Marque une tâche comme terminée (VALIDEE → DONE).
 */
export function useMarkAsDone(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: markAsDone,
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: taskKeys.detail(id) });
      queryClient.invalidateQueries({ queryKey: taskKeys.all });
    },
  });
}
