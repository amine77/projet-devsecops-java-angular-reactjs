package com.todo.infrastructure.storage;

import com.todo.domain.port.out.FileStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.time.Duration;

/**
 * ══════════════════════════════════════════════════════════════
 *  ADAPTATEUR S3 — S3FileStorageAdapter
 * ══════════════════════════════════════════════════════════════
 *
 *  Implémente FileStoragePort via AWS S3.
 *  Actif uniquement avec le profil Spring "aws".
 *
 *  POURQUOI S3 POUR LES PIÈCES JOINTES ?
 *  → Durabilité 11 9's (99.999999999%) — quasi-indestructible
 *  → Scalabilité illimitée sans gestion de capacité
 *  → Versioning natif (récupérer une version antérieure)
 *  → Lifecycle rules : archiver vers Glacier après 90 jours (économique)
 *  → URLs pré-signées : le client télécharge DIRECTEMENT depuis S3
 *    sans passer par notre API (économise bande passante et CPU)
 *
 *  STRUCTURE DES CLÉS S3 :
 *  → tasks/{taskId}/{sanitizedFileName}
 *  → Ex: "tasks/550e8400-e29b-41d4-a716-446655440000/rapport_Q1.pdf"
 *
 *  URLs PRÉ-SIGNÉES (Presigned URLs) :
 *  → S3 génère une URL temporaire avec signature HMAC
 *  → Le client HTTP peut télécharger le fichier directement depuis S3
 *    SANS passer par notre backend
 *  → Avantages :
 *    - Notre API ne sert pas de gros fichiers (pas de saturation)
 *    - S3 gère le débit, le CDN, la géolocalisation
 *    - La signature expire après 15 minutes (sécurité)
 *  → L'URL contient : bucket, key, expiration, signature AWS
 *
 *  SÉCURITÉ S3 :
 *  → Bucket PRIVÉ (pas de public access)
 *  → Server-side encryption : SSE-S3 (géré par AWS)
 *  → Versioning activé (récupérer les suppressions accidentelles)
 *  → Accès via IAM Role uniquement (pas de keys statiques)
 * ══════════════════════════════════════════════════════════════
 */
@Component
@Profile("aws")
public class S3FileStorageAdapter implements FileStoragePort {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorageAdapter.class);

    /** Durée de validité des URLs pré-signées : 15 minutes */
    private static final Duration PRESIGN_TTL = Duration.ofMinutes(15);

    @Value("${aws.s3.bucket-name:todo-enterprise-attachments}")
    private String bucketName;

    /** Client S3 SDK v2 — injecté depuis AwsConfig */
    private final S3Client s3Client;

    /**
     * S3Presigner : génère les URLs pré-signées.
     * Séparé du S3Client car opérations différentes.
     */
    private final S3Presigner s3Presigner;

    public S3FileStorageAdapter(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client    = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public String store(String taskId, String fileName, String contentType, InputStream content) {
        String sanitizedFileName = sanitizeFileName(fileName);
        String s3Key = "tasks/" + taskId + "/" + sanitizedFileName;

        try {
            // Lire le contenu pour obtenir la taille (requis par S3 SDK)
            byte[] bytes = content.readAllBytes();

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .contentType(contentType)
                    .contentLength((long) bytes.length)
                    // Server-side encryption (SSE-S3 = géré par AWS, gratuit)
                    .serverSideEncryption(
                        software.amazon.awssdk.services.s3.model.ServerSideEncryption.AES256
                    )
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(bytes));
            log.info("Fichier uploadé sur S3 : s3://{}/{}", bucketName, s3Key);
            return s3Key;

        } catch (Exception e) {
            throw new RuntimeException("Erreur upload S3 : " + s3Key, e);
        }
    }

    /**
     * Génère une URL pré-signée S3 valide 15 minutes.
     *
     * EXEMPLE D'URL GÉNÉRÉE :
     * https://todo-enterprise-attachments.s3.eu-west-3.amazonaws.com/
     *   tasks/uuid/fichier.pdf
     *   ?X-Amz-Algorithm=AWS4-HMAC-SHA256
     *   &X-Amz-Credential=...
     *   &X-Amz-Date=20260524T103000Z
     *   &X-Amz-Expires=900  ← 15 minutes en secondes
     *   &X-Amz-Signature=...
     *
     * LE CLIENT :
     * 1. Appelle GET /api/tasks/{id}/attachments/{attachmentId}/download
     * 2. Notre API génère l'URL pré-signée et retourne 302 Redirect
     * 3. Le navigateur suit la redirection vers S3 directement
     */
    @Override
    public String generateDownloadUrl(String storageKey) {
        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .signatureDuration(PRESIGN_TTL)
                .getObjectRequest(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build())
                .build()
        );
        return presignedRequest.url().toString();
    }

    @Override
    public void delete(String storageKey) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(storageKey)
                    .build());
            log.info("Fichier supprimé de S3 : s3://{}/{}", bucketName, storageKey);
        } catch (Exception e) {
            log.error("Erreur suppression S3 : {}", storageKey, e);
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
