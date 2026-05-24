package com.todo.domain.model;

import java.util.UUID;

/**
 * VALUE OBJECT — TeamId
 *
 * Identifiant d'une équipe. Utilisé pour vérifier que
 * le Manager agit bien sur les tâches de SON équipe (RG-04).
 *
 * Exemple de vérification dans le domaine :
 *   if (!task.teamId().equals(actor.teamId())) {
 *       throw new UnauthorizedActionException(...);
 *   }
 */
public record TeamId(UUID value) {

    public TeamId {
        if (value == null) {
            throw new IllegalArgumentException("TeamId ne peut pas être null");
        }
    }

    public static TeamId generate() {
        return new TeamId(UUID.randomUUID());
    }

    public static TeamId of(String uuid) {
        return new TeamId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
