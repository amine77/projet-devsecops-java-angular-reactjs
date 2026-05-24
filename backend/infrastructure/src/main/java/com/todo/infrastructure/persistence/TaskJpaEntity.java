package com.todo.infrastructure.persistence;

import com.todo.domain.model.Priority;
import com.todo.domain.model.Task;
import com.todo.domain.model.TaskId;
import com.todo.domain.model.TaskStatus;
import com.todo.domain.model.TeamId;
import com.todo.domain.model.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * ══════════════════════════════════════════════════════════════
 *  JPA ENTITY — TaskJpaEntity
 * ══════════════════════════════════════════════════════════════
 *
 *  RÔLE : Représentation de la tâche au niveau PERSISTANCE (PostgreSQL).
 *
 *  SÉPARATION DOMAINE / PERSISTANCE :
 *  → Task.java (domaine)          = agrégat immuable, logique métier, Records Java 21
 *  → TaskJpaEntity.java (infra)   = entité mutable, annotations JPA, champs bruts
 *
 *  POURQUOI DEUX REPRÉSENTATIONS ?
 *  → Le domaine ne doit pas avoir d'annotations JPA (@Entity, @Column)
 *  → Les annotations JPA sont une dépendance technique, pas métier
 *  → Si on change d'ORM (JPA → jOOQ), seul cet adaptateur change
 *  → L'agrégat Task reste intact
 *
 *  MAPPING BIDIRECTIONNEL :
 *  → toDomain()  : TaskJpaEntity → Task (pour lire depuis la DB)
 *  → fromDomain(): Task → TaskJpaEntity (pour écrire en DB)
 *
 *  OPTIMISTIC LOCKING (@Version) :
 *  → JPA incrémente la colonne 'version' à chaque UPDATE
 *  → Si deux transactions essaient de modifier la même tâche simultanément :
 *    - Transaction 1 lit version=5, modifie, sauvegarde version=6 ✓
 *    - Transaction 2 lit version=5 aussi, essaie de sauvegarder avec version=5
 *      → JPA détecte le conflit → OptimisticLockException
 *    - Évite les "lost updates" sans lock pessimiste (meilleure perf)
 *
 *  ENUMS STOCKÉS EN STRING (@Enumerated(EnumType.STRING)) :
 *  → Stocke "A_FAIRE" plutôt que 0
 *  → Survit aux refactorings de l'enum (ajout/réordonnancement de valeurs)
 *  → Compatible avec la CHECK constraint SQL de la migration Flyway
 *
 *  NOTE SUR LES CHAMPS NULLABLE :
 *  → rejectionReason, rejectedBy, rejectedAt : null sauf si REJETEE
 *  → validatedBy, validatedAt : null sauf si VALIDEE ou DONE
 *  → doneBy, doneAt : null sauf si DONE
 *  → La CHECK constraint SQL (V1__init_schema.sql) enforce la cohérence
 * ══════════════════════════════════════════════════════════════
 */
@Entity
@Table(name = "tasks", schema = "public")
public class TaskJpaEntity {

