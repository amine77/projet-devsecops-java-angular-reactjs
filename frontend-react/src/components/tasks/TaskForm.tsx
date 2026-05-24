import { useState, useEffect } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTask, useCreateTask, useUpdateTask } from '@hooks/useTasks';
import type { Priority, CreateTaskRequest } from '@app-types/task.types';

/**
 * ══════════════════════════════════════════════════════════════
 *  COMPOSANT — TaskForm.tsx (React)
 * ══════════════════════════════════════════════════════════════
 *
 *  Formulaire de création ET de modification de tâche.
 *  Dual mode : /tasks/new → création | /tasks/:id/edit → édition
 *
 *  DIFFÉRENCE AVEC ANGULAR :
 *  → Angular : ReactiveFormsModule + FormBuilder + FormGroup
 *    Puissant mais verbeux, nécessite des imports NgModule
 *  → React   : State local (useState) + événements natifs
 *    Plus simple, adapté pour des formulaires peu complexes
 *    Pour des formulaires complexes : react-hook-form / Formik
 *
 *  VALIDATION :
 *  → Validation HTML5 native (required, min, max, minLength)
 *  → Validation double côté serveur (Bean Validation Java)
 *  → La date min = aujourd'hui (RG : pas de date dans le passé)
 *
 *  PATTERN :
 *  → Mode création : pas de prefill, POST /api/tasks
 *  → Mode édition : précharge les données, PUT /api/tasks/{id}
 *  → useTask(id) est 'disabled' si id est vide (mode création)
 */

// Options de priorité avec labels FR
const PRIORITY_OPTIONS: Array<{ value: Priority; label: string }> = [
  { value: 'BASSE',   label: 'Basse' },
  { value: 'NORMALE', label: 'Normale' },
  { value: 'HAUTE',   label: 'Haute' },
  { value: 'URGENTE', label: 'Urgente' },
];

// Date d'aujourd'hui au format YYYY-MM-DD (pour l'attribut min du date input)
function todayISO(): string {
  return new Date().toISOString().split('T')[0];
}

