import { useState, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMyTasks, useDeleteTask, useMarkAsDone } from '@hooks/useTasks';
import { useAuthContext } from '@context/AuthContext';
import {
  STATUS_LABELS, STATUS_BADGE_CLASS, PRIORITY_LABELS, PRIORITY_BADGE_CLASS,
  type TaskStatus,
} from '@app-types/task.types';

/**
 * ══════════════════════════════════════════════════════════════
 *  COMPOSANT — TaskList.tsx (React)
 * ══════════════════════════════════════════════════════════════
 *
 *  Liste des tâches du gestionnaire connecté avec :
 *  → Filtrage par statut (client-side, pas de nouveau appel API)
 *  → Actions : Modifier, Supprimer, Marquer comme fait
 *  → Badges de statut et priorité colorés
 *
 *  DIFFÉRENCE AVEC ANGULAR :
 *  → Angular : @for + signals + computed()
 *  → React   : .map() + useMemo() + useState()
 *
 *  TANSTACK QUERY :
 *  → useMyTasks() gère le loading/error/data automatiquement
 *  → Pas besoin de gérer un state 'isLoading' manuellement
 *
 *  OPTIMISME :
 *  → On n'a pas fait d'optimistic update ici pour la suppression
 *    (comportement plus sûr : attendre la confirmation serveur)
 *  → Pour les KPIs du dashboard Manager, on fait de l'optimistic update
 */

// Options de filtre — correspond aux valeurs backend
const FILTER_OPTIONS: Array<{ value: TaskStatus | 'ALL'; label: string }> = [
  { value: 'ALL',      label: 'Toutes' },
  { value: 'A_FAIRE',  label: STATUS_LABELS.A_FAIRE },
  { value: 'VALIDEE',  label: STATUS_LABELS.VALIDEE },
  { value: 'REJETEE',  label: STATUS_LABELS.REJETEE },
  { value: 'DONE',     label: STATUS_LABELS.DONE },
];

