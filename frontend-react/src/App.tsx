import { BrowserRouter, Routes, Route, Link, Navigate } from 'react-router-dom';
import { AuthProviderInner, useAuthContext } from '@context/AuthContext';
import TaskList from '@components/tasks/TaskList';
import TaskForm from '@components/tasks/TaskForm';
import ManagerDashboard from '@components/dashboard/ManagerDashboard';

/**
 * ══════════════════════════════════════════════════════════════
 *  COMPOSANT RACINE — App.tsx (React)
 * ══════════════════════════════════════════════════════════════
 *
 *  Point d'entrée de l'application React.
 *  Gère le layout principal, la navigation et le routage.
 *
 *  STRUCTURE :
 *  → AuthProviderInner (Context d'auth, injecte le JWT dans Axios)
 *  → AppShell (layout : nav + contenu)
 *    → BrowserRouter / Routes (React Router v7)
 *      → /tasks         → TaskList (GESTIONNAIRE)
 *      → /tasks/new     → TaskForm (création)
 *      → /tasks/:id/edit → TaskForm (édition)
 *      → /dashboard     → ManagerDashboard (MANAGER)
 *      → /              → Redirection intelligente selon le rôle
 *
 *  DIFFÉRENCE AVEC ANGULAR :
 *  → Angular : app.routes.ts + RouterOutlet + loadComponent (lazy)
 *  → React   : BrowserRouter + Routes + Route (React Router v7)
 *  → La lazy loading React se fait avec React.lazy() + Suspense
 *    (non implémentée ici pour simplifier l'exemple pédagogique)
 *
 *  GUARD :
 *  → Pas de RouteGuard séparé comme Angular (canActivate)
 *  → ProtectedRoute est un composant wrapper qui redirige si non-auth
 *  → RoleRoute vérifie le rôle avant d'afficher le composant
 */

// ── Guard : route protégée (authentification requise) ──────────

interface ProtectedRouteProps {
  children: React.ReactNode;
}

function ProtectedRoute({ children }: ProtectedRouteProps) {
  const { isAuthenticated, isLoading, login } = useAuthContext();

  if (isLoading) {
    return (
      <div className="flex justify-center items-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600" />
      </div>
    );
  }

  if (!isAuthenticated) {
    // Auto-login : redirige vers Keycloak au lieu d'afficher une page de login
    login();
    return null;
  }

  return <>{children}</>;
}

// ── Guard : route limitée à certains rôles ─────────────────────

interface RoleRouteProps {
  children: React.ReactNode;
  allowedRoles: Array<'GESTIONNAIRE' | 'MANAGER' | 'SUPER_ADMINISTRATEUR'>;
}

function RoleRoute({ children, allowedRoles }: RoleRouteProps) {
  const { currentUser } = useAuthContext();

  if (!currentUser) return null;

  if (!allowedRoles.includes(currentUser.role)) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg p-8 text-center">
        <p className="text-2xl mb-2">🚫</p>
        <h2 className="text-lg font-semibold text-red-700">Accès non autorisé</h2>
        <p className="text-sm text-red-500 mt-1">
          Cette page est réservée aux rôles : {allowedRoles.join(', ')}
        </p>
      </div>
    );
  }

  return <>{children}</>;
}

// ── Navigation ─────────────────────────────────────────────────

