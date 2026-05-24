package com.todo.application.command;

import com.todo.domain.model.Priority;
import com.todo.domain.model.UserId;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * ══════════════════════════════════════════════════════════════
 *  COMMAND — CreateTaskCommand
 * ══════════════════════════════════════════════════════════════
 *
 *  Une Command est un objet immuable qui représente UNE intention
 *  de modifier l'état du système. C'est le "DTO d'entrée" du domaine.
 *
 *  POURQUOI UN RECORD JAVA 21 ?
 *  → Immuable par défaut (tous les champs final)
 *  → equals/hashCode/toString générés automatiquement
 *  → Syntaxe compacte, lisible, pas de boilerplate Lombok
 *
 *  POURQUOI DES ANNOTATIONS JAKARTA VALIDATION ICI ET PAS DANS LE DOMAINE ?
 *  → La validation "format" (titre non vide, max 200 chars) appartient
 *    à la couche application/infrastructure (Bean Validation).
 *  → La validation "règle métier" (titre non null dans l'aggregat) est
 *    dans le compact constructor de Task.java.
 *  → Deux niveaux de défense : validation HTTP avant d'atteindre le domaine.
 *
 *  POURQUOI PAS JUSTE DES PARAMÈTRES DE MÉTHODE ?
 *  → Les Commands sont des objets de valeur nommés.
 *  → On peut les logger, les auditer, les rejouer (Event Sourcing futur).
 *  → La signature du Handler reste stable si on ajoute un champ à la Command.
 *
 *  CYCLE DE VIE :
 *  HTTP POST /tasks → TaskController désérialise le JSON en CreateTaskRequest
 *                   → construit CreateTaskCommand avec l'actorId du JWT
 *                   → passe au CreateTaskHandler
 *                   → CreateTaskHandler appelle taskUseCase.createTask()
 * ══════════════════════════════════════════════════════════════
 */
public record CreateTaskCommand(

        /**
         * Titre de la tâche.
         * Validé par Bean Validation avant d'atteindre le domaine.
         * Le domaine vérifie aussi le null dans Task.compact constructor.
         */
        @NotBlank(message = "Le titre est obligatoire")
        @Size(max = 200, message = "Le titre ne peut pas dépasser 200 caractères")
        String title,

        /**
         * Description optionnelle — peut être null.
         * Max 2000 chars pour éviter les abus.
         */
        @Size(max = 2000, message = "La description ne peut pas dépasser 2000 caractères")
        String description,

        /**
         * Priorité obligatoire. Le contrôleur valide que la valeur JSON
         * correspond à l'enum Priority (BASSE, NORMALE, HAUTE, URGENTE).
         */
        @NotNull(message = "La priorité est obligatoire")
        Priority priority,

        /**
         * Date d'échéance optionnelle.
         * Si fournie, doit être aujourd'hui ou dans le futur.
         * La vérification isOverdue() sera faite par la logique métier.
         */
        @FutureOrPresent(message = "La date d'échéance doit être dans le futur ou aujourd'hui")
        LocalDate dueDate,

        /**
         * ID de l'utilisateur qui effectue l'action.
         * JAMAIS fourni par le client HTTP — extrait du JWT par le contrôleur.
         * C'est la garantie que l'acteur est authentifié et identifié.
         *
         * Pattern sécurité : le contrôleur lit le sub du JWT Keycloak/Cognito,
         * charge (ou crée) l'utilisateur en base, et injecte son UserId ici.
         */
        @NotNull
        UserId actorId

) {
    /**
     * Compact constructor — validation d'invariants supplémentaires
     * si nécessaire. Pour l'instant, la validation Jakarta suffit.
     *
     * NOTE : Dans un Record, le "compact constructor" permet de valider
     * sans répéter les assignments (ils sont faits automatiquement après).
     */
    public CreateTaskCommand {
        // Les annotations @NotNull/@NotBlank sont vérifiées par Bean Validation
        // avant d'atteindre ce constructeur. Ici, on pourrait ajouter des
        // règles transversales si needed.
    }
}