    // ── Identifiant ──────────────────────────────────────────────
    /**
     * UUID généré par le domaine (factory TaskId.generate()).
     * On n'utilise PAS @GeneratedValue — l'ID est généré DANS le domaine,
     * pas par la DB. Avantage : l'ID est connu AVANT la persistence.
     */
    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    // ── Données métier ───────────────────────────────────────────
    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /**
     * Statut stocké comme String pour la lisibilité et la résistance aux refactorings.
     * La colonne SQL a une CHECK constraint : status IN ('A_FAIRE', 'VALIDEE', 'REJETEE', 'DONE')
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Column(name = "due_date")
    private LocalDate dueDate;

    // ── Références (UUID bruts — pas de JOIN/FK JPA) ────────────
    /**
     * On stocke les UUID bruts plutôt que des relations JPA (@ManyToOne).
     * POURQUOI ?
     * → Architecture hexagonale : les relations entre agrégats se font
     *   par ID, pas par référence directe (DDD : "reference by id")
     * → Évite le problème N+1 et les @Lazy / @Eager
     * → L'agrégat User est chargé séparément via UserRepository si besoin
     */
    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "team_id", nullable = false, columnDefinition = "uuid")
    private UUID teamId;

    // ── Champs de rejet (nullables) ──────────────────────────────
    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    @Column(name = "rejected_by", columnDefinition = "uuid")
    private UUID rejectedBy;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    // ── Champs de validation (nullables) ─────────────────────────
    @Column(name = "validated_by", columnDefinition = "uuid")
    private UUID validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    // ── Champs de clôture (nullables) ────────────────────────────
    @Column(name = "done_by", columnDefinition = "uuid")
    private UUID doneBy;

    @Column(name = "done_at")
    private Instant doneAt;

    // ── Audit ─────────────────────────────────────────────────────
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Version pour l'optimistic locking JPA.
     * JPA gère automatiquement l'incrémentation et la vérification.
     * Correspond à la colonne 'version' dans la migration SQL.
     */
    @Version
    private long version;

    // ── Constructeur no-arg requis par JPA ───────────────────────
    /** JPA exige un constructeur sans argument. Protected pour décourager l'usage direct. */
    protected TaskJpaEntity() {}

    // ══════════════════════════════════════════════════════════
    //  MAPPING BIDIRECTIONNEL
    // ══════════════════════════════════════════════════════════

    /**
     * Convertit l'entité JPA en agrégat domaine.
     *
     * SENS : base de données → domaine
     * APPELÉ PAR : TaskRepositoryAdapter.findById(), findByOwner(), etc.
     *
     * PRINCIPE : le domaine reçoit des Value Objects typés (TaskId, UserId...),
     * jamais des UUID bruts. C'est la responsabilité de l'adaptateur de faire
     * cette conversion.
     */
    public Task toDomain() {
        return new Task(
                new TaskId(id),
                title,
                description,
                status,
                priority,
                dueDate,
                new UserId(ownerId),
                new TeamId(teamId),
                rejectionReason,
                rejectedBy  != null ? new UserId(rejectedBy)  : null,
                rejectedAt,
                validatedBy != null ? new UserId(validatedBy) : null,
                validatedAt,
                doneBy      != null ? new UserId(doneBy)      : null,
                doneAt,
                createdAt,
                updatedAt,
                version
        );
    }

    /**
     * Crée (ou met à jour) une TaskJpaEntity à partir d'un agrégat domaine.
     *
     * SENS : domaine → base de données
     * APPELÉ PAR : TaskRepositoryAdapter.save()
     *
     * STATIC FACTORY METHOD : On ne fait pas new TaskJpaEntity() puis setters
     * pour éviter un objet dans un état partiellement initialisé.
     */
    public static TaskJpaEntity fromDomain(Task task) {
        TaskJpaEntity entity = new TaskJpaEntity();
        entity.id              = task.id().value();
        entity.title           = task.title();
        entity.description     = task.description();
        entity.status          = task.status();
        entity.priority        = task.priority();
        entity.dueDate         = task.dueDate();
        entity.ownerId         = task.ownerId().value();
        entity.teamId          = task.teamId().value();
        entity.rejectionReason = task.rejectionReason();
        entity.rejectedBy      = task.rejectedBy()  != null ? task.rejectedBy().value()  : null;
        entity.rejectedAt      = task.rejectedAt();
        entity.validatedBy     = task.validatedBy() != null ? task.validatedBy().value() : null;
        entity.validatedAt     = task.validatedAt();
        entity.doneBy          = task.doneBy()      != null ? task.doneBy().value()      : null;
        entity.doneAt          = task.doneAt();
        entity.createdAt       = task.createdAt();
        entity.updatedAt       = task.updatedAt();
        entity.version         = task.version();
        return entity;
    }
}
