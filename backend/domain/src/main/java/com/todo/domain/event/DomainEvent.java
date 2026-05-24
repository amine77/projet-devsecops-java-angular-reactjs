package com.todo.domain.event;

import java.time.Instant;

/**
 * ══════════════════════════════════════════════════════════════
 *  Interface marqueur — Domain Event
 * ══════════════════════════════════════════════════════════════
 *
 *  Un Domain Event représente quelque chose qui s'est passé dans
 *  le domaine. Il est immuable, horodaté, et décrit un fait passé
 *  (nom au passé : TaskCreated, NOT CreateTask).
 *
 *  Pourquoi des events ?
 *  → Découplage : le domaine annonce ce qui s'est passé.
 *    Les autres systèmes (Kafka, SES, WebSocket) réagissent.
 *  → La logique domaine ne dépend pas des systèmes de notification.
 *
 *  Flux dans ce projet :
 *    TaskDomainService → EventPublisher (port out) → KafkaEventPublisher (infra)
 *                                                 → SesNotificationAdapter (infra)
 * ══════════════════════════════════════════════════════════════
 */
public interface DomainEvent {

    /** Identifiant unique de l'événement — utile pour la déduplication Kafka. */
    String eventId();

    /** Type de l'événement — utilisé comme clé de routage Kafka. */
    String eventType();

    /** Horodatage de l'occurrence — toujours en UTC. */
    Instant occurredAt();
}
