package com.todo.domain.service;

import com.todo.domain.event.TaskCreated;
import com.todo.domain.event.TaskMarkedAsDone;
import com.todo.domain.event.TaskRejected;
import com.todo.domain.event.TaskValidated;
import com.todo.domain.exception.TaskNotFoundException;
import com.todo.domain.exception.UnauthorizedActionException;
import com.todo.domain.exception.UserNotFoundException;
import com.todo.domain.model.Priority;
import com.todo.domain.model.Task;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.TaskStatus;
import com.todo.domain.model.TeamId;
import com.todo.domain.model.User;
import com.todo.domain.model.UserId;
import com.todo.domain.port.in.TaskUseCase;
import com.todo.domain.port.out.CachePort;
import com.todo.domain.port.out.EventPublisher;
import com.todo.domain.port.out.NotificationPort;
import com.todo.domain.port.out.TaskRepository;
import com.todo.domain.port.out.UserRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * ══════════════════════════════════════════════════════════════
 *  DOMAIN SERVICE — TaskDomainService
 * ══════════════════════════════════════════════════════════════
 *
 *  Le Domain Service orchestre la logique métier qui ne peut pas
 *  tenir dans une seule entité (car elle implique plusieurs
 *  agrégats : Task + User).
 *
 *  Ce que fait ce service :
 *  1. Charge les entités via les ports (TaskRepository, UserRepository)
 *  2. Vérifie les droits (rôle + périmètre de données)
 *  3. Délègue la logique métier aux entités (task.validate(), task.reject()...)
 *  4. Sauvegarde les nouvelles instances
 *  5. Invalide le cache
 *  6. Publie les Domain Events (→ Kafka → SES, MongoDB...)
 *
 *  RÈGLE D'OR : Aucun import Spring, JPA, Kafka, AWS dans cette classe.
 *  Si vous voyez "import org.springframework.*" ici → violation architecturale.
 *
 *  La gestion des transactions (@Transactional) est faite dans la couche
 *  infrastructure (ApplicationConfig ou directement sur les adaptateurs JPA).
 * ══════════════════════════════════════════════════════════════
 */
public class TaskDomainService implements TaskUseCase {

    // ── Ports injectés par l'infrastructure (Dependency Injection) ─
    // Pas d'@Autowired ici — le domaine ne connaît pas Spring.
    // L'injection est configurée dans infrastructure/config/DomainConfig.java

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final EventPublisher eventPublisher;
    private final CachePort cachePort;
    private final NotificationPort notificationPort;

    /**
     * Injection par constructeur — la seule manière valide en architecture hexagonale.
     * Avantage test : on passe des mocks simplement via new TaskDomainService(mock1, mock2...).
     * Aucun framework nécessaire pour instancier ce service dans les tests.
     */
    public TaskDomainService(
            TaskRepository taskRepository,
            UserRepository userRepository,
            EventPublisher eventPublisher,
            CachePort cachePort,
            NotificationPort notificationPort
    ) {
        this.taskRepository    = taskRepository;
        this.userRepository    = userRepository;
        this.eventPublisher    = eventPublisher;
        this.cachePort         = cachePort;
        this.notificationPort  = notificationPort;
    }

    // ══════════════════════════════════════════════════════════
    //  COMMANDES GESTIONNAIRE
    // ══════════════════════════════════════════════════════════

    @Override
    public TaskId createTask(String title, String description, Priority priority,
                              LocalDate dueDate, UserId actorId) {

        // 1. Charger l'acteur pour vérifier son rôle et récupérer son équipe
        User actor = loadUser(actorId);

        // 2. Vérification RBAC : seul GESTIONNAIRE/SUPER_ADMIN peut créer
        if (!actor.role().canCreateTask()) {
            throw new UnauthorizedActionException(actorId, null, "créer une tâche");
        }

        // 3. Créer l'agrégat Task via sa factory method
        // La validation des invariants (title non vide, etc.) est faite dans Task
        Task task = Task.create(
                title, description, priority, dueDate,
                actorId,
                actor.teamId()  // l'équipe est celle du Gestionnaire connecté
        );

        // 4. Persister
        taskRepository.save(task);

        // 5. Invalider le cache de l'owner (sa liste de tâches a changé)
        cachePort.evict(task.id(), actorId, task.teamId());

        // 6. Publier l'événement domaine
        // → KafkaEventPublisher le route vers le topic "task-events"
        // → Le Manager reçoit une notification in-app (SQS → Spring Boot)
        eventPublisher.publish(
                TaskCreated.of(task.id(), title, task.priority(), actorId, task.teamId())
        );

        return task.id();
    }

