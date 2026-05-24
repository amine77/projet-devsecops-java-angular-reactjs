package com.todo.infrastructure.notification;

import com.todo.domain.model.Task;
import com.todo.domain.model.User;
import com.todo.domain.port.out.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

/**
 * ══════════════════════════════════════════════════════════════
 *  ADAPTATEUR — SesNotificationAdapter
 * ══════════════════════════════════════════════════════════════
 *
 *  Implémente NotificationPort (port domaine) via AWS SES.
 *
 *  AWS SES (Simple Email Service) :
 *  → Service d'envoi d'emails managé
 *  → Free Tier : 62 000 emails/mois si envoyé depuis EC2/Lambda
 *  → Prix hors free tier : 0,10$/1000 emails
 *  → Limites sandbox : doit vérifier les adresses de destination
 *    (pendant le développement — lever la limite en demandant "production access")
 *
 *  PRÉREQUIS AWS :
 *  1. Vérifier l'email expéditeur dans SES Console
 *  2. Le profil AWS actif doit avoir iam:ses:SendEmail
 *  3. La région SES doit être eu-west-3 (configurable)
 *
 *  EN MODE LOCAL (profil "local") :
 *  → SesClient n'est pas créé (SES n'existe pas en local)
 *  → Remplacer par NoOpNotificationAdapter (mock pour le dev)
 *  → La configuration des profils est dans DomainConfig ou AwsConfig
 *
 *  STRUCTURE D'UN EMAIL SES :
 *  → Destination : liste des destinataires
 *  → Message : Subject + Body (texte ou HTML)
 *  → Source : adresse expéditeur vérifiée dans SES
 *
 *  SÉCURITÉ :
 *  → L'adresse email de l'utilisateur est dans User.email()
 *  → Ne jamais logger les adresses email en production (RGPD)
 * ══════════════════════════════════════════════════════════════
 */
@Component
public class SesNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(SesNotificationAdapter.class);

    /**
     * Adresse expéditeur vérifiée dans AWS SES.
     * Configurable via application-aws.yml ou variable d'environnement.
     * En local, cette valeur est ignorée (NoOpNotificationAdapter actif).
     */
    @Value("${aws.ses.from-email:noreply@todo-enterprise.com}")
    private String fromEmail;

    /**
     * SesClient AWS SDK v2.
     * Auto-configuré par Spring Cloud AWS si profil "aws" actif.
     * Pour le profil "local", on injecte un mock/no-op.
     */
    private final SesClient sesClient;

    public SesNotificationAdapter(SesClient sesClient) {
        this.sesClient = sesClient;
    }

    // ══════════════════════════════════════════════════════════
    //  NOTIFICATIONS MÉTIER
    // ══════════════════════════════════════════════════════════

    @Override
    public void notifyTaskValidated(Task task, User owner) {
        String subject = String.format("✅ Tâche validée : %s", task.title());
        String body = String.format("""
            Bonjour %s,

            Votre tâche a été validée par le Manager.

            Tâche    : %s
            Statut   : VALIDÉE
            Priorité : %s

            Cordialement,
            L'équipe Todo Enterprise
            """,
            owner.firstName(),
            task.title(),
            task.priority()
        );
        sendEmail(owner.email(), subject, body);
    }

    @Override
    public void notifyTaskRejected(Task task, User owner) {
        String subject = String.format("❌ Tâche rejetée : %s", task.title());
        String body = String.format("""
            Bonjour %s,

            Votre tâche a été rejetée par le Manager.

            Tâche           : %s
            Motif de rejet  : %s

            Vous pouvez modifier la tâche pour qu'elle repasse automatiquement en A_FAIRE.

            Cordialement,
            L'équipe Todo Enterprise
            """,
            owner.firstName(),
            task.title(),
            task.rejectionReason()
        );
        sendEmail(owner.email(), subject, body);
    }

    @Override
    public void notifyTaskDone(Task task, User owner) {
        String subject = String.format("🏁 Tâche clôturée : %s", task.title());
        String body = String.format("""
            Bonjour %s,

            Votre tâche a été marquée comme terminée.

            Tâche    : %s
            Priorité : %s

            Cordialement,
            L'équipe Todo Enterprise
            """,
            owner.firstName(),
            task.title(),
            task.priority()
        );
        sendEmail(owner.email(), subject, body);
    }

    @Override
    public void notifyNewTaskForManager(Task task, User manager) {
        String subject = String.format("📋 Nouvelle tâche à traiter : %s", task.title());
        String body = String.format("""
            Bonjour %s,

            Une nouvelle tâche requiert votre attention.

            Tâche    : %s
            Priorité : %s
            Échéance : %s

            Connectez-vous pour valider ou rejeter cette tâche.

            Cordialement,
            L'équipe Todo Enterprise
            """,
            manager.firstName(),
            task.title(),
            task.priority(),
            task.dueDate() != null ? task.dueDate().toString() : "Non définie"
        );
        sendEmail(manager.email(), subject, body);
    }

    @Override
    public void notifyOverdueTasksReminder(java.util.List<Task> overdueTasks, User manager) {
        if (overdueTasks.isEmpty()) return;

        String subject = String.format("⚠️ %d tâche(s) en retard dans votre équipe", overdueTasks.size());
        StringBuilder body = new StringBuilder(String.format(
            "Bonjour %s,\n\nLes tâches suivantes sont en retard :\n\n",
            manager.firstName()
        ));
        overdueTasks.forEach(task ->
            body.append(String.format("• %s (Priorité : %s, Échéance : %s)\n",
                task.title(), task.priority(), task.dueDate()))
        );
        body.append("\nConnectez-vous pour prendre les décisions nécessaires.\n\nCordialement,\nL'équipe Todo Enterprise");

        sendEmail(manager.email(), subject, body.toString());
    }

    // ══════════════════════════════════════════════════════════
    //  HELPER PRIVÉ
    // ══════════════════════════════════════════════════════════

    /**
     * Envoie un email via AWS SES.
     *
     * GESTION D'ERREUR :
     * → SesException capturée et loguée → ne fait pas échouer la transaction principale
     * → L'échec d'une notification ne doit JAMAIS annuler une opération métier
     * → En production : ajouter une alerte CloudWatch sur les SesException
     *
     * @param toEmail   adresse du destinataire
     * @param subject   objet de l'email
     * @param plainText corps en texte plain (Phase future : HTML avec template Thymeleaf)
     */
    private void sendEmail(String toEmail, String subject, String plainText) {
        try {
            SendEmailRequest request = SendEmailRequest.builder()
                    .source(fromEmail)
                    .destination(Destination.builder()
                            .toAddresses(toEmail)
                            .build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .text(Content.builder().data(plainText).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build();

            sesClient.sendEmail(request);
            log.info("Email envoyé à {} : {}", toEmail, subject);

        } catch (SesException e) {
            // Ne pas relancer — la notification est best-effort
            log.error("Échec envoi email à {} : {}", toEmail, e.getMessage());
        }
    }
}
