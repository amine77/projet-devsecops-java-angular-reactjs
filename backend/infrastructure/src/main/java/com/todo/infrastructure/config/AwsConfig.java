package com.todo.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * ══════════════════════════════════════════════════════════════
 *  CONFIGURATION AWS — AwsConfig
 * ══════════════════════════════════════════════════════════════
 *
 *  Déclare les clients AWS SDK v2, conditionnellement activés
 *  selon le profil Spring actif.
 *
 *  STRATÉGIE MULTI-PROFILS :
 *  ┌─────────────────────────────────────────────────────────┐
 *  │  Profil "local"                                         │
 *  │  → Pas de clients AWS (SES, S3, DynamoDB non créés)     │
 *  │  → NoOpNotificationAdapter (email simulé)               │
 *  │  → LocalFileStorageAdapter (disque local)               │
 *  │  → InMemoryUserPreferenceAdapter (Map en mémoire)        │
 *  │                                                         │
 *  │  Profil "aws"                                           │
 *  │  → SesClient, S3Client, DynamoDbClient créés ici        │
 *  │  → SesNotificationAdapter actif                         │
 *  │  → S3FileStorageAdapter actif                           │
 *  │  → DynamoUserPreferenceAdapter actif                    │
 *  └─────────────────────────────────────────────────────────┘
 *
 *  @Profile("aws") :
 *  → Ce Bean n'est créé que si le profil Spring "aws" est actif
 *  → spring.profiles.active=aws dans application-aws.yml
 *  → Lancé avec : java -jar app.jar --spring.profiles.active=aws
 *
 *  AUTHENTIFICATION AWS (DefaultCredentialsProvider) :
 *  → Cherche les credentials dans cet ordre :
 *    1. Variables d'environnement : AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY
 *    2. Fichier ~/.aws/credentials
 *    3. Profil de configuration AWS (~/.aws/config)
 *    4. Metadata du container ECS (si déployé en ECS)
 *    5. Instance Metadata Service (IMDS) pour EC2/App Runner
 *    6. IAM Role (recommandé en production — zéro credential stocké)
 *  → En GitHub Actions : OIDC → IAM Role (zero credentials stockés — Phase 5)
 *
 *  RÉGION AWS :
 *  → Configurable via la variable d'environnement AWS_REGION
 *  → Défaut : eu-west-3 (Paris) — proche de l'utilisateur européen
 *
 *  AWS SDK v2 vs v1 :
 *  → SDK v2 : builder pattern, immutable, async non-bloquant possible
 *  → SDK v1 : déprécié mais encore utilisé par Spring Cloud AWS 2.x
 *  → On utilise SDK v2 directement (pas Spring Cloud AWS pour S3/SES)
 *  → Spring Cloud AWS 3.x est compatible SDK v2 mais on préfère le contrôle direct
 * ══════════════════════════════════════════════════════════════
 */
@Configuration
@Profile("aws") // ← Ce bean bloc COMPLET n'est actif qu'avec -Dspring.profiles.active=aws
public class AwsConfig {

    @Value("${aws.region:eu-west-3}")
    private String awsRegion;

    /**
     * Client SES pour l'envoi d'emails.
     * Utilisé par SesNotificationAdapter.
     *
     * PRÉREQUIS :
     * → Email expéditeur vérifié dans SES Console
     * → IAM policy : ses:SendEmail sur resource "*"
     * → Sortir du mode sandbox SES pour envoyer à des emails non-vérifiés
     */
    @Bean
    public SesClient sesClient() {
        return SesClient.builder()
                .region(Region.of(awsRegion))
                // DefaultCredentialsProvider : cherche les creds dans l'ordre standard AWS
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Client S3 pour le stockage de fichiers (pièces jointes).
     * Utilisé par S3FileStorageAdapter.
     *
     * BUCKET : "todo-enterprise-attachments" (configuré dans application-aws.yml)
     * PRÉREQUIS :
     * → Bucket existant avec versioning activé
     * → IAM policy : s3:PutObject, s3:GetObject, s3:DeleteObject sur le bucket
     * → Server-side encryption : SSE-S3 ou SSE-KMS recommandé
     */
    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * S3Presigner : génère les URLs de téléchargement temporaires (15 min).
     * Séparé du S3Client car c'est une opération locale (pas d'appel réseau).
     */
    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Client DynamoDB pour les préférences utilisateur.
     * Utilisé par DynamoUserPreferenceAdapter.
     *
     * TABLE : "user-preferences"
     * PRÉREQUIS :
     * → Table DynamoDB créée (Terraform ou AWS Console)
     * → IAM policy : dynamodb:PutItem, dynamodb:GetItem, dynamodb:DeleteItem
     * → Always free tier : 25 Go, 25 WCU, 25 RCU
     *   → Suffisant pour une application d'entreprise standard
     */
    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
