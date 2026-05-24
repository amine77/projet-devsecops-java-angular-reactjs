package com.todo.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * ══════════════════════════════════════════════════════════════
 *  CONFIGURATION KAFKA — KafkaConfig
 * ══════════════════════════════════════════════════════════════
 *
 *  Configure le KafkaTemplate utilisé par KafkaEventPublisher.
 *
 *  POURQUOI CONFIGURER MANUELLEMENT ?
 *  → Spring Boot Kafka auto-configure un KafkaTemplate<Object, Object>
 *    avec des sérialiseurs génériques.
 *  → On veut un KafkaTemplate<String, Object> avec JSON sérialiseur
 *    et les types Java 8 date/time correctement gérés.
 *
 *  ARCHITECTURE PRODUCTEUR KAFKA :
 *  ┌─────────────────────────────────────────────────────────┐
 *  │  KafkaEventPublisher                                    │
 *  │      ↓ kafkaTemplate.send(topic, key, event)            │
 *  │  KafkaTemplate<String, Object>                          │
 *  │      ↓ serialise en JSON                                │
 *  │  ProducerFactory                                        │
 *  │      ↓ crée des KafkaProducer avec la config ci-dessous │
 *  │  Kafka Broker (Redpanda local / MSK AWS)                │
 *  └─────────────────────────────────────────────────────────┘
 *
 *  PARAMÈTRES PRODUCTEUR :
 *
 *  bootstrap.servers : adresse du broker
 *  → Local  : localhost:19092 (Redpanda, voir docker-compose.yml)
 *  → AWS    : MSK brokers endpoints (depuis Parameter Store)
 *
 *  acks = "all" :
 *  → Le broker confirme la réception par TOUS les réplicas avant d'acquitter
 *  → Garantit qu'aucun message n'est perdu même si un broker crash
 *  → Trade-off latence vs durabilité (acceptable pour des domain events)
 *
 *  retries = 3 :
 *  → Réessayer 3 fois en cas d'erreur réseau transitoire
 *  → Combiné avec acks=all : résilience maximale
 *
 *  enable.idempotence = true :
 *  → Évite les doublons en cas de retry (le producteur déduplique)
 *  → Exige acks=all et retries > 0
 *
 *  SÉCURITÉ EN PRODUCTION (MSK avec IAM) :
 *  → Configurer SASL_SSL + AWS_MSK_IAM dans le profil "aws"
 *  → La config de sécurité est dans application-aws.yml (spring.kafka.properties)
 * ══════════════════════════════════════════════════════════════
 */
@Configuration
public class KafkaConfig {

    /**
     * Adresse du broker Kafka.
     * Injecté depuis application.yml (spring.kafka.bootstrap-servers).
     * Local  : localhost:19092 (Redpanda docker-compose)
     * AWS    : ${KAFKA_BOOTSTRAP_SERVERS} (depuis Parameter Store)
     */
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Factory de producteurs Kafka — crée et pool les KafkaProducer.
     * Chaque Producer est thread-safe → partagé entre tous les threads Virtual.
     */
    @Bean
    public ProducerFactory<String, Object> kafkaProducerFactory() {
        Map<String, Object> config = new HashMap<>();

        // ── Connexion ─────────────────────────────────────────────────
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // ── Sérialiseurs ──────────────────────────────────────────────
        // Clé : String (taskId ou eventId en texte)
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // Valeur : JSON via Jackson (DomainEvent → JSON)
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // ── Fiabilité ─────────────────────────────────────────────────
        // acks=all : confirmation par tous les réplicas (durabilité maximale)
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        // Retry en cas d'erreur réseau transitoire
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        // Idempotence : éviter les doublons lors des retries
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        // ── Performance ────────────────────────────────────────────────
        // Attendre 5ms pour agréger des messages (batch) — bon compromis latence/débit
        config.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        // Taille max d'un batch : 32KB
        config.put(ProducerConfig.BATCH_SIZE_CONFIG, 32 * 1024);

        // Configurer le JsonSerializer avec notre ObjectMapper (Java Time Module)
        DefaultKafkaProducerFactory<String, Object> factory =
                new DefaultKafkaProducerFactory<>(config);
        factory.setValueSerializer(new JsonSerializer<>(kafkaObjectMapper()));
        return factory;
    }

    /**
     * KafkaTemplate : API de haut niveau pour envoyer des messages.
     * Wraps le ProducerFactory et expose send(), sendDefault(), etc.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(
            ProducerFactory<String, Object> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * ObjectMapper dédié à Kafka — séparé de l'ObjectMapper HTTP.
     *
     * DIFFÉRENCE AVEC LE MAPPER HTTP :
     * → Pas de type inclus (@class) dans les messages Kafka
     * → Les consumers Kafka connaissent le type attendu (DomainEvent)
     * → Le JSON est plus propre (pas de @class pollué)
     */
    private ObjectMapper kafkaObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Ignorer les propriétés inconnues côté consumer (tolérance aux évolutions)
        mapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            false
        );
        return mapper;
    }
}
