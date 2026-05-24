package com.todo.infrastructure.web;

import com.todo.domain.model.Priority;
import com.todo.domain.model.Task;
import com.todo.domain.model.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * ══════════════════════════════════════════════════════════════
 *  DTO HTTP SORTANT — TaskResponse
 * ══════════════════════════════════════════════════════════════
 *
 *  RÔLE : Représentation JSON d'une tâche renvoyée au client.
 *
 *  POURQUOI PAS SÉRIALISER Task DIRECTEMENT ?
 *  → Task est un agrégat domaine : exposer tous ses champs directement
 *    couple l'API HTTP à la structure interne du domaine.
 *  → Si on ajoute un champ interne à Task, il apparaît dans l'API.
 *  → Si l'API doit changer (renommer un champ), on modifie le domaine.
 *  → Solution : TaskResponse = "contrat public" de l'API
 *
 *  DONNÉES EXPOSÉES :
 *  → On expose les UUID (pas les Value Objects internes)
 *  → On calcule isOverdue() et l'affiche (pratique pour le frontend)
 *  → On expose les données d'audit (rejectedBy, validatedBy) pour l'UI
 *
 *  JACKSON SÉRIALISATION :
 *  → Record Java 21 → Jackson sérialise automatiquement tous les components
 *  → Les enums (Priority, TaskStatus) sont sérialisés comme String
 *  → LocalDate → "2026-06-30" (via JavaTimeModule configuré dans application.yml)
 *  → Instant → "2026-05-24T10:30:00Z" (ISO 8601 UTC)
 *
 *  FACTORY METHOD fromDomain() :
 *  → Conversion domaine → DTO dans une méthode statique
 *  → Pas de logique de conversion dans le contrôleur
 * ══════════════════════════════════════════════════════════════
 */
public record TaskResponse(

        UUID id,
        String title,
        String description,
        TaskStatus status,
        Priority priority,
        LocalDate dueDate,
        boolean overdue,        // calculé depuis task.isOverdue()

        UUID ownerId,
        UUID teamId,

        // ── Données de rejet (null si non rejetée) ──────────────────
        String rejectionReason,
        UUID rejectedBy,
        Instant rejectedAt,

        // ── Données de validation (null si non validée) ──────────────
        UUID validatedBy,
        Instant validatedAt,

        // ── Données de clôture (null si non terminée) ────────────────
        UUID doneBy,
        Instant doneAt,

        // ── Audit ─────────────────────────────────────────────────────
        Instant createdAt,
        Instant updatedAt,
        long version            // pour l'optimistic locking côté client

) {
    /**
     * Crée un TaskResponse à partir d'un agrégat Task.
     *
     * CONVENTIONS :
     * → Les Value Objects (UserId, TeamId) sont convertis en UUID bruts
     * → isOverdue() est pré-calculé pour éviter la logique côté client
     *
     * @param task l'agrégat domaine chargé depuis la DB ou le cache
     * @return DTO JSON-serializable
     */
    public static TaskResponse fromDomain(Task task) {
        return new TaskResponse(
                task.id().value(),
                task.title(),
                task.description(),
                task.status(),
                task.priority(),
                task.dueDate(),
                task.isOverdue(),           // logique métier calculée dans le domaine
                task.ownerId().value(),
                task.teamId().value(),
                task.rejectionReason(),
                task.rejectedBy()  != null ? task.rejectedBy().value()  : null,
                task.rejectedAt(),
                task.validatedBy() != null ? task.validatedBy().value() : null,
                task.validatedAt(),
                task.doneBy()      != null ? task.doneBy().value()      : null,
                task.doneAt(),
                task.createdAt(),
                task.updatedAt(),
                task.version()
        );
    }
}
