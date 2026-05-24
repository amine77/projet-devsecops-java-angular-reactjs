package com.todo.infrastructure.web;

import com.todo.application.command.CreateTaskCommand;
import com.todo.application.command.DeleteTaskCommand;
import com.todo.application.command.MarkAsDoneCommand;
import com.todo.application.command.RejectTaskCommand;
import com.todo.application.command.UpdateTaskCommand;
import com.todo.application.command.ValidateTaskCommand;
import com.todo.application.handler.CreateTaskHandler;
import com.todo.application.handler.DeleteTaskHandler;
import com.todo.application.handler.MarkAsDoneHandler;
import com.todo.application.handler.RejectTaskHandler;
import com.todo.application.handler.UpdateTaskHandler;
import com.todo.application.handler.ValidateTaskHandler;
import com.todo.application.query.GetMyTasksQuery;
import com.todo.application.query.GetTaskByIdQuery;
import com.todo.application.query.GetTeamTasksQuery;
import com.todo.application.query.TaskQueryHandler;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.TaskStatus;
import com.todo.domain.model.TeamId;
import com.todo.domain.model.UserId;
import com.todo.infrastructure.config.JwtToUserConverter;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * ══════════════════════════════════════════════════════════════
 *  REST CONTROLLER — TaskController
 * ══════════════════════════════════════════════════════════════
 *
 *  Point d'entrée HTTP pour toutes les opérations sur les tâches.
 *
 *  RESPONSABILITÉS DU CONTRÔLEUR (et seulement ça) :
 *  1. Désérialiser le JSON entrant (DTO)
 *  2. Extraire l'actorId du JWT
 *  3. Construire la Command ou Query
 *  4. Déléguer au Handler
 *  5. Sérialiser la réponse en JSON (DTO)
 *  6. Retourner le bon code HTTP
 *
 *  CE QUE LE CONTRÔLEUR NE FAIT PAS :
 *  → Pas de logique métier
 *  → Pas d'accès direct à la DB
 *  → Pas de vérification de droits (fait dans le domaine)
 *
 *  CONVENTIONS HTTP REST :
 *  → POST /api/tasks            → 201 Created + Location header
 *  → GET  /api/tasks/me         → 200 OK + liste JSON
 *  → GET  /api/tasks/{id}       → 200 OK + tâche JSON
 *  → PUT  /api/tasks/{id}       → 204 No Content
 *  → DELETE /api/tasks/{id}     → 204 No Content
 *  → PUT /api/tasks/{id}/validate → 200 OK
 *  → PUT /api/tasks/{id}/reject   → 200 OK
 *  → PUT /api/tasks/{id}/done     → 200 OK
 *
 *  @AuthenticationPrincipal Jwt :
 *  → Spring Security injecte automatiquement le JWT validé
 *  → JwtToUserConverter.extractUserId() fait le mapping JWT → UserId local
 *
 *  GESTION DES ERREURS :
 *  → Les exceptions domaine (TaskNotFoundException, UnauthorizedActionException...)
 *    sont interceptées par GlobalExceptionHandler.java (à créer en Phase 3 suite)
 *  → Le contrôleur ne contient aucun try-catch
 * ══════════════════════════════════════════════════════════════
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    // ── Handlers Commands ────────────────────────────────────────
    private final CreateTaskHandler   createHandler;
    private final UpdateTaskHandler   updateHandler;
    private final DeleteTaskHandler   deleteHandler;
    private final ValidateTaskHandler validateHandler;
    private final RejectTaskHandler   rejectHandler;
    private final MarkAsDoneHandler   markAsDoneHandler;

    // ── Handler Queries ──────────────────────────────────────────
    private final TaskQueryHandler queryHandler;

    // ── Utilitaire JWT ───────────────────────────────────────────
    private final JwtToUserConverter jwtConverter;

    public TaskController(
            CreateTaskHandler createHandler,
            UpdateTaskHandler updateHandler,
            DeleteTaskHandler deleteHandler,
            ValidateTaskHandler validateHandler,
            RejectTaskHandler rejectHandler,
            MarkAsDoneHandler markAsDoneHandler,
            TaskQueryHandler queryHandler,
            JwtToUserConverter jwtConverter
    ) {
        this.createHandler    = createHandler;
        this.updateHandler    = updateHandler;
        this.deleteHandler    = deleteHandler;
        this.validateHandler  = validateHandler;
        this.rejectHandler    = rejectHandler;
        this.markAsDoneHandler= markAsDoneHandler;
        this.queryHandler     = queryHandler;
        this.jwtConverter     = jwtConverter;
    }

    // ══════════════════════════════════════════════════════════
    //  COMMANDES GESTIONNAIRE
    // ══════════════════════════════════════════════════════════

    /**
     * Crée une nouvelle tâche.
     *
     * POST /api/tasks
     * Authorization: Bearer <token GESTIONNAIRE>
     * Body: { "title": "...", "priority": "HAUTE", "dueDate": "2026-06-30" }
     *
     * Réponse 201 Created :
     * Location: /api/tasks/550e8400-e29b-41d4-a716-446655440000
     * Body: <vide> — le client suit le Location pour obtenir les détails
     *
     * LOCATION HEADER :
     * → Bonne pratique REST pour les 201 Created
     * → Permet au client de faire immédiatement un GET sur la ressource créée
     * → ServletUriComponentsBuilder construit l'URL absolue à partir de la requête courante
     */
    @PostMapping
    public ResponseEntity<Void> createTask(
            @RequestBody @Valid TaskRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserId actorId = jwtConverter.extractUserId(jwt);

        TaskId taskId = createHandler.handle(new CreateTaskCommand(
                request.title(),
                request.description(),
                request.priority(),
                request.dueDate(),
                actorId
        ));

        // Construire l'URL de la ressource créée (ex: http://localhost:8080/api/tasks/uuid)
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(taskId.value())
                .toUri();

        return ResponseEntity.created(location).build();
    }

    /**
     * Modifie une tâche existante.
     *
     * PUT /api/tasks/{id}
     * Body: { "title": "...", "priority": "NORMALE" }
     * Réponse: 204 No Content
     */
    @PutMapping("/{id}")
    public ResponseEntity<Void> updateTask(
            @PathVariable UUID id,
            @RequestBody @Valid TaskRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserId actorId = jwtConverter.extractUserId(jwt);

        updateHandler.handle(new UpdateTaskCommand(
                TaskId.of(id.toString()),
                request.title(),
                request.description(),
                request.priority(),
                request.dueDate(),
                actorId
        ));

        return ResponseEntity.noContent().build(); // 204
    }

    /**
     * Supprime une tâche (uniquement en A_FAIRE — RG-05).
     *
     * DELETE /api/tasks/{id}
     * Réponse: 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserId actorId = jwtConverter.extractUserId(jwt);
        deleteHandler.handle(new DeleteTaskCommand(TaskId.of(id.toString()), actorId));
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════
    //  COMMANDES MANAGER
    // ══════════════════════════════════════════════════════════

    /**
     * Valide une tâche : A_FAIRE → VALIDEE.
     *
     * PUT /api/tasks/{id}/validate
     * Authorization: Bearer <token MANAGER>
     * Body: <vide>
     * Réponse: 200 OK
     */
    @PutMapping("/{id}/validate")
    public ResponseEntity<Void> validateTask(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserId actorId = jwtConverter.extractUserId(jwt);
        validateHandler.handle(new ValidateTaskCommand(TaskId.of(id.toString()), actorId));
        return ResponseEntity.ok().build();
    }

    /**
     * Rejette une tâche : A_FAIRE → REJETEE.
     * Le motif est OBLIGATOIRE (RG-06).
     *
     * PUT /api/tasks/{id}/reject
     * Body: { "rejectionReason": "Budget insuffisant" }
     * Réponse: 200 OK
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<Void> rejectTask(
            @PathVariable UUID id,
            @RequestBody @Valid RejectRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserId actorId = jwtConverter.extractUserId(jwt);
        rejectHandler.handle(new RejectTaskCommand(
                TaskId.of(id.toString()),
                request.rejectionReason(),
                actorId
        ));
        return ResponseEntity.ok().build();
    }

    /**
     * Marque une tâche comme terminée.
     *
     * PUT /api/tasks/{id}/done
     * Body: <vide>
     * Réponse: 200 OK
     */
    @PutMapping("/{id}/done")
    public ResponseEntity<Void> markAsDone(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserId actorId = jwtConverter.extractUserId(jwt);
        markAsDoneHandler.handle(new MarkAsDoneCommand(TaskId.of(id.toString()), actorId));
        return ResponseEntity.ok().build();
    }

    // ══════════════════════════════════════════════════════════
    //  REQUÊTES
    // ══════════════════════════════════════════════════════════

    /**
     * Tâches du GESTIONNAIRE connecté.
     * Utilise le Cache-Aside (Redis → PostgreSQL).
     *
     * GET /api/tasks/me
     * Réponse: 200 OK + liste JSON
     */
    @GetMapping("/me")
    public ResponseEntity<List<TaskResponse>> getMyTasks(@AuthenticationPrincipal Jwt jwt) {
        UserId actorId = jwtConverter.extractUserId(jwt);
        List<TaskResponse> tasks = queryHandler.getMyTasks(new GetMyTasksQuery(actorId))
                .stream()
                .map(TaskResponse::fromDomain)
                .toList();
        return ResponseEntity.ok(tasks);
    }

    /**
     * Tâches d'une équipe — tableau de bord Manager.
     *
     * GET /api/tasks?teamId={uuid}
     * Réponse: 200 OK + liste JSON
     */
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTeamTasks(
            @RequestParam UUID teamId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserId actorId = jwtConverter.extractUserId(jwt);
        List<TaskResponse> tasks = queryHandler.getTeamTasks(
                new GetTeamTasksQuery(new TeamId(teamId), actorId)
        ).stream().map(TaskResponse::fromDomain).toList();
        return ResponseEntity.ok(tasks);
    }

    /**
     * Détail d'une tâche par ID.
     *
     * GET /api/tasks/{id}
     * Réponse: 200 OK + tâche JSON ou 404 Not Found
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserId actorId = jwtConverter.extractUserId(jwt);
        TaskResponse task = TaskResponse.fromDomain(
                queryHandler.getTaskById(new GetTaskByIdQuery(TaskId.of(id.toString()), actorId))
        );
        return ResponseEntity.ok(task);
    }

    /**
     * Tâches en retard pour l'équipe du Manager.
     *
     * GET /api/tasks/overdue?teamId={uuid}
     */
    @GetMapping("/overdue")
    public ResponseEntity<List<TaskResponse>> getOverdueTasks(
            @RequestParam UUID teamId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserId actorId = jwtConverter.extractUserId(jwt);
        List<TaskResponse> tasks = queryHandler.getOverdueTasksForTeam(new TeamId(teamId), actorId)
                .stream().map(TaskResponse::fromDomain).toList();
        return ResponseEntity.ok(tasks);
    }

    /**
     * Tâches filtrées par statut.
     *
     * GET /api/tasks/by-status?status=A_FAIRE
     */
    @GetMapping("/by-status")
    public ResponseEntity<List<TaskResponse>> getTasksByStatus(
            @RequestParam TaskStatus status,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UserId actorId = jwtConverter.extractUserId(jwt);
        List<TaskResponse> tasks = queryHandler.getTasksByStatus(status, actorId)
                .stream().map(TaskResponse::fromDomain).toList();
        return ResponseEntity.ok(tasks);
    }
}
