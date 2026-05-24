import { useState, useMemo } from 'react';
import { useTeamTasks, useValidateTask, useRejectTask } from '@hooks/useTasks';
import { useAuthContext } from '@context/AuthContext';
import { STATUS_LABELS, PRIORITY_BADGE_CLASS, PRIORITY_LABELS } from '@types/task.types';
import type { Task } from '@types/task.types';

/**
 * ══════════════════════════════════════════════════════════════
 *  COMPOSANT — ManagerDashboard.tsx (React)
 * ══════════════════════════════════════════════════════════════
 *
 *  Tableau de bord du MANAGER avec :
 *  → KPIs : total, en attente, en retard, terminées
 *  → File d'attente de validation (tâches A_FAIRE)
 *  → Actions rapides : Valider / Rejeter (avec motif obligatoire)
 *
 *  DIFFÉRENCE AVEC ANGULAR :
 *  → Angular : Signals + computed() + @if/@for dans le template
 *  → React   : useState() + useMemo() + JSX ternaires
 *
 *  OPTIMISTIC UPDATE :
 *  → Quand le Manager valide une tâche, on met à jour l'UI localement
 *    AVANT que le serveur réponde → UX plus fluide
 *  → Si le serveur répond avec une erreur, TanStack Query invalide
 *    le cache et le refetch remet les données correctes
 *  → Attention : pas recommandé pour toutes les actions
 *    (ici OK car la validation échoue rarement)
 *
 *  NOTE SUR LE teamId :
 *  → En prod : le teamId serait dans les claims JWT du Manager
 *  → Ici pour la démo : on utilise le premier teamId trouvé dans les tâches,
 *    ou un UUID hardcodé si aucune tâche n'est chargée
 */

// ── Sous-composant : Carte KPI ────────────────────────────────

interface KpiCardProps {
  label: string;
  value: number;
  color: 'blue' | 'yellow' | 'red' | 'green';
  icon: string;
}

function KpiCard({ label, value, color, icon }: KpiCardProps) {
  const colorMap = {
    blue:   'bg-blue-50 text-blue-700 border-blue-200',
    yellow: 'bg-yellow-50 text-yellow-700 border-yellow-200',
    red:    'bg-red-50 text-red-700 border-red-200',
    green:  'bg-green-50 text-green-700 border-green-200',
  };

  return (
    <div className={`rounded-xl border p-5 ${colorMap[color]}`}>
      <div className="text-2xl mb-2">{icon}</div>
      <div className="text-3xl font-bold">{value}</div>
      <div className="text-sm font-medium mt-1 opacity-80">{label}</div>
    </div>
  );
}

// ── Sous-composant : Modal de rejet ────────────────────────────

interface RejectModalProps {
  task: Task;
  onConfirm: (reason: string) => void;
  onCancel: () => void;
  isPending: boolean;
}

function RejectModal({ task, onConfirm, onCancel, isPending }: RejectModalProps) {
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);

  const isValid = reason.trim().length >= 10;

  return (
    /* Overlay semi-transparent */
    <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md p-6 space-y-4">

        <h3 className="text-lg font-semibold text-gray-900">
          Rejeter la tâche
        </h3>

        <p className="text-sm text-gray-500">
          Tâche : <span className="font-medium text-gray-700">{task.title}</span>
        </p>

        {/* Motif de rejet — RG-06 : obligatoire, minimum 10 caractères */}
        <div>
          <label htmlFor="reason" className="block text-sm font-medium text-gray-700 mb-1">
            Motif de rejet <span className="text-red-500">*</span>
          </label>
          <textarea
            id="reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            onBlur={() => setTouched(true)}
            rows={3}
            placeholder="Expliquez pourquoi cette tâche est rejetée (min. 10 caractères)..."
            className={`w-full rounded-lg border px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-red-500 resize-none ${
              touched && !isValid ? 'border-red-400' : 'border-gray-300'
            }`}
          />
          {touched && !isValid && (
            <p className="mt-1 text-xs text-red-600">
              Le motif doit faire au moins 10 caractères (RG-06)
            </p>
          )}
          <p className="mt-1 text-xs text-gray-400">{reason.length} caractères</p>
        </div>

        <div className="flex gap-3 pt-2">
          <button
            onClick={() => {
              setTouched(true);
              if (isValid) onConfirm(reason.trim());
            }}
            disabled={isPending}
            className="flex-1 bg-red-600 hover:bg-red-700 disabled:bg-red-400 text-white font-medium py-2 rounded-lg transition-colors"
          >
            {isPending ? 'Rejet...' : 'Confirmer le rejet'}
          </button>
          <button
            onClick={onCancel}
            disabled={isPending}
            className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 transition-colors"
          >
            Annuler
          </button>
        </div>
      </div>
    </div>
  );
}

