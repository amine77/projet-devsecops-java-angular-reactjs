package com.todo.domain.exception;

import com.todo.domain.model.UserId;

/**
 * Levée quand un utilisateur est introuvable.
 * Traduite en HTTP 404 par la couche infrastructure.
 */
public class UserNotFoundException extends DomainException {

    private final UserId userId;

    public UserNotFoundException(UserId userId) {
        super("Utilisateur introuvable : " + userId);
        this.userId = userId;
    }

    public UserId userId() {
        return userId;
    }
}
