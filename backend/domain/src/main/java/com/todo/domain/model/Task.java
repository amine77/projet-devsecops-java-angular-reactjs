package com.todo.domain.model;

import com.todo.domain.exception.InvalidTaskStatusException;
import com.todo.domain.exception.RejectionReasonRequiredException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * ══════════════════════════════════════════════════════════════
 *  AGGREGATE ROOT — Task (Acte de gestion / To-Do)
 * ══════════════════════════════════════════════════════════════
 *
 *  En DDD, un Aggregate Root est l'entrée unique pour modifier
 *  un groupe d'objets liés. Toute modification de Task passe
 *  obligatoirement par ses méthodes — jamais par accès direct
 *  aux champs (ils sont de toute façon final dans un Record).
 *
 *  Pourquoi un Record pour une entité (et pas juste un VO) ?
 *  → Les Records sont immutables. Chaque transition d'état
 *    retourne une NOUVELLE instance de Task. C'est le pattern
 *    "Immutable Domain Object" : on ne mute pas l'état,
 *    on crée un nouvel état. Avantages :
 *    - Thread-safe par nature (Virtual Threads Java 21)
 *    - Pas d'effets de bord surprenants
 *    - L'historique des états est traçable
 *
 *  Important : Task ne connaît pas le repository, Kafka, S3, etc.
 *  Elle contient UNIQUEMENT la logique métier pure.
 *  La persistance est gérée par TaskJpaRepository (infrastructure).
 * ══════════════════════════════════════════════════════════════
 */
