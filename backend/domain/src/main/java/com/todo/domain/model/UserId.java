package com.todo.domain.model;

import java.util.UUID;

/**
 * ══════════════════════════════════════════════════════════════
 *  VALUE OBJECT — UserId
 * ══════════════════════════════════════════════════════════════
 *
 *  Identifiant d'un utilisateur. Correspond au claim "sub"
 *  du JWT Keycloak/Cognito (UUID v4).
 *
 *  Même structure que TaskId — c'est voulu : chaque type d'entité
 *  a son propre type d'ID. Cela rend le code auto-documenté :
 *
 *    void validateTask(TaskId taskId, UserId actor)  ← clair
 *    void validateTask(UUID taskId, UUID actor)       ← ambigu
 * ══════════════════════════════════════════════════════════════
 */
public record UserId(UUID value) {

    public UserId {
        if (value == null) {
            throw new IllegalArgumentException("UserId ne peut pas être null");
        }
    }

    public static UserId generate() {
        return new UserId(UUID.randomUUID());
    }

    public static UserId of(String uuid) {
        return new UserId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
