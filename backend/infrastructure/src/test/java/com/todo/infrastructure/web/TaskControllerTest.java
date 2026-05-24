package com.todo.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.todo.application.handler.CreateTaskHandler;
import com.todo.application.handler.DeleteTaskHandler;
import com.todo.application.handler.MarkAsDoneHandler;
import com.todo.application.handler.RejectTaskHandler;
import com.todo.application.handler.UpdateTaskHandler;
import com.todo.application.handler.ValidateTaskHandler;
import com.todo.application.query.TaskQueryHandler;
import com.todo.domain.exception.TaskNotFoundException;
import com.todo.domain.model.Priority;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.TaskStatus;
import com.todo.domain.model.TeamId;
import com.todo.domain.model.UserId;
import com.todo.infrastructure.config.JwtToUserConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ══════════════════════════════════════════════════════════════
 *  TEST D'INTÉGRATION WEB — TaskControllerTest
 * ══════════════════════════════════════════════════════════════
 *
 *  STRATÉGIE : @WebMvcTest
 *  → Démarre UNIQUEMENT la couche web Spring MVC (pas la DB, pas Kafka)
 *  → Charge : contrôleur, filtres de sécurité, validation, serialization JSON
 *  → Mocks : tous les beans non-web (handlers, query handler, JWT converter)
 *
 *  POURQUOI @WebMvcTest ET PAS @SpringBootTest ?
 *  → @SpringBootTest charge TOUT le contexte Spring (DB, Kafka, Redis...)
 *  → Plus lent (5-30s vs < 2s pour @WebMvcTest)
 *  → Pour les tests du contrôleur, on a besoin que de la couche web
 *
 *  AUTHENTIFICATION DANS LES TESTS :
 *  → .with(SecurityMockMvcRequestPostProcessors.jwt()) simule un JWT valide
 *  → Évite de démarrer Keycloak ou de mocker le JwtDecoder
 *  → On peut personnaliser les claims JWT dans chaque test
 *
 *  MOCKING :
 *  → @MockBean : crée un mock Mockito ET l'enregistre dans le contexte Spring
 *  → Différent de @Mock (Mockito pur) qui ne s'intègre pas dans le contexte Spring
 *
 *  CE QU'ON TESTE :
 *  → Codes HTTP retournés (201, 204, 400, 403, 404...)
 *  → Présence du Location header sur POST
 *  → Validation Bean Validation (@NotBlank → 400)
 *  → Mapping des exceptions domaine → codes HTTP
 *  → Format JSON des réponses
 * ══════════════════════════════════════════════════════════════
 */
