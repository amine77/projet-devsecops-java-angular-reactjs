package com.todo.infrastructure.config;

import com.todo.application.handler.CreateTaskHandler;
import com.todo.application.handler.DeleteTaskHandler;
import com.todo.application.handler.MarkAsDoneHandler;
import com.todo.application.handler.RejectTaskHandler;
import com.todo.application.handler.UpdateTaskHandler;
import com.todo.application.handler.ValidateTaskHandler;
import com.todo.application.query.TaskQueryHandler;
import com.todo.domain.port.in.TaskUseCase;
import com.todo.domain.port.out.CachePort;
import com.todo.domain.port.out.EventPublisher;
import com.todo.domain.port.out.NotificationPort;
import com.todo.domain.port.out.TaskRepository;
import com.todo.domain.port.out.UserRepository;
import com.todo.domain.service.TaskDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * ══════════════════════════════════════════════════════════════
 *  CONFIGURATION SPRING — DomainConfig
 * ══════════════════════════════════════════════════════════════
 *
 *  C'est ici que l'architecture hexagonale est "câblée" :
 *  les ports (interfaces du domaine) sont connectés aux adaptateurs
 *  (implémentations infrastructure).
 *
 *  PRINCIPE DE DÉPENDANCE INVERSÉE (DIP) :
 *  → Le domaine définit les interfaces (ports)
 *  → L'infrastructure implémente ces interfaces (adaptateurs)
 *  → Spring injecte les implémentations au démarrage
 *  → Le domaine ne connaît que les interfaces — jamais les classes concrètes
 *
 *  POURQUOI @Configuration ET PAS @Service SUR TaskDomainService ?
 *  → TaskDomainService est dans le module `domain` qui n'a PAS de dépendance Spring
 *  → On ne peut pas mettre @Service dessus sans violer l'architecture hexagonale
 *  → La solution : déclarer le Bean Spring ICI, dans l'infrastructure
 *  → C'est la technique "Configuration as Adapter" — la config Spring EST un adaptateur
 *
 *  ORDRE DE RÉSOLUTION DES DÉPENDANCES :
 *  Spring résout le graphe de dépendances automatiquement :
 *  TaskRepositoryAdapter → TaskJpaRepository (Spring Data auto-créé)
 *  RedisTaskCacheAdapter → RedisTemplate (auto-configuré par Spring Boot)
 *  KafkaEventPublisher → KafkaTemplate (auto-configuré par Spring Boot)
 *  SesNotificationAdapter → AwsSesClient (déclaré dans AwsConfig ou auto-configuré)
 *  ↓
 *  TaskDomainService (notre Bean manuel) reçoit tous les adaptateurs
 *  ↓
 *  Handlers reçoivent TaskDomainService (via interface TaskUseCase)
 *
 *  @EnableTransactionManagement :
 *  → Active le support de @Transactional sur les Beans Spring
 *  → Les transactions sont gérées dans TaskRepositoryAdapter (couche infra)
 *    et non dans TaskDomainService (couche domaine)
 *  → Règle : JAMAIS @Transactional dans le domaine — les transactions
 *    sont une préoccupation technique, pas métier
 * ══════════════════════════════════════════════════════════════
 */
@Configuration
@EnableTransactionManagement
public class DomainConfig {

    // ══════════════════════════════════════════════════════════
    //  DOMAIN SERVICE — point central de l'orchestration
    // ══════════════════════════════════════════════════════════

    /**
     * Crée le TaskDomainService en injectant tous les ports sortants.
     *
     * Spring détecte automatiquement les implémentations des interfaces :
     * - TaskRepository     → TaskRepositoryAdapter (@Repository)
     * - UserRepository     → UserRepositoryAdapter (@Repository)
     * - EventPublisher     → KafkaEventPublisher (@Component)
     * - CachePort          → RedisTaskCacheAdapter (@Component)
     * - NotificationPort   → SesNotificationAdapter (@Component)
     *
     * Si une implémentation manque → NoSuchBeanDefinitionException au démarrage
     * C'est volontaire : on veut un "fail fast" en développement.
     */
    @Bean
    public TaskUseCase taskUseCase(
            TaskRepository taskRepository,
            UserRepository userRepository,
            EventPublisher eventPublisher,
            CachePort cachePort,
            NotificationPort notificationPort
    ) {
        // On retourne l'interface TaskUseCase, pas TaskDomainService.
        // Spring injectera cette interface partout où c'est demandé.
        // Avantage : on peut swapper l'implémentation sans changer le code des consommateurs.
        return new TaskDomainService(
                taskRepository,
                userRepository,
                eventPublisher,
                cachePort,
                notificationPort
        );
    }

    // ══════════════════════════════════════════════════════════
    //  COMMAND HANDLERS — un bean par cas d'usage de modification
    // ══════════════════════════════════════════════════════════

    @Bean
    public CreateTaskHandler createTaskHandler(TaskUseCase taskUseCase) {
        return new CreateTaskHandler(taskUseCase);
    }

    @Bean
    public UpdateTaskHandler updateTaskHandler(TaskUseCase taskUseCase) {
        return new UpdateTaskHandler(taskUseCase);
    }

    @Bean
    public DeleteTaskHandler deleteTaskHandler(TaskUseCase taskUseCase) {
        return new DeleteTaskHandler(taskUseCase);
    }

    @Bean
    public ValidateTaskHandler validateTaskHandler(TaskUseCase taskUseCase) {
        return new ValidateTaskHandler(taskUseCase);
    }

    @Bean
    public RejectTaskHandler rejectTaskHandler(TaskUseCase taskUseCase) {
        return new RejectTaskHandler(taskUseCase);
    }

    @Bean
    public MarkAsDoneHandler markAsDoneHandler(TaskUseCase taskUseCase) {
        return new MarkAsDoneHandler(taskUseCase);
    }

    // ══════════════════════════════════════════════════════════
    //  QUERY HANDLER — un seul bean pour tous les cas de lecture
    // ══════════════════════════════════════════════════════════

    @Bean
    public TaskQueryHandler taskQueryHandler(TaskUseCase taskUseCase) {
        return new TaskQueryHandler(taskUseCase);
    }
}
