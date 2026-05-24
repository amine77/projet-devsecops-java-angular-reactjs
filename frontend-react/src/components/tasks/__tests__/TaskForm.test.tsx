import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import TaskForm from '../TaskForm';

/**
 * ══════════════════════════════════════════════════════════════
 *  TEST UNITAIRE — TaskForm.test.tsx (React / Vitest)
 * ══════════════════════════════════════════════════════════════
 *
 *  VITEST + @testing-library/react :
 *  → Vitest simule un navigateur via jsdom (configuré dans vite.config.ts)
 *  → @testing-library/react : render(), screen, fireEvent, waitFor
 *  → Approche "boîte noire" : on teste ce que l'utilisateur voit
 *
 *  DIFFÉRENCE AVEC ANGULAR :
 *  → Angular : TestBed.configureTestingModule() + fixture + detectChanges()
 *    Verbose mais très intégré (vérifie les templates Angular)
 *  → React   : render(<Component />) + assertions sur le DOM
 *    Plus simple, plus proche du comportement réel du navigateur
 *
 *  MOCKS :
 *  → vi.mock() (équivalent jest.mock()) intercepte les imports
 *  → On mock les hooks TanStack Query pour isoler le composant
 *  → On mock react-router-dom pour contrôler les params d'URL
 */

// ── Mock de l'AuthContext ─────────────────────────────────────

vi.mock('@context/AuthContext', () => ({
  useAuthContext: () => ({
    currentUser: { id: 'user-1', email: 'test@test.com', name: 'Gestionnaire Test', role: 'GESTIONNAIRE' },
    isGestionnaire: true,
    isManager: false,
    canCreateTask: true,
  }),
}));

// ── Mock des hooks TanStack Query ─────────────────────────────

const mockMutateAsync = vi.fn().mockResolvedValue('new-task-id');

vi.mock('@hooks/useTasks', () => ({
  useTask: () => ({ data: undefined, isLoading: false }),
  useCreateTask: () => ({
    mutateAsync: mockMutateAsync,
    isPending: false,
    error: null,
  }),
  useUpdateTask: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
    error: null,
  }),
}));

// ── Helpers de test ────────────────────────────────────────────

/** Crée un QueryClient isolé pour chaque test (évite les pollutions de cache) */
function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
}

/** Wrapper : fournit les providers nécessaires à TaskForm */
function renderTaskForm(route = '/tasks/new') {
  const queryClient = createTestQueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path="/tasks/new"      element={<TaskForm />} />
          <Route path="/tasks/:id/edit" element={<TaskForm />} />
          <Route path="/tasks"          element={<div>Liste des tâches</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

// ── Tests ─────────────────────────────────────────────────────

describe('TaskForm', () => {

  beforeEach(() => {
    mockMutateAsync.mockClear();
  });

  describe('Mode création (/tasks/new)', () => {

    it('affiche le formulaire de création vide', () => {
      renderTaskForm('/tasks/new');

      expect(screen.getByText('Nouvelle tâche')).toBeInTheDocument();
      expect(screen.getByLabelText(/titre/i)).toHaveValue('');
      expect(screen.getByLabelText(/priorité/i)).toHaveValue('NORMALE');
      expect(screen.getByRole('button', { name: /créer la tâche/i })).toBeInTheDocument();
    });

    it('affiche une erreur si le titre est vide à la soumission', async () => {
      renderTaskForm('/tasks/new');

      // Soumettre sans remplir le titre
      fireEvent.click(screen.getByRole('button', { name: /créer la tâche/i }));

      await waitFor(() => {
        expect(screen.getByText(/le titre est obligatoire/i)).toBeInTheDocument();
      });

      // La mutation ne doit pas être appelée
      expect(mockMutateAsync).not.toHaveBeenCalled();
    });

    it('soumet le formulaire avec des données valides', async () => {
      renderTaskForm('/tasks/new');

      // Remplir le titre
      fireEvent.change(screen.getByLabelText(/titre/i), {
        target: { value: 'Ma nouvelle tâche de test' },
      });

      // Changer la priorité
      fireEvent.change(screen.getByLabelText(/priorité/i), {
        target: { value: 'HAUTE' },
      });

      // Soumettre
      fireEvent.click(screen.getByRole('button', { name: /créer la tâche/i }));

      await waitFor(() => {
        expect(mockMutateAsync).toHaveBeenCalledWith({
          title: 'Ma nouvelle tâche de test',
          priority: 'HAUTE',
          description: undefined,
          dueDate: undefined,
        });
      });
    });

    it('affiche une erreur si le titre dépasse 200 caractères', async () => {
      renderTaskForm('/tasks/new');

      const longTitle = 'a'.repeat(201);
      fireEvent.change(screen.getByLabelText(/titre/i), {
        target: { value: longTitle },
      });

      fireEvent.click(screen.getByRole('button', { name: /créer la tâche/i }));

      await waitFor(() => {
        expect(screen.getByText(/200 caractères/i)).toBeInTheDocument();
      });
    });

  });

  describe('Mode édition (/tasks/:id/edit)', () => {

    it('affiche le titre "Modifier la tâche" en mode édition', () => {
      renderTaskForm('/tasks/abc-123/edit');
      expect(screen.getByText('Modifier la tâche')).toBeInTheDocument();
    });

    it('affiche le bouton "Enregistrer les modifications" en mode édition', () => {
      renderTaskForm('/tasks/abc-123/edit');
      expect(
        screen.getByRole('button', { name: /enregistrer les modifications/i })
      ).toBeInTheDocument();
    });

  });

});