export default function TaskList() {
  const navigate   = useNavigate();
  const { currentUser } = useAuthContext();

  // ── TanStack Query hooks ──────────────────────────────────────
  const { data: tasks, isLoading, isError, error } = useMyTasks();
  const deleteMutation   = useDeleteTask();
  const markDoneMutation = useMarkAsDone();

  // ── État local — filtre de statut ─────────────────────────────
  const [filterStatus, setFilterStatus] = useState<TaskStatus | 'ALL'>('ALL');

  /**
   * useMemo : recalcule filteredTasks seulement quand tasks ou filterStatus change.
   * Équivalent de computed() en Angular.
   */
  const filteredTasks = useMemo(() => {
    if (!tasks) return [];
    if (filterStatus === 'ALL') return tasks;
    return tasks.filter((t) => t.status === filterStatus);
  }, [tasks, filterStatus]);

  // Compteur de tâches en retard — pour l'alerte visuelle
  const overdueCount = useMemo(
    () => tasks?.filter((t) => t.overdue && t.status === 'A_FAIRE').length ?? 0,
    [tasks]
  );

  // ── Handlers ──────────────────────────────────────────────────

  async function handleDelete(id: string, title: string) {
    // Confirmation native du navigateur — Simple, accessible
    if (!window.confirm(`Supprimer la tâche "${title}" ?`)) return;
    try {
      await deleteMutation.mutateAsync(id);
    } catch {
      // L'erreur est affichée via deleteMutation.isError
    }
  }

  async function handleMarkAsDone(id: string) {
    try {
      await markDoneMutation.mutateAsync(id);
    } catch {
      // Idem
    }
  }

  // ── Rendu ─────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-4 text-red-700">
        Erreur lors du chargement : {(error as Error).message}
      </div>
    );
  }

  return (
    <div className="space-y-6">

      {/* ── En-tête ── */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Mes tâches</h1>
          <p className="text-sm text-gray-500 mt-1">
            Bonjour {currentUser?.name} — {tasks?.length ?? 0} tâche(s)
          </p>
        </div>
        <Link
          to="/tasks/new"
          className="inline-flex items-center gap-2 bg-blue-600 hover:bg-blue-700 text-white px-4 py-2 rounded-lg font-medium transition-colors"
        >
          + Nouvelle tâche
        </Link>
      </div>

      {/* ── Alerte retard ── */}
      {overdueCount > 0 && (
        <div className="bg-red-50 border border-red-300 rounded-lg p-3 flex items-center gap-2 text-red-700">
          <span className="text-lg">⏰</span>
          <span className="font-medium">{overdueCount} tâche(s) en retard !</span>
        </div>
      )}

      {/* ── Filtre par statut ── */}
      <div className="flex gap-2 flex-wrap">
        {FILTER_OPTIONS.map(({ value, label }) => (
          <button
            key={value}
            onClick={() => setFilterStatus(value)}
            className={`px-3 py-1.5 rounded-full text-sm font-medium transition-colors ${
              filterStatus === value
                ? 'bg-blue-600 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            {label}
            {value !== 'ALL' && (
              <span className="ml-1.5 bg-white/30 rounded-full px-1.5">
                {tasks?.filter((t) => t.status === value).length ?? 0}
              </span>
            )}
          </button>
        ))}
      </div>

      {/* ── Liste des tâches ── */}
      {filteredTasks.length === 0 ? (
        <div className="text-center py-16 text-gray-400">
          <p className="text-4xl mb-3">📭</p>
          <p className="font-medium">Aucune tâche {filterStatus !== 'ALL' ? 'dans ce statut' : ''}</p>
        </div>
      ) : (
        <div className="space-y-3">
          {filteredTasks.map((task) => (
            <article
              key={task.id}
              className={`bg-white rounded-lg border p-4 hover:shadow-sm transition-shadow ${
                task.overdue && task.status === 'A_FAIRE' ? 'border-red-300' : 'border-gray-200'
              }`}
            >
              <div className="flex items-start justify-between gap-4">

                {/* ── Infos tâche ── */}
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 flex-wrap">
                    <h3 className="font-semibold text-gray-900 truncate">{task.title}</h3>

                    {/* Badge statut */}
                    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_BADGE_CLASS[task.status]}`}>
                      {STATUS_LABELS[task.status]}
                    </span>

                    {/* Badge priorité */}
                    <span className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${PRIORITY_BADGE_CLASS[task.priority]}`}>
                      {PRIORITY_LABELS[task.priority]}
                    </span>

                    {/* Badge retard */}
                    {task.overdue && task.status === 'A_FAIRE' && (
                      <span className="inline-block px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-700">
                        En retard
                      </span>
                    )}
                  </div>

                  {/* Description tronquée */}
                  {task.description && (
                    <p className="text-sm text-gray-500 mt-1 truncate">{task.description}</p>
                  )}

                  {/* Métadonnées */}
                  <div className="flex gap-4 mt-2 text-xs text-gray-400">
                    {task.dueDate && (
                      <span>
                        📅 Échéance : {new Date(task.dueDate).toLocaleDateString('fr-FR')}
                      </span>
                    )}
                    {task.rejectionReason && (
                      <span className="text-red-500">
                        Rejeté : {task.rejectionReason}
                      </span>
                    )}
                  </div>
                </div>

                {/* ── Actions ── */}
                <div className="flex gap-2 shrink-0">
                  {/* Modifier — seulement si A_FAIRE ou REJETEE (RG-07) */}
                  {(task.status === 'A_FAIRE' || task.status === 'REJETEE') && (
                    <button
                      onClick={() => navigate(`/tasks/${task.id}/edit`)}
                      className="p-1.5 rounded text-gray-400 hover:text-blue-600 hover:bg-blue-50 transition-colors"
                      title="Modifier"
                    >
                      ✏️
                    </button>
                  )}

                  {/* Marquer comme fait — seulement si VALIDEE */}
                  {task.status === 'VALIDEE' && (
                    <button
                      onClick={() => handleMarkAsDone(task.id)}
                      disabled={markDoneMutation.isPending}
                      className="p-1.5 rounded text-gray-400 hover:text-green-600 hover:bg-green-50 transition-colors disabled:opacity-50"
                      title="Marquer comme terminée"
                    >
                      ✅
                    </button>
                  )}

                  {/* Supprimer — seulement si A_FAIRE (RG-05) */}
                  {task.status === 'A_FAIRE' && (
                    <button
                      onClick={() => handleDelete(task.id, task.title)}
                      disabled={deleteMutation.isPending}
                      className="p-1.5 rounded text-gray-400 hover:text-red-600 hover:bg-red-50 transition-colors disabled:opacity-50"
                      title="Supprimer"
                    >
                      🗑️
                    </button>
                  )}
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
    </div>
  );
}