// ── Composant principal ────────────────────────────────────────

export default function ManagerDashboard() {
  const { currentUser } = useAuthContext();

  /**
   * teamId : en production, extrait des claims JWT du Manager.
   * Pour la démo, on utilise une constante.
   * À remplacer par : currentUser?.teamId ?? ''
   */
  const teamId = (currentUser as Record<string, unknown> | null)?.['teamId'] as string ?? '';

  // ── TanStack Query ────────────────────────────────────────────
  const { data: tasks, isLoading, isError, error } = useTeamTasks(teamId);
  const validateMutation = useValidateTask();
  const rejectMutation   = useRejectTask();

  // ── État local ────────────────────────────────────────────────
  const [rejectingTask, setRejectingTask] = useState<Task | null>(null);

  // ── KPIs calculés ────────────────────────────────────────────

  const kpis = useMemo(() => {
    if (!tasks) return { total: 0, pending: 0, overdue: 0, done: 0 };
    return {
      total:   tasks.length,
      pending: tasks.filter((t) => t.status === 'A_FAIRE').length,
      overdue: tasks.filter((t) => t.overdue && t.status === 'A_FAIRE').length,
      done:    tasks.filter((t) => t.status === 'DONE').length,
    };
  }, [tasks]);

  // File d'attente : tâches A_FAIRE triées par priorité puis par date
  const pendingTasks = useMemo(() => {
    if (!tasks) return [];
    const priorityOrder = { URGENTE: 0, HAUTE: 1, NORMALE: 2, BASSE: 3 };
    return tasks
      .filter((t) => t.status === 'A_FAIRE')
      .sort((a, b) => {
        const pDiff = priorityOrder[a.priority] - priorityOrder[b.priority];
        if (pDiff !== 0) return pDiff;
        // Tâches en retard en premier
        if (a.overdue && !b.overdue) return -1;
        if (!a.overdue && b.overdue) return 1;
        return 0;
      });
  }, [tasks]);

  // ── Handlers ─────────────────────────────────────────────────

  async function handleValidate(taskId: string) {
    await validateMutation.mutateAsync(taskId);
  }

  async function handleRejectConfirm(reason: string) {
    if (!rejectingTask) return;
    await rejectMutation.mutateAsync({
      id: rejectingTask.id,
      request: { rejectionReason: reason },
    });
    setRejectingTask(null);
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
        Erreur : {(error as Error).message}
      </div>
    );
  }

  return (
    <div className="space-y-8">

      {/* ── En-tête ── */}
      <div>
        <h1 className="text-2xl font-bold text-gray-900">Tableau de bord Manager</h1>
        <p className="text-sm text-gray-500 mt-1">
          Bonjour {currentUser?.name} — vue d'ensemble de l'équipe
        </p>
      </div>

      {/* ── KPIs ── */}
      <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
        <KpiCard label="Total tâches"    value={kpis.total}   color="blue"   icon="📋" />
        <KpiCard label="En attente"      value={kpis.pending} color="yellow" icon="⏳" />
        <KpiCard label="En retard"       value={kpis.overdue} color="red"    icon="🔴" />
        <KpiCard label="Terminées"       value={kpis.done}    color="green"  icon="✅" />
      </div>

      {/* ── File d'attente de validation ── */}
      <div>
        <h2 className="text-lg font-semibold text-gray-900 mb-4">
          File d'attente de validation
          {kpis.pending > 0 && (
            <span className="ml-2 bg-yellow-100 text-yellow-700 text-sm px-2 py-0.5 rounded-full">
              {kpis.pending}
            </span>
          )}
        </h2>

        {pendingTasks.length === 0 ? (
          <div className="text-center py-12 bg-white rounded-lg border border-gray-200 text-gray-400">
            <p className="text-3xl mb-2">🎉</p>
            <p className="font-medium">Aucune tâche en attente de validation</p>
          </div>
        ) : (
          <div className="space-y-3">
            {pendingTasks.map((task) => (
              <div
                key={task.id}
                className={`bg-white rounded-lg border p-4 ${
                  task.overdue ? 'border-red-300 bg-red-50/30' : 'border-gray-200'
                }`}
              >
                <div className="flex items-center justify-between gap-4">

                  {/* Infos */}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-medium text-gray-900">{task.title}</span>

                      {/* Badge priorité */}
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${PRIORITY_BADGE_CLASS[task.priority]}`}>
                        {PRIORITY_LABELS[task.priority]}
                      </span>

                      {/* Badge retard */}
                      {task.overdue && (
                        <span className="px-2 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-700">
                          En retard
                        </span>
                      )}
                    </div>

                    {task.description && (
                      <p className="text-sm text-gray-500 mt-1 truncate">{task.description}</p>
                    )}

                    <div className="flex gap-4 mt-1.5 text-xs text-gray-400">
                      {task.dueDate && (
                        <span>📅 {new Date(task.dueDate).toLocaleDateString('fr-FR')}</span>
                      )}
                      <span>
                        Statut : {STATUS_LABELS[task.status]}
                      </span>
                    </div>
                  </div>

                  {/* Actions rapides */}
                  <div className="flex gap-2 shrink-0">
                    <button
                      onClick={() => handleValidate(task.id)}
                      disabled={validateMutation.isPending}
                      className="inline-flex items-center gap-1 bg-green-600 hover:bg-green-700 disabled:bg-green-400 text-white text-sm font-medium px-3 py-1.5 rounded-lg transition-colors"
                    >
                      ✓ Valider
                    </button>
                    <button
                      onClick={() => setRejectingTask(task)}
                      disabled={rejectMutation.isPending}
                      className="inline-flex items-center gap-1 bg-red-600 hover:bg-red-700 disabled:bg-red-400 text-white text-sm font-medium px-3 py-1.5 rounded-lg transition-colors"
                    >
                      ✗ Rejeter
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ── Récapitulatif de toutes les tâches ── */}
      {tasks && tasks.length > 0 && (
        <div>
          <h2 className="text-lg font-semibold text-gray-900 mb-4">Toutes les tâches</h2>
          <div className="bg-white rounded-lg border border-gray-200 overflow-hidden">
            <table className="w-full text-sm">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Titre</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Statut</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Priorité</th>
                  <th className="text-left px-4 py-3 font-medium text-gray-600">Échéance</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {tasks.map((task) => (
                  <tr key={task.id} className="hover:bg-gray-50 transition-colors">
                    <td className="px-4 py-3 font-medium text-gray-900 max-w-xs truncate">
                      {task.title}
                    </td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${
                        task.status === 'A_FAIRE'  ? 'bg-blue-100 text-blue-800'  :
                        task.status === 'VALIDEE'  ? 'bg-green-100 text-green-800' :
                        task.status === 'REJETEE'  ? 'bg-red-100 text-red-800'    :
                        'bg-gray-100 text-gray-600'
                      }`}>
                        {STATUS_LABELS[task.status]}
                      </span>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-0.5 rounded-full text-xs font-medium ${PRIORITY_BADGE_CLASS[task.priority]}`}>
                        {PRIORITY_LABELS[task.priority]}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-500">
                      {task.dueDate
                        ? new Date(task.dueDate).toLocaleDateString('fr-FR')
                        : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* ── Modal de rejet ── */}
      {rejectingTask && (
        <RejectModal
          task={rejectingTask}
          onConfirm={handleRejectConfirm}
          onCancel={() => setRejectingTask(null)}
          isPending={rejectMutation.isPending}
        />
      )}
    </div>
  );
}