public record Task(

        /** Identifiant unique de la tâche — généré à la création. */
        TaskId id,

        /** Titre court et obligatoire (max 200 caractères, validé dans le compact constructor). */
        String title,

        /** Description longue — optionnelle. */
        String description,

        /** Statut courant — gouverne les transitions autorisées. */
        TaskStatus status,

        /** Niveau d'urgence — NORMALE par défaut. */
        Priority priority,

        /** Date limite — optionnelle. Si dépassée sans DONE → tâche "en retard". */
        LocalDate dueDate,

        /** ID du Gestionnaire propriétaire (RG-03 : il ne voit que ses tâches). */
        UserId ownerId,

        /** Équipe à laquelle appartient la tâche (RG-04 : Manager voit son équipe). */
        TeamId teamId,

        /**
         * Motif de rejet — null sauf si status == REJETEE.
         * Invariant enforced : si REJETEE alors rejectionReason != null (RG-06).
         */
        String rejectionReason,

        /** ID du Manager qui a rejeté la tâche (null si jamais rejetée). */
        UserId rejectedBy,

        /** Horodatage du rejet (null si jamais rejetée). */
        Instant rejectedAt,

        /** ID du Manager qui a validé la tâche (null si jamais validée). */
        UserId validatedBy,

        /** Horodatage de la validation (null si jamais validée). */
        Instant validatedAt,

        /** ID du Manager qui a placé en Done (null si pas encore Done). */
        UserId doneBy,

        /** Horodatage du passage en Done (null si pas encore Done). */
        Instant doneAt,

        /** Horodatage de création — ne change jamais. */
        Instant createdAt,

        /** Horodatage de la dernière modification — mis à jour à chaque transition. */
        Instant updatedAt,

        /**
         * Version pour l'optimistic locking JPA.
         *
         * Problème sans optimistic locking :
         *   1. Manager A lit la tâche (version 1)
         *   2. Manager B lit la tâche (version 1)
         *   3. Manager A valide → version 2 sauvegardée
         *   4. Manager B rejette → écrase la version 2 avec une version 1 modifiée !
         *
         * Avec @Version JPA + version incrémentée ici, l'étape 4 lance
         * une OptimisticLockException → l'appelant est informé du conflit.
         */
        long version

) {

    // ══════════════════════════════════════════════════════════
    //  COMPACT CONSTRUCTOR — Invariants du domaine
    // ══════════════════════════════════════════════════════════

    /**
     * Le compact constructor valide les invariants à CHAQUE instanciation.
     * Impossible de créer une Task invalide — le domaine est auto-protégé.
     */
    public Task {
        Objects.requireNonNull(id,       "id est obligatoire");
        Objects.requireNonNull(title,    "title est obligatoire");
        Objects.requireNonNull(status,   "status est obligatoire");
        Objects.requireNonNull(priority, "priority est obligatoire");
        Objects.requireNonNull(ownerId,  "ownerId est obligatoire");
        Objects.requireNonNull(teamId,   "teamId est obligatoire");
        Objects.requireNonNull(createdAt,"createdAt est obligatoire");
        Objects.requireNonNull(updatedAt,"updatedAt est obligatoire");

        if (title.isBlank()) {
            throw new IllegalArgumentException("Le titre ne peut pas être vide");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("Le titre ne peut pas dépasser 200 caractères");
        }

        // RG-06 : le motif de rejet est obligatoire si le statut est REJETEE
        if (status == TaskStatus.REJETEE &&
                (rejectionReason == null || rejectionReason.isBlank())) {
            throw new IllegalArgumentException(
                "Motif de rejet obligatoire pour le statut REJETEE (RG-06)"
            );
        }
    }

    // ══════════════════════════════════════════════════════════
    //  FACTORY METHOD — Création
    // ══════════════════════════════════════════════════════════

    /**
     * Crée une nouvelle tâche au statut A_FAIRE.
     *
     * Factory method plutôt que constructeur public direct :
     * - Nom expressif (create vs new Task(...))
     * - Centralise les valeurs par défaut (id, status, timestamps)
     * - Le reste du code ne peut pas "oublier" d'initialiser un champ
     *
     * @param title       titre court (obligatoire, max 200 chars)
     * @param description texte long (optionnel)
     * @param priority    niveau d'urgence
     * @param dueDate     date limite (optionnelle)
     * @param ownerId     ID du Gestionnaire créateur
     * @param teamId      ID de l'équipe
     * @return une nouvelle Task au statut A_FAIRE
     */
    public static Task create(
            String title,
            String description,
            Priority priority,
            LocalDate dueDate,
            UserId ownerId,
            TeamId teamId
    ) {
        var now = Instant.now();
        return new Task(
                TaskId.generate(),  // ID généré ici — pas besoin que l'appelant le connaisse
                title,
                description,
                TaskStatus.A_FAIRE, // Statut initial toujours A_FAIRE (RG-01 implicite)
                priority != null ? priority : Priority.NORMALE, // Valeur par défaut
                dueDate,
                ownerId,
                teamId,
                null,   // rejectionReason — null à la création
                null,   // rejectedBy
                null,   // rejectedAt
                null,   // validatedBy
                null,   // validatedAt
                null,   // doneBy
                null,   // doneAt
                now,    // createdAt
                now,    // updatedAt
                0L      // version initiale
        );
    }

    // ══════════════════════════════════════════════════════════
    //  TRANSITIONS D'ÉTAT — Chaque méthode retourne une nouvelle Task
    // ══════════════════════════════════════════════════════════

    /**
     * Transition A_FAIRE → VALIDEE (Manager/Super-Admin).
     *
     * Pattern "Tell, Don't Ask" : on dit à la tâche de se valider,
     * pas on lui demande son statut pour décider en dehors.
     *
     * @param validatedBy ID du Manager qui valide
     * @return nouvelle instance de Task au statut VALIDEE
     * @throws InvalidTaskStatusException si la tâche n'est pas en A_FAIRE
     */
    public Task validate(UserId validatedBy) {
        Objects.requireNonNull(validatedBy, "validatedBy est obligatoire");

        if (!this.status.canTransitionTo(TaskStatus.VALIDEE)) {
            throw new InvalidTaskStatusException(this.id, this.status, "valider");
        }

        var now = Instant.now();
        return new Task(
                id, title, description,
                TaskStatus.VALIDEE, priority, dueDate,
                ownerId, teamId,
                null, null, null,           // rejet effacé si re-validation après REJETEE
                validatedBy, now,            // validatedBy et validatedAt mis à jour
                null, null,                  // doneBy/doneAt pas encore
                createdAt, now,              // updatedAt = maintenant
                version + 1                  // optimistic lock incrémenté
        );
    }

    /**
     * Transition A_FAIRE → REJETEE (Manager/Super-Admin).
     *
     * RG-06 : le motif est OBLIGATOIRE. La task elle-même enforce
     * cette règle — pas besoin de vérification en dehors.
     *
     * @param reason    motif obligatoire (non null, non vide)
     * @param rejectedBy ID du Manager qui rejette
     * @return nouvelle instance de Task au statut REJETEE
     * @throws RejectionReasonRequiredException si le motif est absent
     * @throws InvalidTaskStatusException       si la transition est invalide
     */
    public Task reject(String reason, UserId rejectedBy) {
        Objects.requireNonNull(rejectedBy, "rejectedBy est obligatoire");

        // RG-06 : vérification explicite avant de lever l'exception domaine dédiée
        if (reason == null || reason.isBlank()) {
            throw new RejectionReasonRequiredException(this.id);
        }

        if (!this.status.canTransitionTo(TaskStatus.REJETEE)) {
            throw new InvalidTaskStatusException(this.id, this.status, "rejeter");
        }

        var now = Instant.now();
        return new Task(
                id, title, description,
                TaskStatus.REJETEE, priority, dueDate,
                ownerId, teamId,
                reason, rejectedBy, now,    // rejet documenté
                null, null,                  // validation effacée
                null, null,                  // pas encore done
                createdAt, now,
                version + 1
        );
    }

    /**
     * Transition A_FAIRE ou VALIDEE → DONE (Manager/Super-Admin).
     *
     * @param doneBy ID du Manager qui clôture
     * @return nouvelle instance de Task au statut DONE (terminal)
     * @throws InvalidTaskStatusException si la transition est invalide
     */
    public Task markAsDone(UserId doneBy) {
        Objects.requireNonNull(doneBy, "doneBy est obligatoire");

        if (!this.status.canTransitionTo(TaskStatus.DONE)) {
            throw new InvalidTaskStatusException(this.id, this.status, "marquer Done");
        }

        var now = Instant.now();
        return new Task(
                id, title, description,
                TaskStatus.DONE, priority, dueDate,
                ownerId, teamId,
                rejectionReason, rejectedBy, rejectedAt,  // historique conservé
                validatedBy, validatedAt,                  // historique conservé
                doneBy, now,                               // done documenté
                createdAt, now,
                version + 1
        );
    }

    /**
     * Modification du contenu (Gestionnaire — uniquement en A_FAIRE ou REJETEE).
     *
     * RG-07 : Si la tâche est REJETEE, la modification la repasse
     * automatiquement en A_FAIRE.
     *
     * @param title       nouveau titre
     * @param description nouvelle description
     * @param priority    nouvelle priorité
     * @param dueDate     nouvelle date limite
     * @return nouvelle instance de Task, potentiellement en A_FAIRE si elle était REJETEE
     * @throws InvalidTaskStatusException si la tâche n'est pas modifiable
     */
    public Task update(String title, String description, Priority priority, LocalDate dueDate) {
        if (!this.status.isEditableByGestionnaire()) {
            throw new InvalidTaskStatusException(this.id, this.status, "modifier");
        }

        // RG-07 : une tâche REJETEE remodifiée repasse en A_FAIRE
        var newStatus = (this.status == TaskStatus.REJETEE)
                ? TaskStatus.A_FAIRE
                : this.status;

        // Si on repasse en A_FAIRE, on efface le motif de rejet (plus pertinent)
        var newRejectionReason = (newStatus == TaskStatus.A_FAIRE) ? null : this.rejectionReason;

        return new Task(
                id,
                title != null ? title : this.title,
                description,
                newStatus,
                priority != null ? priority : this.priority,
                dueDate,
                ownerId, teamId,
                newRejectionReason,
                (newStatus == TaskStatus.A_FAIRE) ? null : rejectedBy,  // effacé si retour A_FAIRE
                (newStatus == TaskStatus.A_FAIRE) ? null : rejectedAt,
                validatedBy, validatedAt,
                doneBy, doneAt,
                createdAt, Instant.now(),
                version + 1
        );
    }

    // ══════════════════════════════════════════════════════════
    //  REQUÊTES (Query methods — ne modifient pas l'état)
    // ══════════════════════════════════════════════════════════

    /** @return true si la tâche a dépassé sa date limite sans être DONE */
    public boolean isOverdue() {
        return dueDate != null
                && !status.isTerminal()
                && LocalDate.now().isAfter(dueDate);
    }

    /** @return true si cette tâche appartient à l'équipe donnée */
    public boolean belongsToTeam(TeamId targetTeamId) {
        return this.teamId.equals(targetTeamId);
    }

    /** @return true si cet utilisateur est le propriétaire de la tâche */
    public boolean isOwnedBy(UserId userId) {
        return this.ownerId.equals(userId);
    }
}
