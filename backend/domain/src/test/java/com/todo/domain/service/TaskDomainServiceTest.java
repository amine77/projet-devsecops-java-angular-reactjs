package com.todo.domain.service;

import com.todo.domain.event.DomainEvent;
import com.todo.domain.event.TaskCreated;
import com.todo.domain.event.TaskRejected;
import com.todo.domain.event.TaskValidated;
import com.todo.domain.exception.InvalidTaskStatusException;
import com.todo.domain.exception.RejectionReasonRequiredException;
import com.todo.domain.exception.TaskNotFoundException;
import com.todo.domain.exception.UnauthorizedActionException;
import com.todo.domain.model.Priority;
import com.todo.domain.model.Task;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.TaskStatus;
import com.todo.domain.model.TeamId;
import com.todo.domain.model.UnitId;
import com.todo.domain.model.User;
import com.todo.domain.model.UserId;
import com.todo.domain.port.out.CachePort;
import com.todo.domain.port.out.EventPublisher;
import com.todo.domain.port.out.NotificationPort;
import com.todo.domain.port.out.TaskRepository;
import com.todo.domain.port.out.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * ══════════════════════════════════════════════════════════════
 *  Tests du TaskDomainService — Approche TDD
 * ══════════════════════════════════════════════════════════════
 *
 *  Ces tests s'exécutent SANS Spring, SANS base de données,
 *  SANS Docker. Ils sont instantanés (<100ms pour l'ensemble).
 *
 *  Pourquoi c'est possible ?
 *  → L'architecture hexagonale isole le domaine.
 *  → On injecte des MOCKS Mockito à la place des vraies implémentations.
 *  → Le domaine ne dépend que d'interfaces (ports) → remplaçables.
 *
 *  Structure des tests : @Nested classes par use case.
 *  Chaque classe teste un comportement métier (feature) avec plusieurs
 *  scénarios (cas nominal, cas d'erreur, cas limites).
 *
 *  Convention de nommage : given_when_then ou description lisible en français.
 * ══════════════════════════════════════════════════════════════
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskDomainService")
class TaskDomainServiceTest {

    // ── Mocks des ports sortants ─────────────────────────────
    // Mockito crée des implémentations vides — on configure
    // leur comportement avec given(...).willReturn(...)
    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private EventPublisher eventPublisher;
    @Mock private CachePort cachePort;
    @Mock private NotificationPort notificationPort;

    /** Le service à tester — instancié avec les mocks (pas de Spring). */
    private TaskDomainService service;

    // ── Fixtures — données de test réutilisables ─────────────
    private final TeamId teamId   = TeamId.generate();
    private final UnitId unitId   = UnitId.generate();
    private final UserId ownerId  = UserId.generate();
    private final UserId managerId= UserId.generate();

    private User gestionnaire;
    private User manager;

    @BeforeEach
    void setUp() {
        // Instanciation sans Spring — c'est la force de l'architecture hexagonale
        service = new TaskDomainService(
                taskRepository, userRepository,
                eventPublisher, cachePort, notificationPort
        );

        // Fixtures utilisateurs
        gestionnaire = User.createGestionnaire(
                UUID.randomUUID().toString(), "alice", "alice@todo.local",
                "Alice", "Dupont", teamId, unitId
        );
        // On fixe l'ID pour les assertions
        gestionnaire = new User(
                ownerId, gestionnaire.keycloakId(), gestionnaire.username(),
                gestionnaire.email(), gestionnaire.firstName(), gestionnaire.lastName(),
                gestionnaire.role(), gestionnaire.teamId(), gestionnaire.unitId(),
                true, gestionnaire.createdAt(), gestionnaire.updatedAt()
        );

        manager = User.createManager(
                UUID.randomUUID().toString(), "claire", "claire@todo.local",
                "Claire", "Bernard", teamId, unitId
        );
        manager = new User(
                managerId, manager.keycloakId(), manager.username(),
                manager.email(), manager.firstName(), manager.lastName(),
                manager.role(), manager.teamId(), manager.unitId(),
                true, manager.createdAt(), manager.updatedAt()
        );
    }

    // ══════════════════════════════════════════════════════════
    //  CAS DE TEST : Création de tâche
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createTask")
    class CreateTaskTests {

        @Test
        @DisplayName("✅ Un Gestionnaire peut créer une tâche → statut A_FAIRE, event publié")
        void gestionnaire_creates_task_successfully() {
            // ── GIVEN ─────────────────────────────────────────
            // Le Gestionnaire existe dans notre UserRepository (mock)
            given(userRepository.findById(ownerId)).willReturn(Optional.of(gestionnaire));
            // Le cache ne contient rien (miss)
            given(cachePort.getByOwner(ownerId)).willReturn(Optional.empty());

            // ── WHEN ──────────────────────────────────────────
            TaskId createdId = service.createTask(
                    "Préparer le rapport Q4",
                    "Rapport trimestriel à remettre avant fin de mois",
                    Priority.HAUTE,
                    LocalDate.now().plusDays(7),
                    ownerId
            );

            // ── THEN ──────────────────────────────────────────
            // 1. La tâche a bien été sauvegardée
            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            then(taskRepository).should().save(taskCaptor.capture());

            Task savedTask = taskCaptor.getValue();
            assertThat(savedTask.title()).isEqualTo("Préparer le rapport Q4");
            assertThat(savedTask.status()).isEqualTo(TaskStatus.A_FAIRE);
            assertThat(savedTask.priority()).isEqualTo(Priority.HAUTE);
            assertThat(savedTask.ownerId()).isEqualTo(ownerId);
            assertThat(savedTask.teamId()).isEqualTo(teamId);

            // 2. L'ID retourné correspond à la tâche sauvegardée
            assertThat(createdId).isEqualTo(savedTask.id());

            // 3. Un event TaskCreated a été publié (pour Kafka → notif Manager)
            ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
            then(eventPublisher).should().publish(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isInstanceOf(TaskCreated.class);
            TaskCreated event = (TaskCreated) eventCaptor.getValue();
            assertThat(event.taskId()).isEqualTo(createdId);
            assertThat(event.ownerId()).isEqualTo(ownerId);
        }

        @Test
        @DisplayName("❌ Un Manager ne peut PAS créer de tâche → UnauthorizedActionException")
        void manager_cannot_create_task() {
            // GIVEN : c'est un Manager qui tente de créer
            given(userRepository.findById(managerId)).willReturn(Optional.of(manager));

            // WHEN + THEN : exception levée, rien sauvegardé
            assertThatThrownBy(() ->
                    service.createTask("Tâche", null, Priority.NORMALE, null, managerId)
            ).isInstanceOf(UnauthorizedActionException.class)
             .hasMessageContaining("créer une tâche");

            // Vérifier que save() n'a jamais été appelé
            then(taskRepository).should(never()).save(any());
            then(eventPublisher).should(never()).publish(any());
        }

        @Test
        @DisplayName("❌ Titre vide → IllegalArgumentException (invariant Task)")
        void empty_title_throws_exception() {
            given(userRepository.findById(ownerId)).willReturn(Optional.of(gestionnaire));

            assertThatThrownBy(() ->
                    service.createTask("   ", null, Priority.NORMALE, null, ownerId)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("titre");
        }

        @Test
        @DisplayName("✅ Priorité null → défaut NORMALE")
        void null_priority_defaults_to_normale() {
            given(userRepository.findById(ownerId)).willReturn(Optional.of(gestionnaire));

            service.createTask("Tâche sans priorité", null, null, null, ownerId);

            ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
            then(taskRepository).should().save(captor.capture());
            assertThat(captor.getValue().priority()).isEqualTo(Priority.NORMALE);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  CAS DE TEST : Validation d'une tâche
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("validateTask")
    class ValidateTaskTests {

        @Test
        @DisplayName("✅ Manager valide une tâche A_FAIRE → VALIDEE, event + notification")
        void manager_validates_task_successfully() {
            // GIVEN
            // Task.create passe ownerId directement → pas besoin de réassigner
            Task task = Task.create("Rapport", null, Priority.NORMALE, null, ownerId, teamId);

            given(cachePort.getById(task.id())).willReturn(Optional.empty());
            given(taskRepository.findById(task.id())).willReturn(Optional.of(task));
            given(userRepository.findById(managerId)).willReturn(Optional.of(manager));
            given(userRepository.findById(ownerId)).willReturn(Optional.of(gestionnaire));

            // WHEN
            service.validateTask(task.id(), managerId);

            // THEN — la tâche sauvegardée doit être en VALIDEE
            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            then(taskRepository).should().save(taskCaptor.capture());

            Task savedTask = taskCaptor.getValue();
            assertThat(savedTask.status()).isEqualTo(TaskStatus.VALIDEE);
            assertThat(savedTask.validatedBy()).isEqualTo(managerId);
            assertThat(savedTask.validatedAt()).isNotNull();
            assertThat(savedTask.version()).isEqualTo(task.version() + 1); // optimistic lock

            // Event publié
            ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
            then(eventPublisher).should().publish(eventCaptor.capture());
            assertThat(eventCaptor.getValue()).isInstanceOf(TaskValidated.class);

            // Notification envoyée au Gestionnaire
            then(notificationPort).should().notifyTaskValidated(any(), any());
        }

        @Test
        @DisplayName("❌ Gestionnaire ne peut PAS valider → UnauthorizedActionException")
        void gestionnaire_cannot_validate_task() {
            Task task = Task.create("Rapport", null, Priority.NORMALE, null, ownerId, teamId);

            given(cachePort.getById(task.id())).willReturn(Optional.empty());
            given(taskRepository.findById(task.id())).willReturn(Optional.of(task));
            given(userRepository.findById(ownerId)).willReturn(Optional.of(gestionnaire));

            assertThatThrownBy(() -> service.validateTask(task.id(), ownerId))
                    .isInstanceOf(UnauthorizedActionException.class)
                    .hasMessageContaining("changer le statut");

            then(taskRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("❌ Valider une tâche DONE → InvalidTaskStatusException")
        void cannot_validate_done_task() {
            // On crée une tâche puis on la marque Done manuellement
            Task doneTask = Task.create("Tâche", null, Priority.NORMALE, null, ownerId, teamId)
                    .validate(managerId)
                    .markAsDone(managerId);

            given(cachePort.getById(doneTask.id())).willReturn(Optional.empty());
            given(taskRepository.findById(doneTask.id())).willReturn(Optional.of(doneTask));
            given(userRepository.findById(managerId)).willReturn(Optional.of(manager));

            assertThatThrownBy(() -> service.validateTask(doneTask.id(), managerId))
                    .isInstanceOf(InvalidTaskStatusException.class)
                    .hasMessageContaining("valider");
        }

        @Test
        @DisplayName("❌ Manager d'une autre équipe ne peut PAS valider → UnauthorizedActionException")
        void manager_cannot_validate_task_from_another_team() {
            // Tâche dans l'équipe A
            Task task = Task.create("Tâche équipe A", null, Priority.NORMALE, null,
                    ownerId, teamId);

            // Manager de l'équipe B (teamId différent)
            TeamId autreTeam = TeamId.generate();
            User managerAutreEquipe = new User(
                    managerId, "kc-id", "bob", "bob@todo.local",
                    "Bob", "Martin", manager.role(), autreTeam, unitId,
                    true, manager.createdAt(), manager.updatedAt()
            );

            given(cachePort.getById(task.id())).willReturn(Optional.empty());
            given(taskRepository.findById(task.id())).willReturn(Optional.of(task));
            given(userRepository.findById(managerId)).willReturn(Optional.of(managerAutreEquipe));

            assertThatThrownBy(() -> service.validateTask(task.id(), managerId))
                    .isInstanceOf(UnauthorizedActionException.class)
                    .hasMessageContaining("équipe");
        }
    }

    // ══════════════════════════════════════════════════════════
    //  CAS DE TEST : Rejet d'une tâche (RG-06)
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("rejectTask")
    class RejectTaskTests {

        @Test
        @DisplayName("✅ Manager rejette avec motif → REJETEE, event + notification")
        void manager_rejects_task_with_reason() {
            // Task.create passe ownerId directement → pas besoin de réassigner
            Task task = Task.create("Rapport", null, Priority.NORMALE, null, ownerId, teamId);

            given(cachePort.getById(task.id())).willReturn(Optional.empty());
            given(taskRepository.findById(task.id())).willReturn(Optional.of(task));
            given(userRepository.findById(managerId)).willReturn(Optional.of(manager));
            given(userRepository.findById(ownerId)).willReturn(Optional.of(gestionnaire));

            service.rejectTask(task.id(), "Le rapport est incomplet", managerId);

            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            then(taskRepository).should().save(taskCaptor.capture());

            Task savedTask = taskCaptor.getValue();
            assertThat(savedTask.status()).isEqualTo(TaskStatus.REJETEE);
            assertThat(savedTask.rejectionReason()).isEqualTo("Le rapport est incomplet");
            assertThat(savedTask.rejectedBy()).isEqualTo(managerId);
            assertThat(savedTask.rejectedAt()).isNotNull();

            // Event avec le motif
            ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
            then(eventPublisher).should().publish(eventCaptor.capture());
            TaskRejected event = (TaskRejected) eventCaptor.getValue();
            assertThat(event.rejectionReason()).isEqualTo("Le rapport est incomplet");
        }

        @Test
        @DisplayName("❌ Rejet sans motif → RejectionReasonRequiredException (RG-06)")
        void reject_without_reason_throws_exception() {
            Task task = Task.create("Rapport", null, Priority.NORMALE, null, ownerId, teamId);

            given(cachePort.getById(task.id())).willReturn(Optional.empty());
            given(taskRepository.findById(task.id())).willReturn(Optional.of(task));
            given(userRepository.findById(managerId)).willReturn(Optional.of(manager));

            // Motif null → exception
            assertThatThrownBy(() -> service.rejectTask(task.id(), null, managerId))
                    .isInstanceOf(RejectionReasonRequiredException.class)
                    .hasMessageContaining("RG-06");

            // Motif vide → exception
            assertThatThrownBy(() -> service.rejectTask(task.id(), "  ", managerId))
                    .isInstanceOf(RejectionReasonRequiredException.class);

            then(taskRepository).should(never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  CAS DE TEST : Modification après rejet (RG-07)
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateTask — RG-07")
    class UpdateTaskTests {

        @Test
        @DisplayName("✅ Modification d'une tâche REJETEE → repasse en A_FAIRE (RG-07)")
        void updating_rejected_task_resets_to_a_faire() {
            // GIVEN : une tâche REJETEE
            // Task.create passe ownerId directement → reject() le préserve → pas de réassignation
            Task rejectedTask = Task.create("Rapport", null, Priority.NORMALE, null, ownerId, teamId)
                    .reject("Incomplet", managerId);

            given(cachePort.getById(rejectedTask.id())).willReturn(Optional.empty());
            given(taskRepository.findById(rejectedTask.id())).willReturn(Optional.of(rejectedTask));
            given(userRepository.findById(ownerId)).willReturn(Optional.of(gestionnaire));

            // WHEN : le Gestionnaire modifie la tâche rejetée
            service.updateTask(rejectedTask.id(), "Rapport corrigé", "Contenu complet",
                    Priority.HAUTE, null, ownerId);

            // THEN : repasse en A_FAIRE, motif de rejet effacé (RG-07)
            ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
            then(taskRepository).should().save(taskCaptor.capture());

            Task savedTask = taskCaptor.getValue();
            assertThat(savedTask.status()).isEqualTo(TaskStatus.A_FAIRE);  // RG-07
            assertThat(savedTask.rejectionReason()).isNull();               // Motif effacé
            assertThat(savedTask.title()).isEqualTo("Rapport corrigé");
            assertThat(savedTask.priority()).isEqualTo(Priority.HAUTE);
        }

        @Test
        @DisplayName("❌ Modification d'une tâche DONE → InvalidTaskStatusException")
        void cannot_update_done_task() {
            Task doneTask = Task.create("Tâche", null, Priority.NORMALE, null, ownerId, teamId)
                    .validate(managerId)
                    .markAsDone(managerId);

            given(cachePort.getById(doneTask.id())).willReturn(Optional.empty());
            given(taskRepository.findById(doneTask.id())).willReturn(Optional.of(doneTask));
            given(userRepository.findById(ownerId)).willReturn(Optional.of(gestionnaire));

            assertThatThrownBy(() ->
                    service.updateTask(doneTask.id(), "Nouveau titre", null, null, null, ownerId)
            ).isInstanceOf(InvalidTaskStatusException.class);
        }

        @Test
        @DisplayName("❌ Gestionnaire ne peut pas modifier la tâche d'un autre → UnauthorizedActionException")
        void gestionnaire_cannot_modify_others_task() {
            UserId autreGestionnaire = UserId.generate();
            // Tâche appartenant à autreGestionnaire
            Task task = Task.create("Tâche d'Alice", null, Priority.NORMALE, null,
                    autreGestionnaire, teamId);

            given(cachePort.getById(task.id())).willReturn(Optional.empty());
            given(taskRepository.findById(task.id())).willReturn(Optional.of(task));
            given(userRepository.findById(ownerId)).willReturn(Optional.of(gestionnaire));

            // Bob (ownerId) tente de modifier la tâche d'Alice (autreGestionnaire)
            assertThatThrownBy(() ->
                    service.updateTask(task.id(), "Titre modifié", null, null, null, ownerId)
            ).isInstanceOf(UnauthorizedActionException.class);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  CAS DE TEST : Suppression (RG-05)
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deleteTask — RG-05")
    class DeleteTaskTests {

        @Test
        @DisplayName("✅ Suppression d'une tâche A_FAIRE → OK")
        void can_delete_a_faire_task() {
            // Task.create passe ownerId directement → variable effectively final
            Task task = Task.create("À supprimer", null, Priority.BASSE, null, ownerId, teamId);

            given(cachePort.getById(task.id())).willReturn(Optional.empty());
            given(taskRepository.findById(task.id())).willReturn(Optional.of(task));
            given(userRepository.findById(ownerId)).willReturn(Optional.of(gestionnaire));

            service.deleteTask(task.id(), ownerId);

            then(taskRepository).should().deleteById(task.id());
        }

        @Test
        @DisplayName("❌ Suppression d'une tâche REJETEE → UnauthorizedActionException (RG-05)")
        void cannot_delete_rejected_task() {
            // IMPORTANT : variable effectively final (pas de réassignation)
            // → obligatoire pour l'utiliser dans la lambda assertThatThrownBy
            // Task.create(..., ownerId, ...).reject(...) préserve ownerId → pas besoin de new Task()
            Task rejectedTask = Task.create("Tâche", null, Priority.NORMALE, null, ownerId, teamId)
                    .reject("Motif", managerId);

            given(cachePort.getById(rejectedTask.id())).willReturn(Optional.empty());
            given(taskRepository.findById(rejectedTask.id())).willReturn(Optional.of(rejectedTask));
            given(userRepository.findById(ownerId)).willReturn(Optional.of(gestionnaire));

            // rejectedTask est effectively final → utilisable dans la lambda
            assertThatThrownBy(() -> service.deleteTask(rejectedTask.id(), ownerId))
                    .isInstanceOf(UnauthorizedActionException.class);

            then(taskRepository).should(never()).deleteById(any());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  CAS DE TEST : Récupération de tâches (cache)
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getMyTasks — Cache-Aside")
    class GetMyTasksTests {

        @Test
        @DisplayName("✅ Cache hit → le repository n'est pas appelé")
        void cache_hit_skips_repository() {
            List<Task> cachedTasks = List.of(
                    Task.create("Tâche 1", null, Priority.NORMALE, null, ownerId, teamId)
            );
            // Cache contient les tâches
            given(cachePort.getByOwner(ownerId)).willReturn(Optional.of(cachedTasks));

            List<Task> result = service.getMyTasks(ownerId);

            assertThat(result).hasSize(1);
            // Le repository n'est jamais consulté (cache hit)
            then(taskRepository).should(never()).findByOwner(any());
        }

        @Test
        @DisplayName("✅ Cache miss → charge depuis BDD puis met en cache")
        void cache_miss_loads_from_repository_and_caches() {
            List<Task> dbTasks = List.of(
                    Task.create("Tâche 1", null, Priority.NORMALE, null, ownerId, teamId),
                    Task.create("Tâche 2", null, Priority.HAUTE,   null, ownerId, teamId)
            );
            // Cache vide
            given(cachePort.getByOwner(ownerId)).willReturn(Optional.empty());
            // Repository contient les données
            given(taskRepository.findByOwner(ownerId)).willReturn(dbTasks);

            List<Task> result = service.getMyTasks(ownerId);

            assertThat(result).hasSize(2);
            // Le repository a été consulté
            then(taskRepository).should().findByOwner(ownerId);
            // Le résultat a été mis en cache pour les prochains appels
            then(cachePort).should().putByOwner(ownerId, dbTasks);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  CAS DE TEST : Tâche introuvable
    // ══════════════════════════════════════════════════════════

    @Test
    @DisplayName("❌ Tâche introuvable → TaskNotFoundException")
    void task_not_found_throws_exception() {
        TaskId inconnuId = TaskId.generate();
        given(cachePort.getById(inconnuId)).willReturn(Optional.empty());
        given(taskRepository.findById(inconnuId)).willReturn(Optional.empty());
        given(userRepository.findById(managerId)).willReturn(Optional.of(manager));

        assertThatThrownBy(() -> service.validateTask(inconnuId, managerId))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining(inconnuId.toString());
    }
}
