package com.todo.application.handler;

import com.todo.application.command.CreateTaskCommand;
import com.todo.domain.model.TaskId;
import com.todo.domain.port.in.TaskUseCase;
import jakarta.validation.Valid;

/**
 * ══════════════════════════════════════════════════════════════
 *  APPLICATION HANDLER — CreateTaskHandler
 * ══════════════════════════════════════════════════════════════
 *
 *  RÔLE DU HANDLER dans l'architecture hexagonale :
 *  C'est la "façade d'application" — il:
 *  1. Reçoit une Command (objet structuré)
 *  2. Valide la Command (Bean Validation via @Valid)
 *  3. Appelle le port entrant (TaskUseCase)
 *  4. Retourne un résultat simple (pas d'objet domaine exposé)
 *
 *  POURQUOI NE PAS APPELER DIRECTEMENT TaskUseCase DEPUIS LE CONTRÔLEUR ?
 *  → Le Handler est un point d'entrée testable et isolé pour CHAQUE cas d'usage
 *  → Il pourrait ajouter de la logique transversale : tracing, retry, saga
 *  → La couche infrastructure (REST controller) ne sait pas comment mapper
 *    un TaskId en réponse — le Handler abstrait ça
 *
 *  POURQUOI PAS D'ANNOTATION SPRING ICI ?
 *  → Ce fichier est dans le module `application` qui ne dépend PAS de Spring.
 *  → Le Bean Spring (@Service, @Component) sera déclaré dans la couche
 *    infrastructure (ApplicationConfig.java ou DomainConfig.java).
 *  → Avantage : ce Handler est testable avec `new CreateTaskHandler(mockUseCase)`
 *    sans démarrer de contexte Spring.
 *
 *  INJECTION PAR CONSTRUCTEUR :
 *  → La seule façon valide en architecture hexagonale.
 *  → Le framework (Spring) peut injecter via le constructeur sans annotation
 *    si c'est le seul constructeur (Spring Boot 3 le fait automatiquement).
 * ══════════════════════════════════════════════════════════════
 */
public class CreateTaskHandler {

    /**
     * Port entrant — interface définie dans le domaine, implémentée par TaskDomainService.
     * Le Handler ne connaît que l'interface, jamais l'implémentation concrète.
     * C'est l'Inversion of Control (IoC) sans framework.
     */
    private final TaskUseCase taskUseCase;

    public CreateTaskHandler(TaskUseCase taskUseCase) {
        this.taskUseCase = taskUseCase;
    }

    /**
     * Traite la commande de création de tâche.
     *
     * @param command objet validé contenant tous les paramètres nécessaires
     * @return TaskId de la tâche créée (pour retourner un 201 Created avec Location header)
     *
     * ANNOTATION @Valid :
     * → Déclenche la validation Bean Validation (Jakarta) sur tous les champs
     *   annotés dans CreateTaskCommand (@NotBlank, @NotNull, @Size...)
     * → Si une contrainte est violée → MethodArgumentNotValidException
     *   → Spring MVC la transforme en HTTP 400 avec le détail des erreurs
     */
    public TaskId handle(@Valid CreateTaskCommand command) {
        return taskUseCase.createTask(
                command.title(),
                command.description(),
                command.priority(),
                command.dueDate(),
                command.actorId()
        );
    }
}
