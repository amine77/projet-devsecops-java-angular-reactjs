/**
 * Modèle d'erreur HTTP — correspond au format RFC 7807 ProblemDetail
 * retourné par GlobalExceptionHandler.java côté backend.
 *
 * EXEMPLE DE RÉPONSE BACKEND :
 * {
 *   "type": "https://todo-enterprise.com/errors/task-not-found",
 *   "title": "Tâche introuvable",
 *   "status": 404,
 *   "detail": "Aucune tâche avec l'ID : 550e8400-...",
 *   "timestamp": "2026-05-24T10:30:00Z"
 * }
 */
export interface ApiError {
  readonly type?: string;       // URI du type d'erreur
  readonly title: string;       // Titre lisible
  readonly status: number;      // Code HTTP (404, 403, 422...)
  readonly detail: string;      // Message détaillé
  readonly timestamp?: string;  // ISO 8601
  readonly errors?: string;     // Pour les erreurs de validation (400)
}
