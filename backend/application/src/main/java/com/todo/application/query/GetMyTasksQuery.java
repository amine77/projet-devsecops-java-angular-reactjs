package com.todo.application.query;

import com.todo.domain.model.UserId;
import jakarta.validation.constraints.NotNull;

/**
 * ══════════════════════════════════════════════════════════════
 *  QUERY — GetMyTasksQuery
 * ══════════════════════════════════════════════════════════════
 *
 *  Une Query est l'équivalent d'une Command mais pour la LECTURE.
 *  Elle ne modifie JAMAIS l'état — elle interroge uniquement.
 *
 *  CQRS (Command Query Responsibility Segregation) :
 *  → Les Commands écrivent et retournent void (ou juste un ID)
 *  → Les Queries lisent et retournent des données
 *  → Avantage : on peut scaler la lecture et l'écriture indépendamment
 *  → Avantage : les Queries peuvent lire depuis un Read Model optimisé
 *    (ex: une vue PostgreSQL dénormalisée, ou un index Elasticsearch)
 *
 *  ICI :
 *  → Cette Query utilise le Cache-Aside Pattern via CachePort
 *  → Si les tâches sont en cache Redis → retour immédiat (< 1ms)
 *  → Sinon → lecture PostgreSQL + mise en cache
 * ══════════════════════════════════════════════════════════════
 */
public record GetMyTasksQuery(
        @NotNull UserId actorId
) {}
