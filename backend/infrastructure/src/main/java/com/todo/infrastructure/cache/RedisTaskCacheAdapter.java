package com.todo.infrastructure.cache;

import com.todo.domain.model.Task;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.TeamId;
import com.todo.domain.model.UserId;
import com.todo.domain.port.out.CachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * ══════════════════════════════════════════════════════════════
 *  ADAPTATEUR — RedisTaskCacheAdapter
 * ══════════════════════════════════════════════════════════════
 *
 *  Implémente CachePort (port domaine) via Redis.
 *
 *  PATTERN CACHE-ASIDE (aussi appelé Lazy Loading) :
 *  ┌─────────────────────────────────────────────────────┐
 *  │  REQUÊTE                                            │
 *  │  1. Vérifier Redis → cache hit ? → retourner       │
 *  │  2. Cache miss → charger depuis PostgreSQL          │
 *  │  3. Stocker dans Redis avec TTL                     │
 *  │  4. Retourner le résultat                           │
 *  │                                                     │
 *  │  MODIFICATION                                       │
 *  │  1. Modifier en base (via TaskRepository)           │
 *  │  2. Invalider les clés Redis concernées             │
 *  │     (pas de mise à jour → suppression)              │
 *  └─────────────────────────────────────────────────────┘
 *
 *  POURQUOI INVALIDER PLUTÔT QUE METTRE À JOUR ?
 *  → Mise à jour = risque d'incohérence si l'écriture Redis échoue
 *    après l'écriture DB (problème de double écriture)
 *  → Invalidation = le cache se repeuple à la prochaine lecture
 *    → cohérence éventuelle mais correcte
 *  → C'est le pattern "write-around" cache
 *
 *  STRATÉGIE DE CLÉS REDIS :
 *  → task:{id}            = une tâche par ID (TTL 5min)
 *  → tasks:owner:{userId} = liste des tâches d'un utilisateur (TTL 5min)
 *  → tasks:team:{teamId}  = liste des tâches d'une équipe (TTL 5min)
 *
 *  SÉRIALISATION :
 *  → RedisTemplate est configuré avec JsonSerializer dans infrastructure/config
 *  → Les objets sont sérialisés en JSON avec le type Java pour la désérialisation
 *  → Avantage sur Serializable : lisible, versionnable, compatible multi-langages
 *
 *  RÉSILIENCE :
 *  → Si Redis est indisponible → toutes les méthodes retournent Optional.empty()
 *    ou ne font rien (catch silencieux + log)
 *  → L'application continue à fonctionner sans cache (dégradé mais opérationnel)
 *  → C'est le pattern "Cache as Optional Optimization"
 * ══════════════════════════════════════════════════════════════
 */
@Component
public class RedisTaskCacheAdapter implements CachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisTaskCacheAdapter.class);

    /** TTL des entrées cache — synchronisé avec spring.cache.redis.time-to-live dans application.yml */
    private static final Duration TTL = Duration.ofMinutes(5);

    /** Préfixes de clés pour éviter les collisions dans Redis */
    private static final String KEY_TASK    = "task:";
    private static final String KEY_OWNER   = "tasks:owner:";
    private static final String KEY_TEAM    = "tasks:team:";

    /**
     * RedisTemplate est auto-configuré par Spring Boot Redis Starter.
     * La configuration JSON est dans infrastructure/config/RedisConfig.java (Phase 3 suite).
     * String (clé) → Object (valeur, sérialisé en JSON)
     */
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisTaskCacheAdapter(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ══════════════════════════════════════════════════════════
    //  LECTURE — get par ID
    // ══════════════════════════════════════════════════════════

    @Override
    @SuppressWarnings("unchecked")
    public Optional<Task> getById(TaskId taskId) {
        try {
            Object cached = redisTemplate.opsForValue().get(KEY_TASK + taskId.value());
            return Optional.ofNullable((Task) cached);
        } catch (Exception e) {
            // Redis indisponible → on laisse passer, la DB prendra le relai
            log.warn("Cache Redis indisponible pour getById({}): {}", taskId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void putById(TaskId taskId, Task task) {
        try {
            redisTemplate.opsForValue().set(KEY_TASK + taskId.value(), task, TTL);
        } catch (Exception e) {
            log.warn("Impossible de mettre en cache la tâche {}: {}", taskId, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  LECTURE — get par propriétaire
    // ══════════════════════════════════════════════════════════

    @Override
    @SuppressWarnings("unchecked")
    public Optional<List<Task>> getByOwner(UserId ownerId) {
        try {
            Object cached = redisTemplate.opsForValue().get(KEY_OWNER + ownerId.value());
            return Optional.ofNullable((List<Task>) cached);
        } catch (Exception e) {
            log.warn("Cache miss owner {}: {}", ownerId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void putByOwner(UserId ownerId, List<Task> tasks) {
        try {
            redisTemplate.opsForValue().set(KEY_OWNER + ownerId.value(), tasks, TTL);
        } catch (Exception e) {
            log.warn("Impossible de mettre en cache les tâches de l'owner {}: {}", ownerId, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  LECTURE — get par équipe
    // ══════════════════════════════════════════════════════════

    @Override
    @SuppressWarnings("unchecked")
    public Optional<List<Task>> getByTeam(TeamId teamId) {
        try {
            Object cached = redisTemplate.opsForValue().get(KEY_TEAM + teamId.value());
            return Optional.ofNullable((List<Task>) cached);
        } catch (Exception e) {
            log.warn("Cache miss team {}: {}", teamId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void putByTeam(TeamId teamId, List<Task> tasks) {
        try {
            redisTemplate.opsForValue().set(KEY_TEAM + teamId.value(), tasks, TTL);
        } catch (Exception e) {
            log.warn("Impossible de mettre en cache les tâches de l'équipe {}: {}", teamId, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  INVALIDATION — supprimer les clés après une modification
    // ══════════════════════════════════════════════════════════

    /**
     * Invalide toutes les clés Redis liées à une tâche modifiée.
     *
     * QUAND APPELÉ :
     * → Après chaque createTask, updateTask, deleteTask, validateTask,
     *   rejectTask, markAsDone dans TaskDomainService
     *
     * POURQUOI INVALIDER PLUSIEURS CLÉS ?
     * → La tâche apparaît dans 3 caches différents :
     *   - task:{id}                = détail de cette tâche
     *   - tasks:owner:{ownerId}    = liste des tâches du propriétaire
     *   - tasks:team:{teamId}      = liste des tâches de l'équipe
     * → Si on n'invalide pas toutes les clés, on peut lire des données périmées
     *
     * ATOMICITÉ :
     * → delete() prend une Collection<String> → Redis MULTI-DEL
     * → Toutes les suppressions en un seul round-trip réseau
     */
    @Override
    public void evict(TaskId taskId, UserId ownerId, TeamId teamId) {
        try {
            redisTemplate.delete(List.of(
                KEY_TASK  + taskId.value(),
                KEY_OWNER + ownerId.value(),
                KEY_TEAM  + teamId.value()
            ));
            log.debug("Cache invalidé pour tâche={}, owner={}, team={}",
                    taskId.value(), ownerId.value(), teamId.value());
        } catch (Exception e) {
            // L'invalidation échoue → la prochaine lecture verra des données périmées
            // jusqu'à expiration du TTL (5 min max). Acceptable.
            log.warn("Échec invalidation cache: {}", e.getMessage());
        }
    }
}