function Navbar() {
  const { currentUser, isAuthenticated, isManager, isGestionnaire, login, logout } = useAuthContext();

  return (
    <nav className="bg-white border-b border-gray-200 px-6 py-3">
      <div className="max-w-7xl mx-auto flex items-center justify-between">

        {/* Logo / Titre */}
        <Link to="/" className="font-bold text-lg text-blue-700 hover:text-blue-800">
          ✅ Todo Enterprise
        </Link>

        {/* Liens de navigation */}
        {isAuthenticated && (
          <div className="flex gap-6">
            {isGestionnaire && (
              <Link
                to="/tasks"
                className="text-sm font-medium text-gray-600 hover:text-blue-600 transition-colors"
              >
                Mes tâches
              </Link>
            )}
            {isManager && (
              <Link
                to="/dashboard"
                className="text-sm font-medium text-gray-600 hover:text-blue-600 transition-colors"
              >
                Dashboard
              </Link>
            )}
          </div>
        )}

        {/* Utilisateur + déconnexion */}
        <div className="flex items-center gap-3">
          {isAuthenticated && currentUser ? (
            <>
              <span className="text-sm text-gray-500">
                {currentUser.name}
                <span className="ml-1.5 bg-gray-100 text-gray-600 text-xs px-2 py-0.5 rounded-full">
                  {currentUser.role}
                </span>
              </span>
              <button
                onClick={logout}
                className="text-sm text-red-600 hover:text-red-700 font-medium transition-colors"
              >
                Déconnexion
              </button>
            </>
          ) : (
            <button
              onClick={login}
              className="bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium px-4 py-1.5 rounded-lg transition-colors"
            >
              Connexion
            </button>
          )}
        </div>
      </div>
    </nav>
  );
}

// ── Shell de l'application ─────────────────────────────────────

function AppShell() {
  const { isGestionnaire, isManager, isSuperAdmin } = useAuthContext();

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <main className="max-w-7xl mx-auto px-6 py-8">
        <Routes>

          {/* ── Redirection racine selon le rôle ── */}
          <Route
            path="/"
            element={
              <ProtectedRoute>
                {isGestionnaire
                  ? <Navigate to="/tasks" replace />
                  : isManager || isSuperAdmin
                    ? <Navigate to="/dashboard" replace />
                    : <Navigate to="/tasks" replace />}
              </ProtectedRoute>
            }
          />

          {/* ── Vue Gestionnaire : liste des tâches ── */}
          <Route
            path="/tasks"
            element={
              <ProtectedRoute>
                <RoleRoute allowedRoles={['GESTIONNAIRE', 'SUPER_ADMINISTRATEUR']}>
                  <TaskList />
                </RoleRoute>
              </ProtectedRoute>
            }
          />

          {/* ── Création d'une tâche ── */}
          <Route
            path="/tasks/new"
            element={
              <ProtectedRoute>
                <RoleRoute allowedRoles={['GESTIONNAIRE', 'SUPER_ADMINISTRATEUR']}>
                  <TaskForm />
                </RoleRoute>
              </ProtectedRoute>
            }
          />

          {/* ── Édition d'une tâche ── */}
          <Route
            path="/tasks/:id/edit"
            element={
              <ProtectedRoute>
                <RoleRoute allowedRoles={['GESTIONNAIRE', 'SUPER_ADMINISTRATEUR']}>
                  <TaskForm />
                </RoleRoute>
              </ProtectedRoute>
            }
          />

          {/* ── Dashboard Manager ── */}
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <RoleRoute allowedRoles={['MANAGER', 'SUPER_ADMINISTRATEUR']}>
                  <ManagerDashboard />
                </RoleRoute>
              </ProtectedRoute>
            }
          />

          {/* ── 404 ── */}
          <Route
            path="*"
            element={
              <div className="text-center py-16">
                <p className="text-5xl mb-4">404</p>
                <h1 className="text-xl font-semibold text-gray-700 mb-2">Page introuvable</h1>
                <Link to="/" className="text-blue-600 hover:underline">Retour à l'accueil</Link>
              </div>
            }
          />
        </Routes>
      </main>
    </div>
  );
}

// ── App : wrappé dans AuthProviderInner ────────────────────────

export default function App() {
  return (
    <BrowserRouter>
      {/*
       * AuthProviderInner doit être DANS BrowserRouter pour que navigate()
       * fonctionne dans les callbacks d'authentification.
       */}
      <AuthProviderInner>
        <AppShell />
      </AuthProviderInner>
    </BrowserRouter>
  );
}