export default function TaskForm() {
  const navigate       = useNavigate();
  const { id }         = useParams<{ id?: string }>(); // undefined en mode création
  const isEditMode     = !!id;

  // ── Données existantes (mode édition uniquement) ──────────────
  const { data: existingTask, isLoading: isLoadingTask } = useTask(id ?? '');

  // ── Mutations TanStack Query ──────────────────────────────────
  const createMutation = useCreateTask();
  const updateMutation = useUpdateTask();

  // ── État local du formulaire ──────────────────────────────────
  const [title,       setTitle]       = useState('');
  const [description, setDescription] = useState('');
  const [priority,    setPriority]    = useState<Priority>('NORMALE');
  const [dueDate,     setDueDate]     = useState('');
  const [errors,      setErrors]      = useState<Record<string, string>>({});

  /**
   * Pré-remplissage du formulaire en mode édition.
   * useEffect avec [existingTask] : se relance quand les données arrivent.
   * Équivalent de ngOnInit + patchValue() en Angular.
   */
  useEffect(() => {
    if (existingTask) {
      setTitle(existingTask.title);
      setDescription(existingTask.description ?? '');
      setPriority(existingTask.priority);
      setDueDate(existingTask.dueDate ?? '');
    }
  }, [existingTask]);

  // ── Validation locale ─────────────────────────────────────────

  function validate(): boolean {
    const newErrors: Record<string, string> = {};

    if (!title.trim()) {
      newErrors['title'] = 'Le titre est obligatoire';
    } else if (title.trim().length > 200) {
      newErrors['title'] = 'Le titre ne doit pas dépasser 200 caractères';
    }

    if (description.length > 2000) {
      newErrors['description'] = 'La description ne doit pas dépasser 2000 caractères';
    }

    if (dueDate && dueDate < todayISO()) {
      newErrors['dueDate'] = "La date d'échéance doit être dans le futur";
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }

  // ── Soumission ────────────────────────────────────────────────

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!validate()) return;

    const request: CreateTaskRequest = {
      title:       title.trim(),
      description: description.trim() || undefined,
      priority,
      dueDate:     dueDate || undefined,
    };

    try {
      if (isEditMode && id) {
        await updateMutation.mutateAsync({ id, request });
        navigate('/tasks');
      } else {
        await createMutation.mutateAsync(request);
        navigate('/tasks');
      }
    } catch (err) {
      // L'erreur serveur est affichée via mutation.error
      console.error('Erreur lors de la soumission :', err);
    }
  }

  // ── Rendu ─────────────────────────────────────────────────────

  // Chargement en mode édition (attente des données existantes)
  if (isEditMode && isLoadingTask) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600" />
      </div>
    );
  }

  const isPending  = createMutation.isPending || updateMutation.isPending;
  const mutError   = createMutation.error ?? updateMutation.error;

  return (
    <div className="max-w-2xl mx-auto">

      {/* ── En-tête ── */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900">
          {isEditMode ? 'Modifier la tâche' : 'Nouvelle tâche'}
        </h1>
        <p className="text-sm text-gray-500 mt-1">
          {isEditMode
            ? 'Modifiez les informations de la tâche'
            : 'Renseignez les informations de la nouvelle tâche'}
        </p>
      </div>

      {/* ── Erreur serveur ── */}
      {mutError && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-4 mb-6 text-red-700 text-sm">
          {(mutError as Error).message || 'Une erreur est survenue'}
        </div>
      )}

      {/* ── Formulaire ── */}
      <form onSubmit={handleSubmit} noValidate className="bg-white rounded-lg border border-gray-200 p-6 space-y-6">

        {/* Titre */}
        <div>
          <label htmlFor="title" className="block text-sm font-medium text-gray-700 mb-1">
            Titre <span className="text-red-500">*</span>
          </label>
          <input
            id="title"
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            maxLength={200}
            placeholder="Ex : Préparer le rapport mensuel"
            className={`w-full rounded-lg border px-3 py-2 text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 ${
              errors['title'] ? 'border-red-400' : 'border-gray-300'
            }`}
          />
          {errors['title'] && (
            <p className="mt-1 text-xs text-red-600">{errors['title']}</p>
          )}
          <p className="mt-1 text-xs text-gray-400">{title.length}/200</p>
        </div>

        {/* Description */}
        <div>
          <label htmlFor="description" className="block text-sm font-medium text-gray-700 mb-1">
            Description
          </label>
          <textarea
            id="description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={4}
            maxLength={2000}
            placeholder="Description optionnelle de la tâche..."
            className={`w-full rounded-lg border px-3 py-2 text-gray-900 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-blue-500 resize-none ${
              errors['description'] ? 'border-red-400' : 'border-gray-300'
            }`}
          />
          {errors['description'] && (
            <p className="mt-1 text-xs text-red-600">{errors['description']}</p>
          )}
          <p className="mt-1 text-xs text-gray-400">{description.length}/2000</p>
        </div>

        {/* Priorité + Date d'échéance — sur la même ligne */}
        <div className="grid grid-cols-2 gap-4">

          {/* Priorité */}
          <div>
            <label htmlFor="priority" className="block text-sm font-medium text-gray-700 mb-1">
              Priorité <span className="text-red-500">*</span>
            </label>
            <select
              id="priority"
              value={priority}
              onChange={(e) => setPriority(e.target.value as Priority)}
              className="w-full rounded-lg border border-gray-300 px-3 py-2 text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {PRIORITY_OPTIONS.map(({ value, label }) => (
                <option key={value} value={value}>{label}</option>
              ))}
            </select>
          </div>

          {/* Date d'échéance */}
          <div>
            <label htmlFor="dueDate" className="block text-sm font-medium text-gray-700 mb-1">
              Date d'échéance
            </label>
            <input
              id="dueDate"
              type="date"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
              min={todayISO()} // Empêche la sélection d'une date passée
              className={`w-full rounded-lg border px-3 py-2 text-gray-900 focus:outline-none focus:ring-2 focus:ring-blue-500 ${
                errors['dueDate'] ? 'border-red-400' : 'border-gray-300'
              }`}
            />
            {errors['dueDate'] && (
              <p className="mt-1 text-xs text-red-600">{errors['dueDate']}</p>
            )}
          </div>
        </div>

        {/* ── Boutons ── */}
        <div className="flex gap-3 pt-2">
          <button
            type="submit"
            disabled={isPending}
            className="flex-1 bg-blue-600 hover:bg-blue-700 disabled:bg-blue-400 text-white font-medium py-2 px-4 rounded-lg transition-colors"
          >
            {isPending
              ? (isEditMode ? 'Mise à jour...' : 'Création...')
              : (isEditMode ? 'Enregistrer les modifications' : 'Créer la tâche')}
          </button>

          <button
            type="button"
            onClick={() => navigate(-1)} // Retour à la page précédente
            className="px-4 py-2 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 transition-colors"
          >
            Annuler
          </button>
        </div>
      </form>
    </div>
  );
}
