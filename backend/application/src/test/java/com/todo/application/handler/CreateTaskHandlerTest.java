package com.todo.application.handler;

import com.todo.application.command.CreateTaskCommand;
import com.todo.domain.exception.UnauthorizedActionException;
import com.todo.domain.model.Priority;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.UserId;
import com.todo.domain.port.in.TaskUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

/**
 * ══════════════════════════════════════════════════════════════
 *  TEST — CreateTaskHandlerTest
 * ══════════════════════════════════════════════════════════════
 *
 *  STRATÉGIE DE TEST DE LA COUCHE APPLICATION :
 *  → On mock TaskUseCase (le port entrant du domaine)
 *  → On teste que le Handler délègue correctement avec les bons paramètres
 *  → On teste que les exceptions du domaine se propagent sans être swallowées
 *
 *  CE QU'ON NE TESTE PAS ICI :
 *  → La logique métier (testée dans TaskDomainServiceTest)
 *  → La sérialisation HTTP (testée dans les tests d'intégration du contrôleur)
 *  → La validation Bean Validation (@Valid) — nécessite un contexte Spring
 *    (testée dans les tests d'intégration avec @WebMvcTest)
 *
 *  POURQUOI CE NIVEAU DE TEST ?
 *  → Vérifier le "câblage" : est-ce que le Handler appelle le bon UseCase
 *    avec les bons arguments dans le bon ordre ?
 *  → Vérifier la propagation des exceptions.
 *  → Tests ultrarapides (< 50ms) — pas de Spring, pas de DB.
 * ══════════════════════════════════════════════════════════════
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CreateTaskHandler")
class CreateTaskHandlerTest {

    // ── Mock du port entrant (interface du domaine) ──────────────
    @Mock
    private TaskUseCase taskUseCase;

    // ── Sous-test (SUT — System Under Test) ─────────────────────
    private CreateTaskHandler handler;

    @BeforeEach
    void setUp() {
        // Instanciation manuelle — pas de Spring, pas d'@Autowired
        // C'est l'avantage de l'injection par constructeur
        handler = new CreateTaskHandler(taskUseCase);
    }

    // ── Fixtures partagées ──────────────────────────────────────
    private final UserId gestionnaire = UserId.generate();
    private final TaskId newTaskId    = TaskId.generate();

    @Nested
    @DisplayName("✅ Cas nominal")
    class NominalCases {

        @Test
        @DisplayName("délègue la création au UseCase et retourne le TaskId")
        void shouldDelegateToUseCaseAndReturnTaskId() {
            // GIVEN
            CreateTaskCommand command = new CreateTaskCommand(
                    "Préparer le rapport mensuel",
                    "Rapport d'activité pour le comité",
                    Priority.HAUTE,
                    LocalDate.now().plusDays(7),
                    gestionnaire
            );
            // On configure le mock : quand taskUseCase.createTask() est appelé
            // avec ces arguments, retourner newTaskId
            given(taskUseCase.createTask(
                    command.title(),
                    command.description(),
                    command.priority(),
                    command.dueDate(),
                    command.actorId()
            )).willReturn(newTaskId);

            // WHEN
            TaskId result = handler.handle(command);

            // THEN
            assertThat(result).isEqualTo(newTaskId);
            // Vérifier que le UseCase a été appelé exactement une fois
            then(taskUseCase).should(times(1)).createTask(
                    command.title(),
                    command.description(),
                    command.priority(),
                    command.dueDate(),
                    command.actorId()
            );
        }

        @Test
        @DisplayName("fonctionne sans description ni dueDate (champs optionnels)")
        void shouldWorkWithNullOptionalFields() {
            // GIVEN — description et dueDate sont optionnels
            CreateTaskCommand command = new CreateTaskCommand(
                    "Tâche minimale",
                    null,         // description optionnelle
                    Priority.NORMALE,
                    null,         // dueDate optionnelle
                    gestionnaire
            );
            given(taskUseCase.createTask(
                    command.title(), null, command.priority(), null, command.actorId()
            )).willReturn(newTaskId);

            // WHEN
            TaskId result = handler.handle(command);

            // THEN
            assertThat(result).isNotNull().isEqualTo(newTaskId);
        }
    }

    @Nested
    @DisplayName("❌ Exceptions propagées depuis le domaine")
    class ExceptionCases {

        @Test
        @DisplayName("propage UnauthorizedActionException si l'acteur n'a pas le droit")
        void shouldPropagateUnauthorizedActionException() {
            // GIVEN — le domaine lève une exception (ex: MANAGER essaie de créer)
            CreateTaskCommand command = new CreateTaskCommand(
                    "Tâche interdite",
                    null,
                    Priority.NORMALE,
                    null,
                    gestionnaire
            );
            given(taskUseCase.createTask(
                    command.title(), null, command.priority(), null, command.actorId()
            )).willThrow(new UnauthorizedActionException(gestionnaire, null, "créer une tâche"));

            // WHEN / THEN — le Handler NE DOIT PAS swallower l'exception
            assertThatThrownBy(() -> handler.handle(command))
                    .isInstanceOf(UnauthorizedActionException.class);
        }
    }
}
