package com.todo.domain.model;

import java.util.UUID;

/**
 * VALUE OBJECT — UnitId
 *
 * Identifiant d'une unité de gestion. Une unité regroupe
 * plusieurs équipes (ex: "Direction Commerciale" contient
 * "Équipe Alpha", "Équipe Beta"...).
 *
 * Utilisé pour les rapports globaux du Super-Administrateur
 * et l'administration organisationnelle.
 */
public record UnitId(UUID value) {

    public UnitId {
        if (value == null) {
            throw new IllegalArgumentException("UnitId ne peut pas être null");
        }
    }

    public static UnitId generate() {
        return new UnitId(UUID.randomUUID());
    }

    public static UnitId of(String uuid) {
        return new UnitId(UUID.fromString(uuid));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
