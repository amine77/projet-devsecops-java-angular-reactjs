package com.todo.domain.port.out;

import com.todo.domain.model.Task;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.TeamId;
import com.todo.domain.model.UserId;

import java.util.List;
import java.util.Optional;

/**
 * PORT SORTANT — CachePort
 *
 * Abstraction du cache pour les requêtes fréquentes.
 *
 * Implémentations :
 * → RedisTaskCacheAdapter : cache Redis (profil local + aws partiel)
 * → NoOpCacheAdapter      : ne fait rien (profil test)
 *
 * Stratégie de cache :
 * - Cache-Aside : le service lit le cache, en cas de miss lit la BDD,
 *   puis met en cache le résultat.
 * - TTL : 5 minutes (configurable dans application.yml)
 * - Invalidation : à chaque save/delete, on évince l'entrée du cache.
 *
 * Quoi cacher ?
 * - findByOwner() : appelée à chaque affichage du tableau de bord Gestionnaire
 * - findByTeam()  : appelée à chaque affichage du tableau de bord Manager
 * - findById()    : pour les lookups fréquents dans les use cases
 */
public interface CachePort {

    /**
     * Met une tâche en cache par son ID.
     * La clé Redis sera "task:{taskId}".
     */
    void putById(TaskId taskId, Task task);

    /** Récupère une tâche depuis le cache (Optional.empty() si absent ou expiré). */
    Optional<Task> getById(TaskId taskId);

    /** Met en cache la liste des tâches d'un Gestionnaire. */
    void putByOwner(UserId ownerId, List<Task> tasks);

    /** Récupère la liste des tâches d'un Gestionnaire depuis le cache. */
    Optional<List<Task>> getByOwner(UserId ownerId);

    /** Met en cache les tâches d'une équipe. */
    void putByTeam(TeamId teamId, List<Task> tasks);

    Optional<List<Task>> getByTeam(TeamId teamId);

    /**
     * Invalide toutes les entrées de cache liées à une tâche.
     * Appelé après chaque modification (save/delete).
     *
     * @param taskId  pour invalider le cache getById()
     * @param ownerId pour invalider le cache getByOwner()
     * @param teamId  pour invalider le cache getByTeam()
     */
    void evict(TaskId taskId, UserId ownerId, TeamId teamId);
}
