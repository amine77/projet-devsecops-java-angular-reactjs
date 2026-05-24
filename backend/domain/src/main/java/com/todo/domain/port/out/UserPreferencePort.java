package com.todo.domain.port.out;

import com.todo.domain.model.UserId;

import java.util.Map;
import java.util.Optional;

/**
 * PORT SORTANT — UserPreferencePort
 *
 * Préférences utilisateur : thème, tri par défaut, filtres sauvegardés.
 *
 * Implémentation :
 * → DynamoUserPreferenceAdapter : DynamoDB (always free, lookup par userId)
 *
 * Pourquoi DynamoDB et pas PostgreSQL pour les préférences ?
 * → Les préférences sont accédées par clé primaire userId, sans joins.
 * → Schéma flexible (chaque user peut avoir des prefs différentes).
 * → Always free jusqu'à 25 GB — jamais besoin de terraform destroy.
 * → Un ORM sur DynamoDB serait un anti-pattern.
 *
 * Structure DynamoDB :
 *   Table "user-preferences"
 *   PK: userId (String)
 *   Attributes: { theme: "dark", taskSort: "PRIORITY_DESC", ... }
 */
public interface UserPreferencePort {

    /**
     * Sauvegarde les préférences d'un utilisateur (upsert).
     *
     * @param userId      ID de l'utilisateur
     * @param preferences map clé/valeur des préférences
     */
    void save(UserId userId, Map<String, String> preferences);

    /**
     * Charge les préférences d'un utilisateur.
     *
     * @return Optional.empty() si aucune préférence enregistrée (utilisateur nouveau)
     */
    Optional<Map<String, String>> findByUserId(UserId userId);

    /**
     * Récupère une préférence spécifique avec une valeur par défaut.
     *
     * @param userId       ID de l'utilisateur
     * @param key          nom de la préférence (ex: "theme")
     * @param defaultValue valeur si absent (ex: "light")
     * @return la valeur de la préférence ou la valeur par défaut
     */
    String getPreference(UserId userId, String key, String defaultValue);

    /** Supprime toutes les préférences d'un utilisateur (désactivation compte). */
    void deleteByUserId(UserId userId);
}
