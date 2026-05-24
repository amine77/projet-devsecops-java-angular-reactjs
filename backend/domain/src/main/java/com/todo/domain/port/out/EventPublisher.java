package com.todo.domain.port.out;

import com.todo.domain.event.DomainEvent;

/**
 * ══════════════════════════════════════════════════════════════
 *  PORT SORTANT — EventPublisher
 * ══════════════════════════════════════════════════════════════
 *
 *  Le domaine publie des événements sans savoir comment.
 *  L'infrastructure décide du transport (Kafka, SNS, WebSocket...).
 *
 *  Implémentations dans infrastructure/ :
 *  → KafkaEventPublisher   : publie sur le topic "task-events" (profil local+aws)
 *  → InMemoryEventPublisher: pour les tests (capture les events en mémoire)
 *
 *  Pattern important : le domaine ne dépend pas de Kafka.
 *  Si on migre de Kafka vers RabbitMQ, le domaine ne change pas.
 * ══════════════════════════════════════════════════════════════
 */
public interface EventPublisher {

    /**
     * Publie un événement domaine de manière asynchrone.
     *
     * @param event l'événement à publier (immuable — Record)
     */
    void publish(DomainEvent event);
}
