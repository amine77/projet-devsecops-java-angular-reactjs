package com.todo.infrastructure.storage;

import com.todo.domain.port.out.FileStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * ══════════════════════════════════════════════════════════════
 *  ADAPTATEUR STOCKAGE LOCAL — LocalFileStorageAdapter
 * ══════════════════════════════════════════════════════════════
 *
 *  Implémente FileStoragePort en stockant les fichiers sur le DISQUE LOCAL.
 *  Actif uniquement avec le profil Spring "local".
 *
 *  QUAND UTILISER :
 *  → Développement local sans accès AWS S3
 *  → Tests d'intégration (Testcontainers ne fournit pas S3 natif)
 *    Note : pour S3 en test, on utiliserait LocalStack
 *
 *  STRUCTURE DES DOSSIERS :
 *  ${local.storage.path}/  (par défaut: /tmp/todo-attachments/)
 *  └── tasks/
 *      └── {taskId}/
 *          └── rapport-mensuel.pdf
 *
 *  CLÉS DE STOCKAGE :
 *  → store() retourne "tasks/{taskId}/{fileName}" comme clé
 *  → La clé est stockée en base dans task_attachments.storage_key
 *  → generateDownloadUrl() retourne le chemin absolu sur le disque
 *    (le contrôleur servira le fichier via Spring MVC Resource)
 *
 *  LIMITATIONS :
 *  → Pas de scalabilité : les fichiers sont sur un seul nœud
 *  → En production (profil "aws") → S3FileStorageAdapter
 *  → Le code du domaine ne change pas — seul l'adaptateur change
 *
 *  @Profile("!aws") :
 *  → Même stratégie que NoOpNotificationAdapter
 * ══════════════════════════════════════════════════════════════
 */
@Component
@Profile("!aws")
public class LocalFileStorageAdapter implements FileStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageAdapter.class);

    /**
     * Répertoire de base pour le stockage local.
     * Configurable dans application-local.yml :
     *   local.storage.path: /tmp/todo-attachments
     */
    @Value("${local.storage.path:/tmp/todo-attachments}")
    private String storagePath;

    @Override
    public String store(String taskId, String fileName, String contentType, InputStream content) {
        try {
            // Construire le chemin : /tmp/todo-attachments/tasks/{taskId}/
            Path taskDir = Paths.get(storagePath, "tasks", taskId);
            Files.createDirectories(taskDir); // mkdir -p équivalent

            // Nettoyer le nom de fichier (éviter path traversal)
            String sanitizedFileName = sanitizeFileName(fileName);
            Path filePath = taskDir.resolve(sanitizedFileName);

            // Copier le flux vers le fichier (REPLACE_EXISTING si déjà présent)
            long bytesWritten = Files.copy(content, filePath, StandardCopyOption.REPLACE_EXISTING);

            String storageKey = "tasks/" + taskId + "/" + sanitizedFileName;
            log.info("Fichier stocké localement : {} ({} bytes)", storageKey, bytesWritten);
            return storageKey;

        } catch (IOException e) {
            throw new RuntimeException("Erreur lors du stockage local du fichier : " + fileName, e);
        }
    }

    @Override
    public String generateDownloadUrl(String storageKey) {
        // En local : retourner le chemin absolu
        // Le contrôleur REST servira le fichier via un endpoint dédié (Phase future)
        return Paths.get(storagePath, storageKey).toAbsolutePath().toString();
    }

    @Override
    public void delete(String storageKey) {
        try {
            Path filePath = Paths.get(storagePath, storageKey);
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Fichier supprimé : {}", storageKey);
            } else {
                log.warn("Fichier non trouvé pour suppression : {}", storageKey);
            }
        } catch (IOException e) {
            log.error("Erreur lors de la suppression du fichier : {}", storageKey, e);
        }
    }

    /**
     * Nettoie le nom de fichier pour éviter les attaques par "path traversal".
     *
     * PATH TRAVERSAL :
     * → Un attaquant envoie fileName = "../../etc/passwd"
     * → Sans nettoyage, on stockerait sur /etc/passwd !
     * → Solution : garder uniquement le nom de fichier (pas de chemin)
     *
     * @param fileName nom original du fichier
     * @return nom sécurisé (uniquement le nom, pas le chemin)
     */
    private String sanitizeFileName(String fileName) {
        // Extraire uniquement le nom de fichier (pas le chemin)
        Path filePath = Paths.get(fileName).getFileName();
        if (filePath == null) {
            throw new IllegalArgumentException("Nom de fichier invalide : " + fileName);
        }
        // Remplacer les espaces et caractères spéciaux (sauf . et -)
        return filePath.toString().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