    @Override
    public void updateTask(TaskId taskId, String title, String description,
                            Priority priority, LocalDate dueDate, UserId actorId) {

        Task task   = loadTask(taskId);
        User actor  = loadUser(actorId);

        // Vérification périmètre : le Gestionnaire ne peut modifier QUE ses tâches (RG-03)
        checkCanModify(actor, task);

        // La logique métier de modification (dont RG-07) est dans task.update()
        // Si la tâche était REJETEE, update() la remet en A_FAIRE automatiquement
        Task updatedTask = task.update(title, description, priority, dueDate);

        taskRepository.save(updatedTask);
        cachePort.evict(taskId, task.ownerId(), task.teamId());

        // Pas d'event publié pour les simples modifications — aucun système externe
        // n'a besoin de savoir qu'un titre a changé.
    }

    @Override
    public void deleteTask(TaskId taskId, UserId actorId) {

        Task task  = loadTask(taskId);
        User actor = loadUser(actorId);

        checkCanModify(actor, task);

        // RG-05 : suppression uniquement en A_FAIRE
        // La vérification est dans TaskStatus.isDeletable()
        if (!task.status().isDeletable()) {
            throw new UnauthorizedActionException(
                actorId, taskId,
                "supprimer une tâche en statut " + task.status()
            );
        }

        taskRepository.deleteById(taskId);
        cachePort.evict(taskId, task.ownerId(), task.teamId());
    }

    // ══════════════════════════════════════════════════════════
    //  COMMANDES MANAGER
    // ══════════════════════════════════════════════════════════

    @Override
    public void validateTask(TaskId taskId, UserId actorId) {

        Task task  = loadTask(taskId);
        User actor = loadUser(actorId);

        // Vérification rôle (MANAGER ou SUPER_ADMIN)
        checkCanChangeStatus(actor, task);

        // La transition d'état et la validation des invariants sont dans task.validate()
        // Si le statut n'est pas A_FAIRE → InvalidTaskStatusException levée par Task
        Task validatedTask = task.validate(actorId);

        taskRepository.save(validatedTask);
        cachePort.evict(taskId, task.ownerId(), task.teamId());

        // Publication event → SES notifie le Gestionnaire par email
        eventPublisher.publish(
                TaskValidated.of(taskId, actorId, task.ownerId())
        );

        // Notification in-app (via SQS → Spring Boot → WebSocket ou polling)
        User owner = loadUser(task.ownerId());
        notificationPort.notifyTaskValidated(validatedTask, owner);
    }

    @Override
    public void rejectTask(TaskId taskId, String rejectionReason, UserId actorId) {

        Task task  = loadTask(taskId);
        User actor = loadUser(actorId);

        checkCanChangeStatus(actor, task);

        // RG-06 : le motif est vérifié dans task.reject()
        // RejectionReasonRequiredException levée si motif absent
        Task rejectedTask = task.reject(rejectionReason, actorId);

        taskRepository.save(rejectedTask);
        cachePort.evict(taskId, task.ownerId(), task.teamId());

        eventPublisher.publish(
                TaskRejected.of(taskId, actorId, task.ownerId(), rejectionReason)
        );

        User owner = loadUser(task.ownerId());
        notificationPort.notifyTaskRejected(rejectedTask, owner);
    }

    @Override
    public void markAsDone(TaskId taskId, UserId actorId) {

        Task task  = loadTask(taskId);
        User actor = loadUser(actorId);

        checkCanChangeStatus(actor, task);

        Task doneTask = task.markAsDone(actorId);

        taskRepository.save(doneTask);
        cachePort.evict(taskId, task.ownerId(), task.teamId());

        eventPublisher.publish(
                TaskMarkedAsDone.of(taskId, actorId, task.ownerId())
        );

        User owner = loadUser(task.ownerId());
        notificationPort.notifyTaskDone(doneTask, owner);
    }

    @Override
    public void commentTask(TaskId taskId, String content, UserId actorId) {
        // Vérification d'existence et d'accès
        Task task  = loadTask(taskId);
        User actor = loadUser(actorId);

        // Tout utilisateur ayant accès à la tâche peut commenter
        checkCanAccess(actor, task);

        // Les commentaires sont persistés séparément (table task_comments)
        // Ici on délèverait à un CommentRepository (non implémenté en Phase 2)
        // TODO Phase 3 : implémenter CommentRepository et ajouter le commentaire
    }

    // ══════════════════════════════════════════════════════════
    //  REQUÊTES
    // ══════════════════════════════════════════════════════════

    @Override
    public List<Task> getMyTasks(UserId actorId) {
        // Stratégie Cache-Aside :
        // 1. Vérifier le cache Redis
        // 2. En cas de miss → charger depuis PostgreSQL
        // 3. Mettre en cache le résultat
        return cachePort.getByOwner(actorId)
                .orElseGet(() -> {
                    var tasks = taskRepository.findByOwner(actorId);
                    cachePort.putByOwner(actorId, tasks);
                    return tasks;
                });
    }

