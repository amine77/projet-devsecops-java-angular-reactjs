package com.todo.infrastructure.scheduling;

import com.todo.domain.model.Task;
import com.todo.domain.model.User;
import com.todo.domain.port.out.NotificationPort;
import com.todo.domain.port.out.TaskRepository;
import com.todo.domain.port.out.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ══════════════════════════════════════════════════════════════
 *  SCHEDULER — OverdueTaskScheduler
 * ══════════════════════════════════════════════════════════════
 *
 *  Envoie des rappels automatiques aux Managers pour les tâches en retard.
 *  Activé par @EnableScheduling dans TodoApplication.
 *
 *  QUAND S'EXÉCUTE-T-IL ?
 *  → Tous les jours à 8h00 (heure du serveur, UTC en production)
 *  → Expression CRON : "0 0 8 * * MON-FRI"
 *    → 0 secondes, 0 minutes, 8 heures, tous les jours, tous les mois,
 *       du lundi au vendredi uniquement
 *
 *  CRON EXPRESSION FORMAT (Spring) :
 *  "s m h dom month dow"
 *   ↑ ↑ ↑  ↑     ↑     ↑
 *   | | |  |     |     day of week (0=Sunday, MON-FRI)
 *   | | |  |     month (1-12 ou *)
 *   | | |  day of month (1-31 ou *)
 *   | | hour (0-23)
 *   | minute (0-59)
 *   second (0-59)
 *
 *  FLOW D'EXÉCUTION :
 *  1. Charger toutes les tâches en retard (TaskRepository.findOverdue())
 *  2. Grouper par teamId
 *  3. Pour chaque équipe → charger son Manager (UserRepository)
 *  4. Envoyer le rappel (NotificationPort.notifyOverdueTasksReminder)
 *
 *  CONCERNANT LE MANAGER :
 *  → On cherche le Manager par teamId dans la base
 *  → Si aucun Manager n'est trouvé pour l'équipe → log + continuer
 *  → En production : chaque équipe DOIT avoir exactement un Manager
 *    (garantie par le processus RH/IT, pas par l'application)
 *
 *  ALTERNATIVE POUR LA PRODUCTION :
 *  → EventBridge Scheduler (AWS) : déclencher une Lambda à 8h
 *    → Plus robuste que @Scheduled (pas dépendant de l'uptime du service)
 *    → Peut être configuré en Terraform (Phase 5)
 *    → @Scheduled suffisant pour Phase 1-3
 *
 *  VIRTUAL THREADS :
 *  → @Scheduled avec Spring Boot 3.2+ utilise automatiquement Virtual Threads
 *    si spring.threads.virtual.enabled=true
 *  → Le scheduler ne bloque pas un thread OS pendant l'exécution
 * ══════════════════════════════════════════════════════════════
 */
@Component
public class OverdueTaskScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueTaskScheduler.class);

    private final TaskRepository     taskRepository;
    private final UserRepository     userRepository;
    private final NotificationPort   notificationPort;

    public OverdueTaskScheduler(
            TaskRepository taskRepository,
            UserRepository userRepository,
            NotificationPort notificationPort
    ) {
        this.taskRepository   = taskRepository;
        this.userRepository   = userRepository;
        this.notificationPort = notificationPort;
    }

    /**
     * Rappel quotidien des tâches en retard — lundi à vendredi à 8h00.
     *
     * fixedDelay vs cron :
     * → fixedDelay : X ms APRÈS la fin de l'exécution précédente
     * → cron : à heure fixe (calendrier) — plus adapté aux rappels métier
     */
    @Scheduled(cron = "${scheduler.overdue-tasks.cron:0 0 8 * * MON-FRI}")
    public void sendOverdueTasksReminders() {
        log.info("Démarrage du scheduler OverdueTaskScheduler");

        try {
            // 1. Charger toutes les tâches en retard (tous statuts non-DONE, dueDate passée)
            List<Task> overdueTasks = taskRepository.findOverdue();

            if (overdueTasks.isEmpty()) {
                log.info("Aucune tâche en retard — rien à notifier");
                return;
            }

            log.info("{} tâches en retard trouvées", overdueTasks.size());

            // 2. Grouper par teamId pour envoyer UN SEUL email par Manager
            //    (pas un email par tâche — éviter le spam)
            Map<String, List<Task>> tasksByTeam = overdueTasks.stream()
                    .collect(Collectors.groupingBy(task -> task.teamId().value().toString()));

            // 3. Pour chaque équipe, trouver le Manager et envoyer le rappel
            tasksByTeam.forEach((teamIdStr, tasks) -> {
                try {
                    notifyManagerForTeam(teamIdStr, tasks);
                } catch (Exception e) {
                    // Erreur sur une équipe ne bloque pas les autres
                    log.error("Erreur notification équipe {} : {}", teamIdStr, e.getMessage());
                }
            });

            log.info("Scheduler OverdueTaskScheduler terminé — {} équipes notifiées",
                    tasksByTeam.size());

        } catch (Exception e) {
            log.error("Erreur critique dans OverdueTaskScheduler", e);
            // En production : alerter via PagerDuty / CloudWatch Alarm
        }
    }

    /**
     * Notifie le Manager d'une équipe pour ses tâches en retard.
     *
     * @param teamIdStr UUID de l'équipe en String
     * @param tasks     liste des tâches en retard de cette équipe
     */
    private void notifyManagerForTeam(String teamIdStr, List<Task> tasks) {
        // Chercher le Manager de cette équipe
        // On cherche dans la base un utilisateur avec role=MANAGER et teamId=X
        // NOTE : UserRepository.findManagerByTeamId() serait idéal — à ajouter
        // Pour l'instant, on utilise ownerId de la première tâche pour trouver l'équipe
        // et on logue un avertissement (implémentation partielle)

        log.info("Équipe {} : {} tâches en retard — notification Manager", teamIdStr, tasks.size());

        // TODO Phase 4 : ajouter UserRepository.findByTeamIdAndRole(teamId, MANAGER)
        // et appeler notificationPort.notifyOverdueTasksReminder(tasks, manager)

        // Pour l'instant, on logue seulement (comportement NoOp en local de toute façon)
        tasks.forEach(task ->
            log.warn("  RETARD - Tâche: '{}' | Échéance: {} | Priorité: {}",
                task.title(), task.dueDate(), task.priority())
        );
    }
}
