package com.todo.application.handler;

import com.todo.application.command.MarkAsDoneCommand;
import com.todo.application.command.RejectTaskCommand;
import com.todo.application.command.ValidateTaskCommand;
import com.todo.domain.exception.InvalidTaskStatusException;
import com.todo.domain.exception.RejectionReasonRequiredException;
import com.todo.domain.exception.UnauthorizedActionException;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.TaskStatus;
import com.todo.domain.model.UserId;
import com.todo.domain.port.in.TaskUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

/**
 * ══════════════════════════════════════════════════════════════
 *  TEST — ValidateAndRejectTaskHandlerTest
 * ══════════════════════════════════════════════════════════════
 *
 *  Tests des handlers Manager : Validate, Reject, MarkAsDone.
 *  Regroupés dans une classe car le pattern est similaire.
 *
 *  PATTERN COMMUN :
 *  1. given → mock willDoNothing (commandes sans retour) ou willThrow
 *  2. when  → handler.handle(command)
 *  3. then  → vérifier que le UseCase a été appelé (ou que l'exception remonte)
 *
 *  NOTE SUR willDoNothing() :
 *  → Les méthodes void de Mockito n'ont pas besoin de stubbing par défaut
 *    (elles font nothing par défaut).
 *  → On peut être explicite avec willDoNothing() pour la lisibilité.
 * ══════════════════════════════════════════════════════════════
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Validate / Reject / MarkAsDone Handlers")
class ValidateAndRejectTaskHandlerTest {

    @Mock
    private TaskUseCase taskUseCase;

    private ValidateTaskHandler validateHandler;
    private RejectTaskHandler   rejectHandler;
    private MarkAsDoneHandler   markAsDoneHandler;

    private final TaskId taskId  = TaskId.generate();
    private final UserId manager = UserId.generate();

    @BeforeEach
    void setUp() {
        validateHandler   = new ValidateTaskHandler(taskUseCase);
        rejectHandler     = new RejectTaskHandler(taskUseCase);
        markAsDoneHandler = new MarkAsDoneHandler(taskUseCase);
    }

    // ─────────────────────────────────────────────────────────────
    //  ValidateTaskHandler
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ValidateTaskHandler")
    class ValidateTests {

        @Test
        @DisplayName("délègue la validation au UseCase")
        void shouldDelegateToUseCase() {
            // GIVEN — la méthode void ne fait rien (comportement par défaut)
            ValidateTaskCommand command = new ValidateTaskCommand(taskId, manager);

            // WHEN
            validateHandler.handle(command);

            // THEN — vérifier que validateTask a bien été appelé une fois
            then(taskUseCase).should(times(1)).validateTask(taskId, manager);
        }

        @Test
        @DisplayName("propage InvalidTaskStatusException si la tâche n'est pas A_FAIRE")
        void shouldPropagateInvalidStatusException() {
            // GIVEN — le domaine lève une exception (ex: tâche déjà DONE)
            ValidateTaskCommand command = new ValidateTaskCommand(taskId, manager);
            willThrow(new InvalidTaskStatusException(taskId, TaskStatus.DONE, "valider"))
                    .given(taskUseCase).validateTask(taskId, manager);

            // WHEN / THEN
            assertThatThrownBy(() -> validateHandler.handle(command))
                    .isInstanceOf(InvalidTaskStatusException.class);
        }

        @Test
        @DisplayName("propage UnauthorizedActionException si GESTIONNAIRE essaie de valider")
        void shouldPropagateUnauthorizedForGestionnaire() {
            UserId gestionnaire = UserId.generate();
            ValidateTaskCommand command = new ValidateTaskCommand(taskId, gestionnaire);
            willThrow(new UnauthorizedActionException(gestionnaire, taskId, "changer le statut"))
                    .given(taskUseCase).validateTask(taskId, gestionnaire);

            assertThatThrownBy(() -> validateHandler.handle(command))
                    .isInstanceOf(UnauthorizedActionException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  RejectTaskHandler
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("RejectTaskHandler")
    class RejectTests {

        @Test
        @DisplayName("délègue le rejet avec le motif au UseCase")
        void shouldDelegateWithRejectionReason() {
            // GIVEN
            RejectTaskCommand command = new RejectTaskCommand(
                    taskId, "Budget insuffisant pour cette tâche", manager
            );

            // WHEN
            rejectHandler.handle(command);

            // THEN — vérifier que rejectTask a été appelé avec le BON motif
            then(taskUseCase).should(times(1))
                    .rejectTask(taskId, "Budget insuffisant pour cette tâche", manager);
        }

        @Test
        @DisplayName("propage RejectionReasonRequiredException si motif vide (RG-06)")
        void shouldPropagateWhenReasonMissing() {
            // NOTE : Normalement @NotBlank dans RejectTaskCommand intercepte ça
            // avant d'atteindre le Handler. Ce test vérifie la propagation si
            // la Command est construite programmatiquement (ex: consumer Kafka)
            // SANS passer par Bean Validation.
            RejectTaskCommand command = new RejectTaskCommand(taskId, "motif", manager);
            willThrow(new RejectionReasonRequiredException(taskId))
                    .given(taskUseCase).rejectTask(taskId, "motif", manager);

            assertThatThrownBy(() -> rejectHandler.handle(command))
                    .isInstanceOf(RejectionReasonRequiredException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  MarkAsDoneHandler
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MarkAsDoneHandler")
    class MarkAsDoneTests {

        @Test
        @DisplayName("délègue markAsDone au UseCase")
        void shouldDelegateToUseCase() {
            MarkAsDoneCommand command = new MarkAsDoneCommand(taskId, manager);

            markAsDoneHandler.handle(command);

            then(taskUseCase).should(times(1)).markAsDone(taskId, manager);
        }

        @Test
        @DisplayName("propage InvalidTaskStatusException si la tâche est déjà DONE")
        void shouldPropagateIfAlreadyDone() {
            MarkAsDoneCommand command = new MarkAsDoneCommand(taskId, manager);
            willThrow(new InvalidTaskStatusException(taskId, TaskStatus.DONE, "marquer comme terminée"))
                    .given(taskUseCase).markAsDone(taskId, manager);

            assertThatThrownBy(() -> markAsDoneHandler.handle(command))
                    .isInstanceOf(InvalidTaskStatusException.class);
        }
    }
}
