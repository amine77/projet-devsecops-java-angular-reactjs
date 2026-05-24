package com.todo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ══════════════════════════════════════════════════════════════
 *  POINT D'ENTRÉE — TodoApplication
 * ══════════════════════════════════════════════════════════════
 *
 *  @SpringBootApplication est un raccourci pour trois annotations :
 *  → @Configuration       : cette classe peut déclarer des @Bean
 *  → @EnableAutoConfiguration : Spring Boot configure automatiquement
 *    les composants détectés dans le classpath (JPA, Redis, Kafka, Security...)
 *  → @ComponentScan       : Spring scanne com.todo.** pour trouver les Beans
 *    (@Component, @Service, @Repository, @RestController...)
 *
 *  STRUCTURE DES MODULES MAVEN :
 *  ┌─────────────────────────────────────────────────────┐
 *  │  todo-domain       (aucune dépendance Spring)       │
 *  │      ↑                                              │
 *  │  todo-application  (Jakarta Validation uniquement)  │
 *  │      ↑                                              │
 *  │  todo-infrastructure (Spring Boot, JPA, Kafka...)   │
 *  │      ← TodoApplication est ICI                      │
 *  └─────────────────────────────────────────────────────┘
 *
 *  POURQUOI DANS LE MODULE INFRASTRUCTURE ?
 *  → L'application Spring Boot est un adaptateur entrant
 *  → Le domaine et l'application n'ont pas besoin de Spring pour exister
 *  → La main class orchestre le démarrage de TOUTE l'infrastructure
 *
 *  @EnableScheduling :
 *  → Active les tâches planifiées (@Scheduled)
 *  → Utilisé pour l'envoi des rappels de tâches en retard
 *    (ScheduledTaskService — créé dans cette phase)
 *  → Exemple : tous les matins à 8h → email aux Managers
 *
 *  PROFILS SPRING :
 *  → local  : docker-compose (PostgreSQL, Redis, Redpanda, Keycloak locaux)
 *  → aws    : RDS, ElastiCache, MSK, Cognito (services managés AWS)
 *  → test   : Testcontainers (DB éphémère pour les tests d'intégration)
 *
 *  LANCEMENT LOCAL :
 *  → mvn spring-boot:run -Dspring-boot.run.profiles=local
 *  → Ou depuis IntelliJ : Run Configuration avec VM option -Dspring.profiles.active=local
 *
 *  VIRTUAL THREADS (Java 21) :
 *  → Activé dans application.yml : spring.threads.virtual.enabled=true
 *  → Spring Boot 3.2+ délègue automatiquement au scheduler de Virtual Threads
 *  → Chaque requête HTTP = un Virtual Thread (léger, scalable)
 *  → Aucune modification du code nécessaire — transparent
 * ══════════════════════════════════════════════════════════════
 */
@SpringBootApplication
@EnableScheduling
public class TodoApplication {

    public static void main(String[] args) {
        SpringApplication.run(TodoApplication.class, args);
    }
}
