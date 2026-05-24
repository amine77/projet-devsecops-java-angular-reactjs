package com.todo.infrastructure.integration;

import com.todo.domain.model.Priority;
import com.todo.domain.model.Task;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.TaskStatus;
import com.todo.domain.model.TeamId;
import com.todo.domain.model.User;
import com.todo.domain.model.UserId;
import com.todo.domain.model.UserRole;
import com.todo.domain.port.in.TaskUseCase;
import com.todo.infrastructure.persistence.UserJpaRepository;
import com.todo.infrastructure.persistence.UserJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ══════════════════════════════════════════════════════════════
 *  TEST D'INTÉGRATION — TaskIntegrationTest
 * ══════════════════════════════════════════════════════════════
 *
 *  Tests d'intégration COMPLETS avec de vraies bases de données.
 *
 *  TESTCONTAINERS :
 *  → Démarre des containers Docker RÉELS pour les tests
 *  → PostgreSQL 16 réel (pas H2 en mémoire)
 *  → Redis 7 réel
 *  → Kafka/Redpanda : non démarré ici (remplacé par mock Kafka)
 *  → Durée de vie : containers démarrés AVANT les tests, arrêtés APRÈS
 *
 *  POURQUOI PAS H2 EN MÉMOIRE ?
 *  → H2 n'est PAS PostgreSQL :
 *    - Les types UUID (::uuid) sont différents
 *    - Les CHECK constraints se comportent différemment
 *    - Les window functions, les index partiels ne sont pas les mêmes
 *    - Les migrations Flyway peuvent passer en H2 mais échouer en PostgreSQL
 *  → RÈGLE : toujours tester contre la même base qu'en production
 *
 *  @Testcontainers :
 *  → Intégration JUnit 5 de Testcontainers
 *  → Gère automatiquement start/stop des containers annotés @Container
 *
 *  @SpringBootTest :
 *  → Charge le contexte Spring COMPLET (tous les beans)
 *  → Plus lent qu'un @WebMvcTest (quelques secondes)
 *  → Nécessaire pour tester l'intégration bout-en-bout :
 *    Contrôleur → Handler → DomainService → Repository → PostgreSQL
 *
 *  @ActiveProfiles("test") :
 *  → Active application-test.yml (pas de SES, S3, DynamoDB)
 *  → Utilise NoOpNotificationAdapter, InMemoryUserPreferenceAdapter
 *  → Configuration Kafka mockée
 *
 *  @DynamicPropertySource :
 *  → Injecte les URLs PostgreSQL/Redis des containers Testcontainers
 *    dans le contexte Spring AVANT le démarrage de l'application
 *  → Les ports sont aléatoires (évite les conflits) → on les récupère
 *    depuis les containers après démarrage
 *
 *  STRATÉGIE DES CONTAINERS (static) :
 *  → @Container static : démarré UNE fois pour toute la classe de test
 *  → @Container non-static : redémarré pour chaque méthode de test
 *  → Static = plus rapide (démarrage PostgreSQL ~2s au lieu de ~10s total)
 * ══════════════════════════════════════════════════════════════
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
// webEnvironment=NONE : pas de serveur HTTP démarré (on teste le service directement)
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Tests d'intégration — PostgreSQL + Redis réels (Testcontainers)")
class TaskIntegrationTest {

    // ══════════════════════════════════════════════════════════
    //  CONTAINERS TESTCONTAINERS (static = partagés entre tests)
    // ══════════════════════════════════════════════════════════

