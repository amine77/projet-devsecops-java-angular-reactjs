package com.todo.infrastructure.notification;

import com.todo.domain.model.Task;
import com.todo.domain.model.User;
import com.todo.domain.port.out.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;


/**
 * ══════════════════════════════════════════════════════════════
 *  ADAPTATEUR (NO-OP) — NoOpNotificationAdapter
 * ══════════════════════════════════════════════════════════════
 *
 *  Implémentation "no-operation" de NotificationPort pour le profil LOCAL.
 *
 *  POURQUOI CE PATTERN ?
 *  → En développement local, on n'a pas accès à AWS SES
 *  → On ne veut pas envoyer de vrais emails pendant les tests
 *  → Mais le domaine DOIT appeler notifyTaskValidated() etc.
 *    (le domaine ne sait pas si on est en local ou en prod)
 *
 *  SOLUTION : Null Object Pattern
 *  → NoOpNotificationAdapter ne fait RIEN mais implémente l'interface
 *  → Il logue ce qui AURAIT été envoyé → visible dans les logs
 *  → Le domaine fonctionne normalement sans avoir conscience du mode
 *
 *  @Profile("!aws") :
 *  → Actif sur TOUS les profils SAUF "aws"
 *  → "local", "test", "default" → NoOpNotificationAdapter
 *  → "aws" → SesNotificationAdapter
 *
 *  CONFLIT DE BEANS :
 *  → SesNotificationAdapter est annoté @Profile("aws")
 *  → NoOpNotificationAdapter est annoté @Profile("!aws")
 *  → Les deux n'existent JAMAIS simultanément → pas de conflit
 *  → Spring ne trouvera toujours qu'UNE SEULE implémentation de NotificationPort
 *
 *  ÉVOLUTION POSSIBLE :
 *  → Phase future : envoyer les emails en local via Mailhog (serveur SMTP local)
 *    → Mailhog capture tous les emails sans les envoyer vraiment
 *    → Interface web : http://localhost:8025
 * ══════════════════════════════════════════════════════════════
 */
@Component
@Profile("!aws") // Actif en local, test, et tout profil sauf "aws"
public class NoOpNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpNotificationAdapter.class);

    @Override
    public void notifyTaskValidated(Task task, User owner) {
        // Simuler l'envoi : logger ce qui aurait été fait
        log.info("[NO-OP EMAIL] Tâche validée → destinataire: {} | tâche: '{}'",
                owner.email(), task.title());
    }

    @Override
    public void notifyTaskRejected(Task task, User owner) {
        log.info("[NO-OP EMAIL] Tâche rejetée → destinataire: {} | tâche: '{}' | motif: '{}'",
                owner.email(), task.title(), task.rejectionReason());
    }

    @Override
    public void notifyTaskDone(Task task, User owner) {
        log.info("[NO-OP EMAIL] Tâche terminée → destinataire: {} | tâche: '{}'",
                owner.email(), task.title());
    }

    @Override
    public void notifyNewTaskForManager(Task task, User manager) {
        log.info("[NO-OP EMAIL] Nouvelle tâche pour le manager → destinataire: {} | tâche: '{}'",
                manager.email(), task.title());
    }

    @Override
    public void notifyOverdueTasksReminder(User manager, int overdueCount) {
        // Signature alignée avec NotificationPort : (User manager, int overdueCount)
        log.info("[NO-OP EMAIL] Rappel tâches en retard → destinataire: {} | {} tâches en retard",
                manager.email(), overdueCount);
    }
}
