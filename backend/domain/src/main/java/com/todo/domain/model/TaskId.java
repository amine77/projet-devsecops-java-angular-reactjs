package com.todo.domain.model;

import java.util.UUID;

/**
 * ══════════════════════════════════════════════════════════════
 *  VALUE OBJECT — TaskId
 * ══════════════════════════════════════════════════════════════
 *
 *  Un Value Object est défini par sa VALEUR, pas son identité.
 *  Deux TaskId avec le même UUID sont strictement équivalents.
 *
 *  Pourquoi un type dédié plutôt qu'un UUID brut ?
 *  → Typage fort : le compilateur interdit de passer un UserId
 *    là où un TaskId est attendu. Sans ça, on peut mélanger
 *    les IDs sans que le compilateur s'en aperçoive.
 *
 *  Pourquoi un Record Java 21 ?
 *  → Immutable par nature (tous les champs sont final)
 *  → equals(), hashCode(), toString() générés automatiquement
 *  → Zéro boilerplate — la déclaration est la documentation
 * ══════════════════════════════════════════════════════════════
 */
public record TaskId(UUID value) {

    /**
     * Compact Constructor — validation à la construction.
     *
     * En DDD, les Value Objects doivent être valides dès leur création.
     * On ne permet pas d'instancier un TaskId avec une valeur nulle.
     * Cette validation est garantie à 100% par le type lui-même.
     */
    public TaskId {
        if (value == null) {
            throw new IllegalArgumentException("TaskId ne peut pas être null");
        }
    }

    /**
     * Factory method sémantique pour la CRÉATION d'une nouvelle tâche.
     *
     * On préfère TaskId.generate() à new TaskId(UUID.randomUUID())
     * car le nom exprime l'intention métier : "générer un nouvel ID".
     */
    public static TaskId generate() {
        return new TaskId(UUID.randomUUID());
    }

    /**
     * Factory method pour la RECONSTRUCTION depuis une source externe
     * (base de données, API REST, message Kafka).
     *
     * UUID.fromString() lance IllegalArgumentException si le format est invalide —
     * c'est intentionnel : un ID malformé est une erreur de programmation.
     */
    public static TaskId of(String uuid) {
        return new TaskId(UUID.fromString(uuid));
    }

    /** Représentation lisible — utile dans les logs et les messages d'erreur. */
    @Override
    public String toString() {
        return value.toString();
    }
}
