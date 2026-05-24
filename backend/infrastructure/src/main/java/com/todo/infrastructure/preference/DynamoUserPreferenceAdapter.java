package com.todo.infrastructure.preference;

import com.todo.domain.model.UserId;
import com.todo.domain.port.out.UserPreferencePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ══════════════════════════════════════════════════════════════
 *  ADAPTATEUR DYNAMODB — DynamoUserPreferenceAdapter
 * ══════════════════════════════════════════════════════════════
 *
 *  Implémente UserPreferencePort via AWS DynamoDB.
 *
 *  POURQUOI DYNAMODB POUR LES PRÉFÉRENCES ?
 *  → Accès par clé primaire (userId) uniquement → DynamoDB est optimal
 *  → Schéma flexible : chaque user peut avoir des prefs différentes
 *  → Always Free tier : 25 Go, 25 WCU/RCU → jamais de coût pour ce cas d'usage
 *  → Un ORM (JPA) sur DynamoDB serait un anti-pattern (modèle différent du SQL)
 *
 *  STRUCTURE DE LA TABLE DYNAMODB :
 *  ┌─────────────────────────────────────────────────────────────┐
 *  │  Table: user-preferences                                    │
 *  │  Partition Key (PK): userId (String)                        │
 *  │                                                             │
 *  │  Exemple d'item :                                           │
 *  │  {                                                          │
 *  │    "userId": "550e8400-e29b-41d4-a716-446655440000",        │
 *  │    "theme": "dark",                                         │
 *  │    "taskSort": "PRIORITY_DESC",                             │
 *  │    "language": "fr",                                        │
 *  │    "notifications": "true"                                  │
 *  │  }                                                          │
 *  └─────────────────────────────────────────────────────────────┘
 *
 *  SDK v2 DynamoDB vs JPA :
 *  → Pas de requêtes SQL : opérations GetItem, PutItem, DeleteItem par clé
 *  → Pas de schéma fixe : on stocke Map<String, String> librement
 *  → Pas de migrations (contrairement à Flyway pour PostgreSQL)
 *  → Les attributs sont des AttributeValue (wrapper SDK) → conversion manuelle
 *
 *  CONSISTANCE :
 *  → Par défaut : lecture "éventuellement consistante" (latence < 1ms)
 *  → consistentRead=true : lecture "fortement consistante" (peut coûter 2x les RCU)
 *  → Pour les préférences UI, la consistance éventuelle suffit largement
 * ══════════════════════════════════════════════════════════════
 */
@Component
@Profile("aws")
public class DynamoUserPreferenceAdapter implements UserPreferencePort {

    private static final Logger log = LoggerFactory.getLogger(DynamoUserPreferenceAdapter.class);

    /** Nom de la colonne PK dans DynamoDB */
    private static final String PK_NAME = "userId";

    @Value("${aws.dynamodb.preferences-table:user-preferences}")
    private String tableName;

    /** Client DynamoDB SDK v2 — injecté depuis AwsConfig */
    private final DynamoDbClient dynamoDbClient;

    public DynamoUserPreferenceAdapter(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public void save(UserId userId, Map<String, String> preferences) {
        // Construire l'item DynamoDB : PK + tous les attributs de préférence
        Map<String, AttributeValue> item = new HashMap<>();
        item.put(PK_NAME, AttributeValue.fromS(userId.value().toString()));

        // Convertir Map<String, String> → Map<String, AttributeValue>
        preferences.forEach((key, value) ->
            item.put(key, AttributeValue.fromS(value))
        );

        // PutItem = upsert (insert ou mise à jour) — pas de SELECT avant INSERT
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());

        log.debug("Préférences sauvegardées pour userId={}", userId.value());
    }

    @Override
    public Optional<Map<String, String>> findByUserId(UserId userId) {
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(PK_NAME, AttributeValue.fromS(userId.value().toString())))
                // consistentRead=false : lecture éventuelle (suffisant pour les prefs UI)
                .consistentRead(false)
                .build());

        if (!response.hasItem() || response.item().isEmpty()) {
            return Optional.empty();
        }

        // Convertir Map<String, AttributeValue> → Map<String, String>
        // En excluant la clé primaire userId
        Map<String, String> preferences = response.item().entrySet().stream()
                .filter(entry -> !PK_NAME.equals(entry.getKey()))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().s() // DynamoDB String → Java String
                ));

        return Optional.of(preferences);
    }

    @Override
    public String getPreference(UserId userId, String key, String defaultValue) {
        // Optimisation : on ne charge pas TOUTES les prefs pour une seule valeur
        // → ProjectionExpression = récupérer seulement la colonne demandée
        GetItemResponse response = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(PK_NAME, AttributeValue.fromS(userId.value().toString())))
                .projectionExpression(key)  // lecture partielle → économise les RCU
                .consistentRead(false)
                .build());

        if (!response.hasItem() || !response.item().containsKey(key)) {
            return defaultValue;
        }

        return Optional.ofNullable(response.item().get(key))
                .map(AttributeValue::s)
                .orElse(defaultValue);
    }

    @Override
    public void deleteByUserId(UserId userId) {
        dynamoDbClient.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(PK_NAME, AttributeValue.fromS(userId.value().toString())))
                .build());
        log.info("Préférences supprimées pour userId={}", userId.value());
    }
}
