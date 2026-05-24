package com.todo.infrastructure.web;

import com.todo.domain.model.Priority;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * ══════════════════════════════════════════════════════════════
 *  DTO HTTP ENTRANT — TaskRequest
 * ══════════════════════════════════════════════════════════════
 *
 *  RÔLE : Représente le body JSON envoyé par le client (Angular/React/Postman).
 *
 *  POURQUOI UN DTO SÉPARÉ DE LA COMMAND ?
 *  → Le DTO HTTP (TaskRequest) est la représentation JSON externe
 *  → La Command est la représentation interne de l'application
 *  → Le contrôleur fait la conversion : TaskRequest → CreateTaskCommand
 *
 *  SÉPARATION DES COUCHES :
 *  ┌─────────────────────────────────────────────────────────┐
 *  │  JSON (HTTP)     : TaskRequest (ce fichier)             │
 *  │  Application     : CreateTaskCommand, UpdateTaskCommand  │
 *  │  Domaine         : Task (agrégat)                        │
 *  │  Persistence     : TaskJpaEntity                         │
 *  └─────────────────────────────────────────────────────────┘
 *
 *  AVANTAGES DE CETTE SÉPARATION :
 *  → L'API HTTP peut évoluer sans impacter le domaine
 *  → On peut versionner l'API (v1/TaskRequest vs v2/TaskRequestV2)
 *  → La validation HTTP (@NotBlank) est séparée de la validation métier
 *
 *  EXEMPLE JSON :
 *  {
 *    "title": "Préparer le rapport mensuel",
 *    "description": "Rapport d'activité Q1",
 *    "priority": "HAUTE",
 *    "dueDate": "2026-06-30"
 *  }
 *
 *  NOTE : actorId n'est PAS dans le JSON — il est extrait du JWT.
 *  C'est une règle de sécurité fondamentale : le client ne peut pas
 *  se faire passer pour quelqu'un d'autre en mettant un actorId arbitraire.
 * ══════════════════════════════════════════════════════════════
 */
public record TaskRequest(

        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200, message = "Le titre ne peut pas dépasser 200 caractères")
        String title,

        @Size(max = 2000, message = "La description ne peut pas dépasser 2000 caractères")
        String description,

        @NotNull(message = "La priorité est obligatoire")
        Priority priority,

        @FutureOrPresent(message = "La date d'échéance doit être dans le futur ou aujourd'hui")
        LocalDate dueDate

) {}
