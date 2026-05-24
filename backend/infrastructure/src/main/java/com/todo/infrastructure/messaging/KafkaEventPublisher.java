package com.todo.infrastructure.messaging;

import com.todo.domain.event.DomainEvent;
import com.todo.domain.port.out.EventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * ══════════════════════════════════════════════════════════════
 *  ADAPTATEUR — KafkaEventPublisher
 * ══════════════════════════════════════════════════════════════
 *
 *  Implémente EventPublisher (port domaine) via Apache Kafka.
 *
 *  RÔLE DE KAFKA DANS CE PROJET :
 *  ┌──────────────────────────────────────────────────────────┐
 *  │  PRODUCTEUR (ce service)                                 │
 *  │  KafkaEventPublisher → topic "task-events"               │
 *  │                                                          │
 *  │  CONSOMMATEURS (autres services)                         │
 *  │  → EmailNotificationConsumer : task-events → SES email   │
 *  │  → AuditConsumer            : task-events → MongoDB audit│
 *  │  → MetricsConsumer          : task-events → Prometheus   │
 *  └──────────────────────────────────────────────────────────┘
 *
 *  POURQUOI KAFKA ET PAS UNE NOTIFICATION DIRECTE ?
 *  → Découplage : le service de tâches ne sait pas qui consomme ses events
 *  → Résilience : si le service d'email est down, les events sont en attente
 *  → Audit : chaque event est persisté 30 jours dans le topic "task-audit"
 *  → Scalabilité : on peut ajouter des consommateurs sans modifier le producteur
 *  → Rejeu : en cas de bug, on peut rejouer les events depuis le début du topic
 *
 *  STRATÉGIE DE CLE KAFKA :
 *  → Clé = taskId.toString() (UUID)
 *  → Même taskId → même partition (ordering garanti par tâche)
 *  → Exemple : tous les events de la tâche "abc-123" sont dans la partition N
 *    → Les consumers voient les events dans l'ordre pour cette tâche
 *
 *  ENVOI ASYNCHRONE vs SYNCHRONE :
 *  → send() est asynchrone (non-bloquant)
 *  → Le callback (whenComplete) logue succès ou échec APRÈS retour au domaine
 *  → Le domaine n'attend pas la confirmation Kafka (fire-and-forget)
 *  → Avantage : la réponse HTTP est envoyée rapidement (< 10ms vs 50ms+)
 *  → Risque : si Kafka est down, l'event est perdu (pour l'instant)
 *  → Phase future : transactional outbox pattern (écrire l'event en DB
 *    dans la même transaction que la modification, puis publisher async)
 *
 *  TOPIC : "task-events"
 *  → Configuré dans helm/values/kafka-values.yaml avec 3 partitions, 7j rétention
 *  → En local : Redpanda (docker-compose.yml), même protocol que Kafka
 * ══════════════════════════════════════════════════════════════
 */
@Component
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    /** Nom du topic Kafka — aligné avec la configuration helm/kafka */
    private static final String TOPIC = "task-events";

    /**
     * KafkaTemplate est auto-configuré par Spring Boot Kafka Starter.
     * Configuré pour sérialiser les values en JSON (KafkaConfig.java).
     * String = clé (taskId), DomainEvent = valeur (sérialisée en JSON).
     */
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publie un Domain Event sur le topic Kafka "task-events".
     *
     * FLOW :
     * 1. Extraire la clé de partitionnement (eventId → String)
     *    Note : dans une v2, on utiliserait taskId comme clé pour le ordering
     * 2. Envoyer de manière asynchrone sur le topic
     * 3. Logger le résultat dans le callback CompletableFuture
     *
     * @param event Domain Event à publier (TaskCreated, TaskValidated, etc.)
     */
    @Override
    public void publish(DomainEvent event) {
        // La clé de partitionnement (eventId) assure la distribution uniforme
        // Pour un ordering par tâche, on utiliserait taskId ici
        String partitionKey = event.eventId().toString();

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC, partitionKey, event);

        // Callback asynchrone — ne bloque pas le thread courant
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Event publié: type={} partition={} offset={}",
                        event.eventType(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                // En production : alerter (PagerDuty, CloudWatch Alarm)
                // Phase future : transactional outbox pour retry automatique
                log.error("ÉCHEC publication event {}: {}", event.eventType(), ex.getMessage(), ex);
            }
        });
    }
}
