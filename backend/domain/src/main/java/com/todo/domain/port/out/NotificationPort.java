package com.todo.domain.port.out;

import com.todo.domain.model.Task;
import com.todo.domain.model.User;

/**
 * PORT SORTANT — NotificationPort
 *
 * Abstraction pour l'envoi de notifications (email, in-app).
 *
 * Implémentations :
 * → SesNotificationAdapter    : envoie des emails via AWS SES (profil aws)
 * → LogNotificationAdapter    : log les notifications en console (profil local)
 *
 * Le domaine ne connaît ni SES, ni SMTP, ni aucun provider d'email.
 * Il dit juste "notifie que cette tâche a été validée" — l'infrastructure
 * décide du canal et du format.
 */
public interface NotificationPort {

    /**
     * Notifie le propriétaire qu'une tâche a été validée.
     *
     * @param task  la tâche validée (pour le titre, l'ID)
     * @param owner le Gestionnaire propriétaire (pour l'email de destination)
     */
    void notifyTaskValidated(Task task, User owner);

    /**
     * Notifie le propriétaire d'un rejet avec le motif.
     *
     * @param task  la tâche rejetée (contient rejectionReason)
     * @param owner le Gestionnaire propriétaire
     */
    void notifyTaskRejected(Task task, User owner);

    /**
     * Notifie le propriétaire que sa tâche est Done.
     *
     * @param task  la tâche clôturée
     * @param owner le Gestionnaire propriétaire
     */
    void notifyTaskDone(Task task, User owner);

    /**
     * Notifie le Manager qu'une nouvelle tâche lui est soumise.
     * (in-app uniquement — pas d'email pour ne pas spammer)
     *
     * @param task    la tâche créée
     * @param manager le Manager de l'équipe
     */
    void notifyNewTaskForManager(Task task, User manager);

    /**
     * Rappel au Manager : des tâches sont en retard dans son équipe.
     *
     * @param manager      le Manager concerné
     * @param overdueCount nombre de tâches en retard
     */
    void notifyOverdueTasksReminder(User manager, int overdueCount);
}
