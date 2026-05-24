package com.todo.infrastructure.web;

import com.todo.domain.exception.InvalidTaskStatusException;
import com.todo.domain.exception.RejectionReasonRequiredException;
import com.todo.domain.exception.TaskNotFoundException;
import com.todo.domain.exception.UnauthorizedActionException;
import com.todo.domain.exception.UserNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * ══════════════════════════════════════════════════════════════
 *  GLOBAL EXCEPTION HANDLER — GlobalExceptionHandler
 * ══════════════════════════════════════════════════════════════
 *
 *  Intercepte toutes les exceptions non gérées et les convertit
 *  en réponses HTTP structurées au format RFC 7807 (Problem Details).
 *
 *  POURQUOI RFC 7807 (ProblemDetail) ?
 *  → Standard pour les erreurs HTTP en REST
 *  → Format JSON standardisé :
 *    {
 *      "type": "https://todo-enterprise.com/errors/task-not-found",
 *      "title": "Tâche introuvable",
 *      "status": 404,
 *      "detail": "Aucune tâche avec l'ID : 550e8400-...",
 *      "timestamp": "2026-05-24T10:30:00Z"
 *    }
 *  → Compatible avec Spring Boot 3 (support natif de ProblemDetail)
 *
 *  MAPPING EXCEPTIONS → HTTP STATUS :
 *  → TaskNotFoundException          → 404 Not Found
 *  → UserNotFoundException          → 404 Not Found
 *  → UnauthorizedActionException    → 403 Forbidden
 *  → InvalidTaskStatusException     → 422 Unprocessable Entity
 *  → RejectionReasonRequiredException→ 422 Unprocessable Entity
 *  → MethodArgumentNotValidException → 400 Bad Request (Bean Validation)
 *  → OptimisticLockingFailure        → 409 Conflict
 *  → AccessDeniedException (Spring)  → 403 Forbidden
 *
 *  POURQUOI PAS DE TRY-CATCH DANS LES CONTRÔLEURS ?
 *  → Ce Handler centralise la gestion des erreurs
 *  → Les contrôleurs restent propres et lisibles
 *  → Un seul endroit pour changer le format des erreurs
 *  → @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 *
 *  SÉCURITÉ — NE PAS LEAKER D'INFORMATIONS INTERNES :
 *  → Ne pas exposer les stack traces en production
 *  → Les messages d'erreur sont génériques côté client
 *  → Les détails techniques sont loggés côté serveur
 * ══════════════════════════════════════════════════════════════
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** URI de base pour les types d'erreur RFC 7807 */
    private static final String ERROR_BASE_URI = "https://todo-enterprise.com/errors/";

    // ══════════════════════════════════════════════════════════
    //  EXCEPTIONS DOMAINE — 404 Not Found
    // ══════════════════════════════════════════════════════════

    @ExceptionHandler(TaskNotFoundException.class)
    public ProblemDetail handleTaskNotFound(TaskNotFoundException ex) {
        log.info("Tâche introuvable : {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problem.setType(URI.create(ERROR_BASE_URI + "task-not-found"));
        problem.setTitle("Tâche introuvable");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleUserNotFound(UserNotFoundException ex) {
        log.info("Utilisateur introuvable : {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                ex.getMessage()
        );
        problem.setType(URI.create(ERROR_BASE_URI + "user-not-found"));
        problem.setTitle("Utilisateur introuvable");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ══════════════════════════════════════════════════════════
    //  EXCEPTIONS DOMAINE — 403 Forbidden
    // ══════════════════════════════════════════════════════════

    @ExceptionHandler(UnauthorizedActionException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedActionException ex) {
        // LOG WARN (pas ERROR) — c'est une tentative d'accès non autorisé, pas un bug
        log.warn("Action non autorisée : {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Vous n'avez pas les droits pour effectuer cette action"
                // Note : on ne retourne pas ex.getMessage() au client car
                // il contient des informations internes (IDs, action...)
        );
        problem.setType(URI.create(ERROR_BASE_URI + "unauthorized-action"));
        problem.setTitle("Action non autorisée");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Accès refusé Spring Security : {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Accès refusé"
        );
        problem.setType(URI.create(ERROR_BASE_URI + "access-denied"));
        problem.setTitle("Accès refusé");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ══════════════════════════════════════════════════════════
    //  EXCEPTIONS DOMAINE — 422 Unprocessable Entity
    // ══════════════════════════════════════════════════════════

    /**
     * Transition de statut invalide (ex: valider une tâche DONE).
     * 422 = la requête est syntaxiquement correcte mais sémantiquement impossible.
     */
    @ExceptionHandler(InvalidTaskStatusException.class)
    public ProblemDetail handleInvalidStatus(InvalidTaskStatusException ex) {
        log.info("Transition de statut invalide : {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage()
        );
        problem.setType(URI.create(ERROR_BASE_URI + "invalid-task-status"));
        problem.setTitle("Transition de statut invalide");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /**
     * Motif de rejet absent (RG-06).
     */
    @ExceptionHandler(RejectionReasonRequiredException.class)
    public ProblemDetail handleRejectionReasonRequired(RejectionReasonRequiredException ex) {
        log.info("Motif de rejet manquant (RG-06) : {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage()
        );
        problem.setType(URI.create(ERROR_BASE_URI + "rejection-reason-required"));
        problem.setTitle("Motif de rejet obligatoire");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ══════════════════════════════════════════════════════════
    //  EXCEPTIONS INFRASTRUCTURE — 400 Bad Request
    // ══════════════════════════════════════════════════════════

    /**
     * Violation de validation Bean Validation (@NotBlank, @Size...).
     * Spring MVC lève cette exception quand @Valid échoue.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        // Extraire tous les messages d'erreur de validation
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + " : " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.debug("Validation échouée : {}", errors);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Données invalides : " + errors
        );
        problem.setType(URI.create(ERROR_BASE_URI + "validation-error"));
        problem.setTitle("Erreur de validation");
        problem.setProperty("timestamp", Instant.now());
        problem.setProperty("errors", errors);
        return problem;
    }

    // ══════════════════════════════════════════════════════════
    //  EXCEPTIONS INFRASTRUCTURE — 409 Conflict
    // ══════════════════════════════════════════════════════════

    /**
     * Conflit de version (optimistic locking).
     * Deux utilisateurs ont modifié la même tâche simultanément.
     * Le client doit recharger la tâche et réessayer.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Conflit de version (optimistic lock) : {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "La tâche a été modifiée par un autre utilisateur. Rechargez et réessayez."
        );
        problem.setType(URI.create(ERROR_BASE_URI + "optimistic-lock-conflict"));
        problem.setTitle("Conflit de modification concurrent");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    // ══════════════════════════════════════════════════════════
    //  CATCH-ALL — 500 Internal Server Error
    // ══════════════════════════════════════════════════════════

    /**
     * Toute exception non prévue → 500 avec log ERROR.
     * En production, ces erreurs déclenchent une alerte PagerDuty.
     *
     * IMPORTANT : on ne retourne PAS le message de l'exception au client
     * (risque de leak d'informations sensibles — stack trace, SQL...)
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // LOG ERROR avec stack trace complète — critique en production
        log.error("Erreur inattendue", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Une erreur inattendue s'est produite. Référence : " + System.currentTimeMillis()
                // Le timestamp sert de référence pour corréler avec les logs
        );
        problem.setType(URI.create(ERROR_BASE_URI + "internal-error"));
        problem.setTitle("Erreur interne");
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
