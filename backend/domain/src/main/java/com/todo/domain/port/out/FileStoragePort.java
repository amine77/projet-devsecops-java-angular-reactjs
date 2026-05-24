package com.todo.domain.port.out;

import java.io.InputStream;

/**
 * PORT SORTANT — FileStoragePort
 *
 * Abstraction du stockage de fichiers (pièces jointes).
 *
 * Implémentations :
 * → S3FileStorageAdapter    : stockage AWS S3 (profil aws)
 * → LocalFileStorageAdapter : stockage disque local (profil local)
 *
 * C'est un exemple classique de l'avantage hexagonal :
 * le domaine dit "stocke ce fichier", et l'infrastructure
 * choisit S3 ou le disque selon le profil Spring actif.
 * Le code de création de tâche n'a JAMAIS besoin de changer.
 */
public interface FileStoragePort {

    /**
     * Stocke un fichier et retourne sa clé de référence.
     *
     * @param taskId      ID de la tâche (pour construire le chemin)
     * @param fileName    nom original du fichier
     * @param contentType type MIME (image/jpeg, application/pdf...)
     * @param content     flux du contenu du fichier
     * @return clé unique permettant de retrouver le fichier (ex: "tasks/uuid/fichier.pdf")
     */
    String store(String taskId, String fileName, String contentType, InputStream content);

    /**
     * Génère une URL pré-signée permettant le téléchargement direct depuis S3.
     *
     * L'URL expire après 15 minutes (configurable).
     * En local : retourne simplement le chemin absolu du fichier.
     *
     * @param storageKey clé retournée par store()
     * @return URL temporaire de téléchargement
     */
    String generateDownloadUrl(String storageKey);

    /**
     * Supprime un fichier du stockage.
     * Appelé quand une tâche est supprimée (A_FAIRE uniquement — RG-05).
     */
    void delete(String storageKey);
}
