package com.todo.domain.model;

/**
 * ENUM — Priority (niveau d'urgence d'une tâche)
 *
 * Utilisé pour le tri dans le tableau de bord Manager
 * (file d'action triée par priorité puis par date de création).
 *
 * Les libellés et couleurs sont configurables par le Super-Admin
 * (stockés dans DynamoDB via UserPreferencePort).
 */
public enum Priority {

    /** Peut attendre — traité en dernier dans la file d'action. */
    BASSE,

    /** Priorité standard — valeur par défaut à la création. */
    NORMALE,

    /** À traiter rapidement — remonte dans la file d'action. */
    HAUTE,

    /** Bloquant — traitement immédiat requis. */
    URGENTE;

    /**
     * Ordre naturel utile pour le tri : URGENTE > HAUTE > NORMALE > BASSE.
     * L'ordinal() Java donne l'ordre inverse → on inverse.
     *
     * Exemple d'utilisation dans un Comparator :
     *   tasks.sort(Comparator.comparing(Task::priority, Priority.descendingOrder()));
     */
    public static java.util.Comparator<Priority> descendingOrder() {
        return (a, b) -> b.ordinal() - a.ordinal();
    }
}
