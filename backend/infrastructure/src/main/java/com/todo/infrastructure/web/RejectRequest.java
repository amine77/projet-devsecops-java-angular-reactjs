package com.todo.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO pour le body de la requête de rejet.
 * Séparé de TaskRequest car seul le motif est nécessaire.
 *
 * EXEMPLE JSON :
 * { "rejectionReason": "Budget insuffisant pour cette tâche" }
 */
public record RejectRequest(

        @NotBlank(message = "Le motif de rejet est obligatoire (RG-06)")
        @Size(max = 500, message = "Le motif ne peut pas dépasser 500 caractères")
        String rejectionReason

) {}