@WebMvcTest(TaskController.class)
@DisplayName("TaskController — Tests d'intégration Web")
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Mocks des handlers ──────────────────────────────────────
    @MockBean private CreateTaskHandler   createHandler;
    @MockBean private UpdateTaskHandler   updateHandler;
    @MockBean private DeleteTaskHandler   deleteHandler;
    @MockBean private ValidateTaskHandler validateHandler;
    @MockBean private RejectTaskHandler   rejectHandler;
    @MockBean private MarkAsDoneHandler   markAsDoneHandler;
    @MockBean private TaskQueryHandler    queryHandler;
    @MockBean private JwtToUserConverter  jwtConverter;

    /** UserId fixe pour tous les tests */
    private final UserId actorId = UserId.generate();

    @Nested
    @DisplayName("POST /api/tasks — Création")
    class CreateTaskTests {

        @Test
        @DisplayName("retourne 201 Created avec Location header")
        void shouldReturn201WithLocation() throws Exception {
            TaskId newTaskId = TaskId.generate();
            given(jwtConverter.extractUserId(any(Jwt.class))).willReturn(actorId);
            given(createHandler.handle(any())).willReturn(newTaskId);

            String body = objectMapper.writeValueAsString(new TaskRequest(
                    "Titre valide", "Description", Priority.HAUTE,
                    LocalDate.now().plusDays(7)
            ));

            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            // Simule un JWT valide avec Spring Security Test
                            .with(SecurityMockMvcRequestPostProcessors.jwt()))
                    .andExpect(status().isCreated())         // 201
                    .andExpect(header().exists("Location"))  // Location: /api/tasks/{uuid}
                    .andExpect(header().string("Location",
                            org.hamcrest.Matchers.containsString(newTaskId.value().toString())));
        }

        @Test
        @DisplayName("retourne 400 si titre vide (Bean Validation)")
        void shouldReturn400IfTitleBlank() throws Exception {
            String body = objectMapper.writeValueAsString(new TaskRequest(
                    "",  // @NotBlank violation
                    null, Priority.NORMALE, null
            ));

            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(SecurityMockMvcRequestPostProcessors.jwt()))
                    .andExpect(status().isBadRequest())        // 400
                    .andExpect(jsonPath("$.title").value("Erreur de validation"));
        }

        @Test
        @DisplayName("retourne 400 si priorité absente")
        void shouldReturn400IfPriorityNull() throws Exception {
            // JSON avec priority null — @NotNull violation
            String body = """
                    { "title": "Tâche valide", "priority": null }
                    """;

            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(SecurityMockMvcRequestPostProcessors.jwt()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("retourne 401 si pas de JWT")
        void shouldReturn401WithoutJwt() throws Exception {
            String body = objectMapper.writeValueAsString(
                new TaskRequest("Titre", null, Priority.NORMALE, null)
            );

            // Pas de .with(jwt()) → requête non authentifiée
            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized()); // 401
        }
    }

    @Nested
    @DisplayName("DELETE /api/tasks/{id} — Suppression")
    class DeleteTaskTests {

        @Test
        @DisplayName("retourne 204 No Content")
        void shouldReturn204() throws Exception {
            given(jwtConverter.extractUserId(any(Jwt.class))).willReturn(actorId);

            mockMvc.perform(delete("/api/tasks/" + UUID.randomUUID())
                            .with(SecurityMockMvcRequestPostProcessors.jwt()))
                    .andExpect(status().isNoContent()); // 204
        }

        @Test
        @DisplayName("retourne 404 si tâche introuvable")
        void shouldReturn404IfNotFound() throws Exception {
            given(jwtConverter.extractUserId(any(Jwt.class))).willReturn(actorId);
            willThrow(new TaskNotFoundException(TaskId.generate()))
                    .given(deleteHandler).handle(any());

            mockMvc.perform(delete("/api/tasks/" + UUID.randomUUID())
                            .with(SecurityMockMvcRequestPostProcessors.jwt()))
                    .andExpect(status().isNotFound())       // 404
                    .andExpect(jsonPath("$.title").value("Tâche introuvable"));
        }
    }

    @Nested
    @DisplayName("PUT /api/tasks/{id}/reject — Rejet")
    class RejectTaskTests {

        @Test
        @DisplayName("retourne 200 OK avec motif valide")
        void shouldReturn200WithReason() throws Exception {
            given(jwtConverter.extractUserId(any(Jwt.class))).willReturn(actorId);

            String body = objectMapper.writeValueAsString(
                new RejectRequest("Budget insuffisant")
            );

            mockMvc.perform(put("/api/tasks/" + UUID.randomUUID() + "/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(SecurityMockMvcRequestPostProcessors.jwt()))
                    .andExpect(status().isOk()); // 200
        }

        @Test
        @DisplayName("retourne 400 si motif absent (RG-06)")
        void shouldReturn400WithoutReason() throws Exception {
            given(jwtConverter.extractUserId(any(Jwt.class))).willReturn(actorId);

            // Motif vide → @NotBlank échoue
            String body = objectMapper.writeValueAsString(new RejectRequest(""));

            mockMvc.perform(put("/api/tasks/" + UUID.randomUUID() + "/reject")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                            .with(SecurityMockMvcRequestPostProcessors.jwt()))
                    .andExpect(status().isBadRequest()); // 400
        }
    }
}