    /**
     * Container PostgreSQL 16.
     *
     * Testcontainers configure automatiquement :
     * → Utilisateur, mot de passe, nom de base (via PostgreSQLContainer)
     * → Flyway s'exécute automatiquement (V1__init_schema.sql) au démarrage Spring
     * → Port aléatoire sur l'hôte (mappé vers 5432 dans le container)
     */
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine")
    )
            .withDatabaseName("tododb_test")
            .withUsername("todouser")
            .withPassword("todopass")
            // Permet de réutiliser le container entre les runs pour accélérer les tests
            // (nécessite ~/.testcontainers.properties avec testcontainers.reuse.enable=true)
            .withReuse(true);

    /**
     * Container Redis 7.
     * Port interne Redis : 6379 (fixe dans le container)
     * Port externe : aléatoire (récupéré via getMappedPort)
     */
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")
    )
            .withExposedPorts(6379)
            .withReuse(true);

    /**
     * Injecte les propriétés des containers dans le contexte Spring
     * AVANT le démarrage de l'application.
     *
     * @DynamicPropertySource :
     * → Méthode statique appelée avant @BeforeAll / @SpringBootTest
     * → Remplace les valeurs de application.yml pour ce test
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL : utiliser l'URL générée par Testcontainers
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis : utiliser l'hôte et port du container
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // Désactiver Kafka (pas de container Kafka dans ces tests)
        // On utilise un mock ou on désactive le KafkaEventPublisher
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:19092");
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
    }

    // ══════════════════════════════════════════════════════════
    //  INJECTION DE BEANS SPRING
    // ══════════════════════════════════════════════════════════

    /** Le point d'entrée du domaine — testé via la vraie implémentation */
    @Autowired
    private TaskUseCase taskUseCase;

    /** Accès direct à la JPA pour préparer les données de test */
    @Autowired
    private UserJpaRepository userJpaRepository;

    // ── Fixtures ────────────────────────────────────────────────
    private UserId gestionnaire;
    private UserId manager;
    private TeamId team;

    /**
     * Préparation des données de test avant chaque méthode.
     *
     * @BeforeEach vs @BeforeAll :
     * → @BeforeEach : nettoie et réinitialise → tests indépendants (recommandé)
     * → @BeforeAll : données partagées → tests couplés (à éviter)
     *
     * On recrée les utilisateurs en base avant chaque test pour garantir
     * l'isolation complète.
     */
    @BeforeEach
    void setUp() {
        // Nettoyer la base entre les tests (Flyway a créé les tables)
        userJpaRepository.deleteAll();

        team = new TeamId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        gestionnaire = UserId.generate();
        manager = UserId.generate();

        // Créer le Gestionnaire en base via JPA directement (pas via le UseCase)
        // Le UseCase nécessite un User existant → on l'insère manuellement
        userJpaRepository.save(createUserEntity(gestionnaire, "Alice", "Martin",
                "alice@todo.com", UserRole.GESTIONNAIRE, team));
        userJpaRepository.save(createUserEntity(manager, "Bob", "Dupont",
                "bob@todo.com", UserRole.MANAGER, team));
    }

    // ══════════════════════════════════════════════════════════
    //  TESTS D'INTÉGRATION
    // ══════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Création et lecture de tâches")
    class CreateAndReadTests {

        @Test
        @DisplayName("le gestionnaire crée une tâche et peut la relire depuis la DB")
        void createAndReadTaskFromRealDatabase() {
            // GIVEN
            String title = "Préparer le rapport Q2";

            // WHEN — appel complet : DomainService → Repository → PostgreSQL
            TaskId taskId = taskUseCase.createTask(
                    title, "Description du rapport", Priority.HAUTE,
                    LocalDate.now().plusDays(30), gestionnaire
            );

            // THEN — relire depuis la VRAIE base PostgreSQL
            Task task = taskUseCase.getTaskById(taskId, gestionnaire);
            assertThat(task.title()).isEqualTo(title);
            assertThat(task.status()).isEqualTo(TaskStatus.A_FAIRE);
            assertThat(task.priority()).isEqualTo(Priority.HAUTE);
            assertThat(task.ownerId()).isEqualTo(gestionnaire);
            assertThat(task.teamId()).isEqualTo(team);
        }

        @Test
        @DisplayName("getMyTasks lit depuis Redis en cache (cache miss puis cache hit)")
        void cacheAsideWorksWithRealRedis() {
            // GIVEN — créer des tâches
            taskUseCase.createTask("Tâche 1", null, Priority.NORMALE, null, gestionnaire);
            taskUseCase.createTask("Tâche 2", null, Priority.HAUTE, null, gestionnaire);

            // WHEN — 1er appel : cache MISS → lit depuis PostgreSQL + peuple Redis
            List<Task> firstCall = taskUseCase.getMyTasks(gestionnaire);

            // WHEN — 2ème appel : cache HIT → lit depuis Redis
            List<Task> secondCall = taskUseCase.getMyTasks(gestionnaire);

            // THEN — les deux appels retournent les mêmes données
            assertThat(firstCall).hasSize(2);
            assertThat(secondCall).hasSize(2);
            assertThat(firstCall).containsExactlyInAnyOrderElementsOf(secondCall);
        }
    }

    @Nested
    @DisplayName("Machine d'état complète (bout-en-bout)")
    class StateMachineIntegrationTests {

        @Test
        @DisplayName("flux complet : créer → valider → marquer done avec données persistées")
        void fullWorkflowPersistedInRealDatabase() {
            // GIVEN — créer une tâche
            TaskId taskId = taskUseCase.createTask(
                    "Rapport annuel", null, Priority.URGENTE, null, gestionnaire
            );

            // WHEN — valider
            taskUseCase.validateTask(taskId, manager);
            Task validated = taskUseCase.getTaskById(taskId, manager);

            // THEN — vérifier la persistance en PostgreSQL
            assertThat(validated.status()).isEqualTo(TaskStatus.VALIDEE);
            assertThat(validated.validatedBy()).isEqualTo(manager);
            assertThat(validated.validatedAt()).isNotNull();

            // WHEN — marquer done
            taskUseCase.markAsDone(taskId, manager);
            Task done = taskUseCase.getTaskById(taskId, manager);

            // THEN — état final persisté
            assertThat(done.status()).isEqualTo(TaskStatus.DONE);
            assertThat(done.doneBy()).isEqualTo(manager);
            assertThat(done.doneAt()).isNotNull();
            assertThat(done.status().isTerminal()).isTrue();
        }

        @Test
        @DisplayName("flux rejet : créer → rejeter (avec motif) → modifier → A_FAIRE (RG-07)")
        void rejectAndUpdateResetsToAFaire() {
            // GIVEN
            TaskId taskId = taskUseCase.createTask(
                    "Plan de formation", null, Priority.NORMALE, null, gestionnaire
            );

            // WHEN — rejeter avec motif (RG-06)
            taskUseCase.rejectTask(taskId, "Budget non disponible ce trimestre", manager);
            Task rejected = taskUseCase.getTaskById(taskId, gestionnaire);
            assertThat(rejected.status()).isEqualTo(TaskStatus.REJETEE);
            assertThat(rejected.rejectionReason()).isEqualTo("Budget non disponible ce trimestre");

            // WHEN — modifier la tâche (RG-07 : doit repasser en A_FAIRE)
            taskUseCase.updateTask(taskId, "Plan de formation révisé",
                    "Budget réduit pris en compte", Priority.BASSE, null, gestionnaire);
            Task updated = taskUseCase.getTaskById(taskId, gestionnaire);

            // THEN — RG-07 vérifié en base réelle
            assertThat(updated.status()).isEqualTo(TaskStatus.A_FAIRE);
            assertThat(updated.rejectionReason()).isNull(); // motif effacé
            assertThat(updated.title()).isEqualTo("Plan de formation révisé");
        }

        @Test
        @DisplayName("la suppression fonctionne seulement en A_FAIRE (RG-05)")
        void deleteOnlyWorksInAFaireStatus() {
            // GIVEN
            TaskId taskId = taskUseCase.createTask(
                    "Tâche à supprimer", null, Priority.BASSE, null, gestionnaire
            );

            // WHEN — valider (passage en VALIDEE)
            taskUseCase.validateTask(taskId, manager);

            // THEN — ne peut pas supprimer une tâche VALIDEE (RG-05)
            assertThatThrownBy(() -> taskUseCase.deleteTask(taskId, gestionnaire))
                    .isInstanceOf(com.todo.domain.exception.UnauthorizedActionException.class);

            // GIVEN — créer une autre tâche et la supprimer en A_FAIRE
            TaskId taskIdToDelete = taskUseCase.createTask(
                    "Supprimable", null, Priority.BASSE, null, gestionnaire
            );
            // WHEN/THEN — doit fonctionner
            taskUseCase.deleteTask(taskIdToDelete, gestionnaire);

            // Vérifier que la tâche n'existe plus en base
            assertThatThrownBy(() -> taskUseCase.getTaskById(taskIdToDelete, gestionnaire))
                    .isInstanceOf(com.todo.domain.exception.TaskNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  HELPER
    // ══════════════════════════════════════════════════════════

    /**
     * Crée une UserJpaEntity pour les données de test.
     * Bypasse le UseCase (test d'intégration DB, pas test métier).
     */
    private UserJpaEntity createUserEntity(UserId id, String firstName, String lastName,
                                           String email, UserRole role, TeamId teamId) {
        User user = new User(id, "keycloak-" + id.value(), firstName, lastName,
                email, role, teamId,
                new com.todo.domain.model.UnitId(
                    UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")));
        return UserJpaEntity.fromDomain(user);
    }
}