    @Override
    public List<Task> getTeamTasks(TeamId teamId, UserId actorId) {
        User actor = loadUser(actorId);
        checkCanAccessTeam(actor, teamId);

        return cachePort.getByTeam(teamId)
                .orElseGet(() -> {
                    var tasks = taskRepository.findByTeam(teamId);
                    cachePort.putByTeam(teamId, tasks);
                    return tasks;
                });
    }

    @Override
    public List<Task> getPendingTasksForTeam(TeamId teamId, UserId actorId) {
        User actor = loadUser(actorId);
        checkCanAccessTeam(actor, teamId);
        // Pas de cache pour la file d'action (données fraîches nécessaires)
        return taskRepository.findByTeamAndStatus(teamId, TaskStatus.A_FAIRE);
    }

    @Override
    public List<Task> getAllTasks(UserId actorId) {
        User actor = loadUser(actorId);
        if (!actor.role().hasGlobalAccess()) {
            throw new UnauthorizedActionException(actorId, null, "voir toutes les tâches");
        }
        return taskRepository.findAll();
    }

    @Override
    public Task getTaskById(TaskId taskId, UserId actorId) {
        Task task  = loadTask(taskId);
        User actor = loadUser(actorId);
        checkCanAccess(actor, task);
        return task;
    }

    @Override
    public List<Task> getOverdueTasksForTeam(TeamId teamId, UserId actorId) {
        User actor = loadUser(actorId);
        checkCanAccessTeam(actor, teamId);
        return taskRepository.findOverdue().stream()
                .filter(t -> t.belongsToTeam(teamId))
                .toList();
    }

    @Override
    public List<Task> getTasksByStatus(TaskStatus status, UserId actorId) {
        User actor = loadUser(actorId);
        return switch (actor.role()) {
            case GESTIONNAIRE        -> taskRepository.findByOwner(actorId).stream()
                                            .filter(t -> t.status() == status).toList();
            case MANAGER             -> taskRepository.findByTeamAndStatus(actor.teamId(), status);
            case SUPER_ADMINISTRATEUR-> taskRepository.findAll().stream()
                                            .filter(t -> t.status() == status).toList();
        };
    }

    // ══════════════════════════════════════════════════════════
    //  HELPERS PRIVÉS — Chargement et vérifications de droits
    // ══════════════════════════════════════════════════════════

    /** Charge une tâche ou lève TaskNotFoundException. */
    private Task loadTask(TaskId taskId) {
        // On vérifie le cache d'abord
        return cachePort.getById(taskId)
                .orElseGet(() -> taskRepository.findById(taskId)
                        .orElseThrow(() -> new TaskNotFoundException(taskId)));
    }

    /** Charge un utilisateur ou lève UserNotFoundException. */
    private User loadUser(UserId userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * Vérifie que l'acteur peut modifier la tâche (créer/modifier/supprimer).
     * Règle : GESTIONNAIRE ne peut agir que sur SES tâches (RG-03).
     */
    private void checkCanModify(User actor, Task task) {
        if (actor.role().hasGlobalAccess()) return; // Super-Admin : tout autorisé
        if (!task.isOwnedBy(actor.id())) {
            throw new UnauthorizedActionException(actor.id(), task.id(), "modifier cette tâche");
        }
    }

    /**
     * Vérifie que l'acteur peut changer le statut d'une tâche.
     * Règle : MANAGER agit sur son ÉQUIPE uniquement (RG-04 + RG-08).
     */
    private void checkCanChangeStatus(User actor, Task task) {
        // Vérification rôle
        if (!actor.role().canChangeTaskStatus()) {
            throw new UnauthorizedActionException(actor.id(), task.id(), "changer le statut");
        }
        // Vérification périmètre (pour MANAGER — pas pour SUPER_ADMIN)
        if (!actor.role().hasGlobalAccess() && !task.belongsToTeam(actor.teamId())) {
            throw new UnauthorizedActionException(
                actor.id(), task.id(), "agir sur une tâche hors de son équipe"
            );
        }
    }

    /** Vérifie qu'un acteur peut accéder (lire) une tâche. */
    private void checkCanAccess(User actor, Task task) {
        if (actor.role().hasGlobalAccess()) return;
        boolean isOwner  = task.isOwnedBy(actor.id());
        boolean isManager= task.belongsToTeam(actor.teamId()) && actor.role().canChangeTaskStatus();
        if (!isOwner && !isManager) {
            throw new UnauthorizedActionException(actor.id(), task.id(), "accéder à cette tâche");
        }
    }

    /** Vérifie qu'un acteur peut accéder aux tâches d'une équipe. */
    private void checkCanAccessTeam(User actor, TeamId teamId) {
        if (!actor.canActOnTeam(teamId)) {
            throw new UnauthorizedActionException(actor.id(), null,
                "accéder aux tâches de l'équipe " + teamId);
        }
    }
}
