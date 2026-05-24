package com.todo.infrastructure.preference;

import com.todo.domain.model.UserId;
import com.todo.domain.port.out.UserPreferencePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ══════════════════════════════════════════════════════════════
 *  ADAPTATEUR IN-MEMORY — InMemoryUserPreferenceAdapter
 * ══════════════════════════════════════════════════════════════
 *
 *  Implémentation en mémoire de UserPreferencePort pour le profil LOCAL.
 *
 *  Equivalent au NoOpNotificationAdapter : permet au code de fonctionner
 *  localement sans DynamoDB.
 *
 *  THREAD-SAFETY :
 *  → ConcurrentHashMap : thread-safe pour les accès concurrents
 *  → Avec Virtual Threads (Java 21), des dizaines de milliers de threads
 *    peuvent accéder simultanément → ConcurrentHashMap essentiel
 *  → HashMap classique provoquerait des race conditions
 *
 *  DURÉE DE VIE :
 *  → Les données existent seulement pendant la vie du processus JVM
 *  → Redémarrer l'application = perte des préférences locales
 *  → Acceptable en développement (les préférences de dev sont triviales)
 *
 *  UTILISATION EN TEST :
 *  → Les tests d'intégration (@Profile("test")) utilisent aussi cet adaptateur
 *  → Pas besoin de DynamoDB dans Testcontainers pour les prefs
 * ══════════════════════════════════════════════════════════════
 */
@Component
@Profile("!aws")
public class InMemoryUserPreferenceAdapter implements UserPreferencePort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryUserPreferenceAdapter.class);

    /**
     * Stockage en mémoire : userId → { clé → valeur }
     * ConcurrentHashMap pour la thread-safety avec Virtual Threads
     */
    private final ConcurrentHashMap<String, Map<String, String>> store = new ConcurrentHashMap<>();

    @Override
    public void save(UserId userId, Map<String, String> preferences) {
        // putIfAbsent + compute pour la thread-safety
        String key = userId.value().toString();
        store.compute(key, (k, existing) -> {
            Map<String, String> updated = existing != null ? new HashMap<>(existing) : new HashMap<>();
            updated.putAll(preferences); // merge (upsert)
            return updated;
        });
        log.debug("[IN-MEMORY PREFS] Sauvegardées pour userId={}: {}", userId.value(), preferences.keySet());
    }

    @Override
    public Optional<Map<String, String>> findByUserId(UserId userId) {
        Map<String, String> prefs = store.get(userId.value().toString());
        return Optional.ofNullable(prefs)
                .map(HashMap::new); // retourner une copie défensive (immutabilité)
    }

    @Override
    public String getPreference(UserId userId, String key, String defaultValue) {
        Map<String, String> prefs = store.get(userId.value().toString());
        if (prefs == null) return defaultValue;
        return prefs.getOrDefault(key, defaultValue);
    }

    @Override
    public void deleteByUserId(UserId userId) {
        store.remove(userId.value().toString());
        log.debug("[IN-MEMORY PREFS] Supprimées pour userId={}", userId.value());
    }
}
