package com.todo.domain.exception;

/**
 * ══════════════════════════════════════════════════════════════
 *  Exception racine du domaine
 * ══════════════════════════════════════════════════════════════
 *
 *  Toutes les exceptions métier héritent de cette classe.
 *  Cela permet à la couche infrastructure de les attraper
 *  globalement et de les traduire en réponses HTTP appropriées :
 *
 *    DomainException          → 400 / 422 (logique métier)
 *    TaskNotFoundException    → 404
 *    UnauthorizedAction...    → 403
 *
 *  Pourquoi RuntimeException et pas Exception ?
 *  → Les exceptions non-vérifiées (unchecked) évitent la pollution
 *    des signatures de méthodes avec "throws". En DDD moderne,
 *    les violations de règles métier sont des erreurs de programmation
 *    (le code appelant ne devrait pas appeler une méthode invalide).
 * ══════════════════════════════════════════════════════════════
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
