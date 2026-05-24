# To-Do List Enterprise — Préparation Entretiens Techniques

> Stack Senior : Java 21 · Spring Boot 3 · Angular 20 · React 19 · Minikube · Kafka · PostgreSQL · MongoDB · Redis · AWS (RDS · S3 · Lambda · DynamoDB · Cognito · Step Functions · Athena · ECR · SES · SNS · CloudWatch · X-Ray · Parameter Store) · Gatling · PLG+ELK

---

## Documents du projet

| Document | Contenu |
|---|---|
| **README.md** | Architecture, stack, infrastructure, roadmap |
| **[FUNCTIONAL-MODEL.md](FUNCTIONAL-MODEL.md)** | Spécification fonctionnelle — 3 rôles, workflow, règles métier |
| **[ARGUMENTAIRES-ENTRETIEN.md](ARGUMENTAIRES-ENTRETIEN.md)** | Argumentaires techniques pour les entretiens |

---

## Table des matières

1. [Contexte & Objectifs](#1-contexte--objectifs)
2. [Stack technique complète](#2-stack-technique-complète)
3. [Architecture hybride — Minikube + AWS](#3-architecture-hybride--minikube--aws)
4. [Argumentaire entretien — les choix clés](#4-argumentaire-entretien--les-choix-clés)
5. [Architecture Hexagonale — Ports & Adapters](#5-architecture-hexagonale--ports--adapters)
6. [Structure du projet](#6-structure-du-projet)
7. [Stratégie Terraform — Ressources éphémères vs permanentes](#7-stratégie-terraform--ressources-éphémères-vs-permanentes)
8. [FinOps — ECR Lifecycle Policies](#7b-finops--ecr-lifecycle-policies)
9. [AWS Lambda — 3 use cases pédagogiques](#8-aws-lambda--3-use-cases-pédagogiques)
9. [Estimation des coûts AWS](#9-estimation-des-coûts-aws)
10. [Suivi des coûts AWS en temps réel](#10-suivi-des-coûts-aws-en-temps-réel)
11. [Infrastructure Kubernetes — Namespaces & Services](#11-infrastructure-kubernetes--namespaces--services)
12. [Plan de déploiement Helm](#12-plan-de-déploiement-helm)
13. [Pré-requis & Installation](#13-pré-requis--installation)
14. [Commandes Minikube essentielles](#14-commandes-minikube-essentielles)
15. [Pipeline DevSecOps — GitHub Actions](#15-pipeline-devsecops--github-actions)
16. [Roadmap du projet — étapes par étapes](#16-roadmap-du-projet--étapes-par-étapes)
17. [Comparatif Minikube vs EKS](#17-comparatif-minikube-vs-eks)
18. [Ressources pour aller plus loin](#18-ressources-pour-aller-plus-loin)

---

## 1. Contexte & Objectifs

Ce projet est une application de gestion de tâches (**To-Do List**) conçue pour être **volontairement over-engineered** dans un but précis : démontrer en entretien technique une maîtrise des architectures distribuées, du Cloud AWS et des bonnes pratiques modernes.

### Modèle fonctionnel (résumé)

L'application gère des **actes de gestion (to-dos)** avec 3 rôles et un workflow en un seul niveau d'approbation.

| Rôle | Périmètre | Actions clés |
|---|---|---|
| **Gestionnaire** | Ses to-dos uniquement | Créer, Modifier (À faire/Rejetée), Supprimer (À faire uniquement) |
| **Manager** | Toute son équipe | Valider, Rejeter (motif obligatoire), Placer en Done |
| **Super-Admin** | Toute l'application | Toutes les actions + administration + configuration |

```
[À FAIRE] → Valider → [VALIDÉE] → Done → [DONE]
          → Rejeter → [REJETÉE] → (Gestionnaire modifie) → [À FAIRE]
          → Done    →            [DONE]
```

> Spécification complète : [FUNCTIONAL-MODEL.md](FUNCTIONAL-MODEL.md)

---

### Stratégie d'apprentissage

- **Minikube** pour tout ce qui est Kubernetes — gratuit, toujours disponible
- **AWS** activé uniquement pendant les sessions d'apprentissage, ressources éphémères détruites via `terraform destroy`
- **Coût AWS estimé : 0 $ (an 1, Free Tier) — $2 à $5/mois (an 2+)**

### Ce que ce projet démontre

| Compétence | Ce qu'on met en œuvre |
|---|---|
| Architecture logicielle | Architecture Hexagonale (DDD, Ports & Adapters) |
| Backend | Java 21 (Virtual Threads, Records, Pattern Matching) |
| Sécurité | Spring Security OIDC, Keycloak (local) / Cognito (AWS) |
| Frontend | Double implémentation Angular 20 + React 19 |
| Serverless | AWS Lambda (Java 21 + SnapStart), API Gateway, EventBridge, Step Functions |
| Stockage cloud | S3 (frontend + pièces jointes), CloudFront (CDN), ECR (images Docker) |
| Base de données cloud | RDS PostgreSQL (éphémère), DynamoDB (permanent, always free) |
| Data local | PostgreSQL + MongoDB + Redis (Minikube) |
| Messaging | Kafka (Event-Driven), SNS (fan-out), SES (email), SQS (file) |
| Observabilité cloud | CloudWatch, X-Ray (distributed tracing), Athena (analytics sur S3) |
| Configuration | AWS Systems Manager Parameter Store |
| Kubernetes | Minikube, Helm, StatefulSets, Ingress, PVC |
| IaC | Terraform (modules permanent + éphémère) |
| Observabilité locale | Stack PLG (Prometheus + Loki + Grafana) + ELK |
| Tests de performance | Gatling (charge, stress, soak, spike) |
| CI/CD | GitHub Actions, SonarCloud, JaCoCo, Trivy, OWASP |

---

## 2. Stack technique complète

### Backend
| Technologie | Version | Rôle |
|---|---|---|
| Java | 21 LTS | Virtual Threads, Records, Pattern Matching |
| Spring Boot | 3.3+ | Framework applicatif |
| Spring Security | 6.x | OIDC / OAuth2 (Keycloak local, Cognito AWS) |
| Spring Batch | 5.x | Traitement par lots (archivage, reporting) |
| Spring Data JPA | 3.x | Accès PostgreSQL local et RDS |
| Spring Data MongoDB | 4.x | Accès MongoDB |
| Spring Kafka | 3.x | Producteur / Consommateur Kafka |
| Spring Cache + Lettuce | — | Mise en cache Redis |
| Spring Cloud AWS | 3.x | Intégration Parameter Store, SQS, SES |
| AWS SDK v2 (S3, DynamoDB, SES…) | 2.x | Accès aux services AWS |

### Frontend
| Technologie | Version | Rôle |
|---|---|---|
| Angular | 20 | Frontend principal — Signals stables, Zoneless, Control Flow, Resource API |
| React | 19 | Frontend alternatif |
| Zustand | 5.x | State management React |
| React Router | 7.x | Routing React |
| Angular Material | 20 | Composants UI Angular |

### AWS Services — Permanent (< $0.10/mois)
| Service | Rôle | Free Tier |
|---|---|---|
| **S3** | Frontend Angular + React + pièces jointes | 5 GB gratuit |
| **CloudFront** | CDN pour les builds statiques | 1 TB/mois gratuit |
| **ECR** | Registry Docker privé + Lifecycle Policies (nettoyage automatique FinOps) | 500 MB gratuit |
| **Lambda (×3)** | Traitement pièces jointes, archivage, reporting | 1M req/mois gratuit |
| **API Gateway** | Endpoint REST serverless (reporting) | 1M appels/mois gratuit |
| **Step Functions** | Orchestration workflow pièces jointes | 4 000 transitions/mois gratuit |
| **EventBridge** | Déclencheur planifié pour la Lambda archivage | Gratuit |
| **SQS** | File de messages Lambda → Spring Boot | 1M messages/mois gratuit |
| **SNS** | Fan-out : email + SQS | 1M req/mois gratuit |
| **SES** | Envoi d'emails (tâche assignée, complétée) | 62 000 emails/mois gratuit |
| **DynamoDB** | Préférences utilisateur (NoSQL, always free) | 25 GB + 25 WCU/RCU toujours gratuit |
| **Cognito** | Identity Provider cloud (OIDC) | 50 000 MAU gratuit |
| **CloudWatch** | Logs Lambdas + métriques custom + alarmes | 10 métriques, 5 GB logs gratuit |
| **X-Ray** | Distributed tracing (API Gateway → Lambda → RDS) | 100 000 traces/mois gratuit |
| **Parameter Store** | Configuration centralisée (endpoints, TTL) | Paramètres Standard gratuits |
| **Athena** | Requêtes SQL sur les archives S3 | $5/TB scanné |
| **IAM** | Rôles et policies | Gratuit |
| **VPC + Security Groups** | Réseau isolé | Gratuit |
| **Budget + Cost Explorer** | Suivi des coûts en temps réel | Gratuit |
| **Cost Anomaly Detection** | Alerte ML sur dépenses anormales | Gratuit |

### AWS Services — Éphémère (créé/détruit à chaque session)
| Service | Rôle | Coût estimé |
|---|---|---|
| **App Runner** | Exposition publique Spring Boot (HTTPS auto, scaling intégré) | ~$0.10/session |
| **RDS PostgreSQL** | Base de données relationnelle cloud | Free Tier an 1 / ~$0.03/session an 2+ |
| **NAT Gateway** | Accès internet depuis subnet privé | ~$0.09/session |

### Data & Messaging (local — Minikube)
| Technologie | Rôle |
|---|---|
| PostgreSQL 16 | Données relationnelles (même schéma que RDS) |
| MongoDB 7 | Historique des événements |
| Redis 7 | Cache des requêtes, sessions |
| Kafka (Redpanda) | Bus d'événements asynchrone |

### Infrastructure & Kubernetes
| Technologie | Rôle |
|---|---|
| Docker (multi-stage) | Build des images optimisées |
| Minikube | Cluster Kubernetes local (0 €) |
| Helm 3 | Packaging et déploiement |
| Keycloak 24 | OIDC local (remplacé par Cognito en session AWS) |
| NGINX Ingress | Reverse proxy et routage |
| Terraform | IaC — modules `permanent/` et `ephemeral/` |

### Observabilité
| Technologie | Rôle |
|---|---|
| Prometheus | Collecte de métriques (Minikube) |
| Grafana | Dashboards métriques + logs (Minikube) |
| Loki | Agrégation de logs (Minikube) |
| ELK Stack | Elasticsearch + Logstash + Kibana |
| CloudWatch | Logs et métriques AWS (Lambdas, RDS) |
| X-Ray | Distributed tracing bout-en-bout |
| Athena | Analyse des archives et logs sur S3 |

### CI/CD & Qualité
| Technologie | Rôle |
|---|---|
| **GitHub Actions** | Pipeline DevSecOps (OIDC AWS — zéro clé stockée) |
| SonarQube | Analyse statique |
| JaCoCo | Couverture de tests (objectif : ≥ 80%) |
| OWASP Dependency-Check | Scan des dépendances CVE |
| Trivy | Scan des images Docker (via ECR aussi) |
| **Gatling** | **Tests de performance (charge, stress, soak, spike)** |

---

## 3. Architecture hybride — Minikube + AWS

### Vue d'ensemble de l'exposition des services

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  POINT D'ENTRÉE UNIQUE : CloudFront (https://d1234.cloudfront.net)           │
│                                                                              │
│  /angular/*    → Origin S3      (Angular build statique)                     │
│  /react/*      → Origin S3      (React build statique)                       │
│  /api/*        → Origin App Runner (Spring Boot — éphémère)                  │
│  /reports/*    → Origin API Gateway (Lambda ReportingApi — permanent)        │
│  /auth/*       → Origin Cognito Hosted UI (authentification)                 │
└──────────────────────────────────────────────────────────────────────────────┘
```

> **Pas d'ALB dans ce projet.** CloudFront joue le rôle de reverse proxy et de point
> d'entrée unique. App Runner intègre son propre load balancer. Résultat : zéro CORS,
> une seule URL, HTTPS automatique partout.

### Pourquoi pas d'AWS Load Balancer (ALB) ?

| Outil | Quand l'utiliser | Coût minimum |
|---|---|---|
| **ALB** | Multi-instances EC2, ECS, EKS | ~$16/mois (même à vide) |
| **App Runner** | Conteneur unique auto-scalé | ~$0.10/session (éphémère) |
| **CloudFront** | Frontend statique + proxy API | Free Tier |
| **API Gateway** | Lambda / microservices serverless | Free Tier |

Un ALB se justifie avec EKS ou plusieurs instances EC2. Dans ce projet (Minikube local + App Runner AWS), l'ALB n'apporte rien et coûte $16/mois à vide.

### Diagramme complet

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            AWS eu-west-3                                │
│                                                                         │
│  ┌──────────── EXPOSITION — CloudFront (point d'entrée unique) ──────┐  │
│  │                                                                   │  │
│  │  https://d1234.cloudfront.net                                     │  │
│  │  │                                                                │  │
│  │  ├─ /angular/* ──▶ S3 (Angular 20 build, cache long terme)       │  │
│  │  ├─ /react/*   ──▶ S3 (React 19 build, cache long terme)         │  │
│  │  ├─ /api/*     ──▶ App Runner (Spring Boot — éphémère)           │  │
│  │  │               ──▶ RDS PostgreSQL (éphémère)                   │  │
│  │  ├─ /reports/* ──▶ API Gateway ──▶ Lambda ReportingApi           │  │
│  │  └─ /auth/*    ──▶ Cognito Hosted UI                             │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────── PERMANENT — Free Tier (< $0.10/mois) ────────────────┐  │
│  │                                                                   │  │
│  │  S3 : Angular + React builds, pièces jointes, archives JSON      │  │
│  │                    │ S3 Event trigger                             │  │
│  │                    ▼                                              │  │
│  │  Step Functions ──▶ Lambda ValidateMime                           │  │
│  │                  ──▶ Lambda GenerateThumbnail                     │  │
│  │                  ──▶ Lambda WriteMetadata ──▶ RDS                 │  │
│  │                  ──▶ SNS ──▶ SES (email) + SQS ──▶ Spring Boot   │  │
│  │                                                                   │  │
│  │  EventBridge (cron 02h00) ──▶ Lambda TaskArchiver                │  │
│  │                    ──▶ archive S3 JSON, purge RDS                 │  │
│  │                    ──▶ Athena requête les archives                 │  │
│  │                                                                   │  │
│  │  API Gateway ──▶ Lambda ReportingApi (SnapStart + X-Ray)         │  │
│  │  Cognito ──▶ Spring Security (OIDC)                              │  │
│  │  Parameter Store ──▶ Spring Boot (endpoints, config)             │  │
│  │  ECR ──▶ images Docker (Lifecycle Policies actives)              │  │
│  │  CloudWatch · X-Ray · DynamoDB (préférences user)                │  │
│  └───────────────────────────────────────────────────────────────────┘  │
│                                                                         │
│  ┌──────────── ÉPHÉMÈRE — terraform destroy fin de session ──────────┐  │
│  │  App Runner (Spring Boot Java 21)     ~$0.10/session              │  │
│  │  RDS PostgreSQL db.t3.micro           FREE an 1                   │  │
│  │  NAT Gateway                          ~$0.09/session              │  │
│  └───────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────┬────────────────────────────────┘
                                         │ dev local uniquement
┌────────────────────────────────────────▼────────────────────────────────┐
│                      Machine Locale (Windows 11)                        │
│                                                                         │
│  ┌─────────────── MINIKUBE — dev quotidien ──────────────────────┐     │
│  │  http://todo.local  (hosts file)                              │     │
│  │  NGINX Ingress                                                │     │
│  │  ├─ /api/*    → spring-api (Java 21, profil local)            │     │
│  │  ├─ /auth/*   → Keycloak                                      │     │
│  │  ├─ /ng/      → Angular (dev server)                          │     │
│  │  └─ /react/   → React (dev server)                            │     │
│  │                                                               │     │
│  │  Namespace data : PostgreSQL · MongoDB · Redis · Kafka        │     │
│  │  Namespace monitoring : Prometheus · Grafana · Loki           │     │
│  └───────────────────────────────────────────────────────────────┘     │
└─────────────────────────────────────────────────────────────────────────┘
```

### App Runner — exposition de l'API Spring Boot

App Runner prend une image ECR et expose automatiquement un endpoint HTTPS avec load balancing intégré, scaling de 0 à N instances, et healthcheck.

```hcl
# terraform/ephemeral/app_runner.tf

resource "aws_apprunner_service" "todo_api" {
  service_name = "todo-api"

  source_configuration {
    image_repository {
      image_identifier      = "${var.ecr_registry}/todo-api:latest"
      image_repository_type = "ECR"
      image_configuration {
        port = "8080"
        runtime_environment_variables = {
          SPRING_PROFILES_ACTIVE  = "aws"
          SPRING_DATASOURCE_URL   = "jdbc:postgresql://${aws_db_instance.postgres.address}:5432/tododb"
          COGNITO_ISSUER_URI      = var.cognito_issuer_uri
        }
      }
    }
    authentication_configuration {
      access_role_arn = aws_iam_role.apprunner_ecr.arn
    }
  }

  instance_configuration {
    cpu    = "0.25 vCPU"   # minimum pour réduire les coûts
    memory = "0.5 GB"
  }

  health_check_configuration {
    path     = "/actuator/health"
    protocol = "HTTP"
  }

  tags = local.common_tags
}

output "api_url" {
  value = "https://${aws_apprunner_service.todo_api.service_url}"
}
```

### CloudFront — configuration multi-origines

```hcl
# terraform/permanent/cloudfront.tf

resource "aws_cloudfront_distribution" "todo" {

  # Origine 1 : S3 (Angular + React)
  origin {
    origin_id   = "s3-frontend"
    domain_name = aws_s3_bucket.frontend.bucket_regional_domain_name
    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.oai.cloudfront_access_identity_path
    }
  }

  # Origine 2 : App Runner (Spring Boot API) — éphémère, mise à jour à chaque session
  origin {
    origin_id   = "app-runner-api"
    domain_name = var.app_runner_url   # injecté par terraform/ephemeral output
    custom_origin_config {
      http_port              = 443
      https_port             = 443
      origin_protocol_policy = "https-only"
    }
  }

  # Origine 3 : API Gateway (Lambda reporting) — permanent
  origin {
    origin_id   = "api-gateway-reporting"
    domain_name = "${aws_api_gateway_rest_api.reporting.id}.execute-api.eu-west-3.amazonaws.com"
    custom_origin_config {
      http_port              = 443
      https_port             = 443
      origin_protocol_policy = "https-only"
    }
  }

  # Comportement par défaut → S3 Angular
  default_cache_behavior {
    target_origin_id       = "s3-frontend"
    viewer_protocol_policy = "redirect-to-https"
    cached_methods         = ["GET", "HEAD"]
    allowed_methods        = ["GET", "HEAD"]
    compress               = true
    cache_policy_id        = "658327ea-f89d-4fab-a63d-7e88639e58f6" # CachingOptimized
  }

  # /api/* → App Runner (Spring Boot)
  ordered_cache_behavior {
    path_pattern           = "/api/*"
    target_origin_id       = "app-runner-api"
    viewer_protocol_policy = "https-only"
    allowed_methods        = ["DELETE", "GET", "HEAD", "OPTIONS", "PATCH", "POST", "PUT"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = "4135ea2d-6df8-44a3-9df3-4b5a84be39ad" # CachingDisabled (API)
    origin_request_policy_id = "b689b0a8-53d0-40ab-baf2-68738e2966ac" # AllViewer
  }

  # /reports/* → API Gateway (Lambda)
  ordered_cache_behavior {
    path_pattern           = "/reports/*"
    target_origin_id       = "api-gateway-reporting"
    viewer_protocol_policy = "https-only"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = "658327ea-f89d-4fab-a63d-7e88639e58f6" # CachingOptimized (5 min)
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  viewer_certificate {
    cloudfront_default_certificate = true   # HTTPS gratuit avec *.cloudfront.net
  }
}
```

### CORS — configuration Spring Boot

Avec CloudFront comme point d'entrée unique, les appels `/api/*` viennent du **même domaine** que le frontend — plus besoin de CORS. Mais on le configure quand même pour le dev local :

```java
// infrastructure/api/rest/CorsConfig.java
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);  // depuis Parameter Store
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

```yaml
# Parameter Store : /todo-app/local/cors/allowed-origins
app.cors.allowed-origins: http://todo.local,http://localhost:4200,http://localhost:3000

# Parameter Store : /todo-app/aws/cors/allowed-origins
app.cors.allowed-origins: https://d1234.cloudfront.net
```

### Basculement local ↔ AWS via profil Spring

```yaml
# application-local.yaml  (Minikube — dev quotidien)
spring.datasource.url: jdbc:postgresql://postgres.data.svc:5432/tododb
spring.security.oauth2.resourceserver.jwt.issuer-uri: http://keycloak.todo-app.svc/realms/todo
app.storage.endpoint: http://minio.data.svc:9000

# application-aws.yaml  (session AWS — API publique via App Runner)
spring.datasource.url: ${TODO_RDS_URL}           # depuis Parameter Store
spring.security.oauth2.resourceserver.jwt.issuer-uri: ${COGNITO_ISSUER_URI}
app.storage.endpoint: https://s3.eu-west-3.amazonaws.com
```

---

## 4. Argumentaire entretien — les choix clés

### Pourquoi Java 21 ?

Java 21 apporte les **Virtual Threads** (Project Loom — JEP 444) : Spring Boot 3.2+ les active avec une seule propriété. Au lieu d'un thread OS par requête, des millions de virtual threads peuvent coexister. Pour une API qui fait des I/O vers Kafka, RDS, Redis et SQS en parallèle, ça se traduit par une meilleure utilisation des ressources sans changer le code métier.

Les **Records** éliminent le boilerplate des Value Objects. Le **Pattern Matching** rend le code de mapping expressif.

En entretien : *"J'utilise Java 21 pour ses apports concrets : Virtual Threads pour la performance I/O, Records pour la lisibilité du domaine, et la compatibilité avec Lambda SnapStart pour le serverless."*

### Pourquoi l'Architecture Hexagonale ?

La logique métier est isolée dans le domaine — zéro dépendance vers Spring, JPA, Kafka ou le SDK AWS.

- **Testabilité** : 100% de couverture sur le domaine avec des mocks Java purs, sans Spring
- **Portabilité** : `FileStoragePort` est implémenté par MinIO (local) ou S3 (AWS) selon le profil actif
- **Indépendance** : migrer de PostgreSQL local vers RDS ne touche pas le domaine

En entretien : *"L'architecture hexagonale m'a permis d'avoir deux implémentations de repository — l'une vers PostgreSQL Minikube, l'autre vers RDS AWS — sans toucher une ligne de logique métier."*

### Pourquoi RDS PostgreSQL et pas Aurora ?

RDS PostgreSQL db.t3.micro est dans le **Free Tier** la première année (750 heures/mois). Aurora Serverless v2 minimum 0.5 ACU coûte ~$43/mois en continu.

Pour un usage pédagogique avec `terraform destroy` à chaque session :
- RDS provisioning : ~3-5 min (vs 5-10 min pour Aurora)
- Coût par session (an 2+) : ~$0.03 (vs ~$0.12 pour Aurora)
- Même driver JDBC PostgreSQL, même Spring Data JPA

En entretien : *"J'ai choisi RDS PostgreSQL pour ce projet pédagogique. Je connais Aurora et ses avantages (scaling ACU, failover < 30s, performance ×5), mais RDS suffit ici et le Free Tier me permet d'apprendre sans frais la première année."*

### Pourquoi Zustand plutôt que Redux ?

Redux se justifie pour des équipes de +50 développeurs avec des domaines bien séparés. Zustand offre la même puissance avec 1/5 du code, est compatible React Concurrent Mode, et s'intègre nativement avec Immer.

En entretien : *"Je connais Redux et je l'utiliserais si l'équipe l'avait déjà adopté. Mais je ne l'introduirais pas dans un nouveau projet sans raison spécifique — Zustand couvre 95% des besoins avec beaucoup moins de boilerplate."*

### Pourquoi Kafka et pas SQS directement ?

Kafka conserve les messages et permet de les **rejouer**. SQS supprime les messages après consommation.

Dans ce projet, j'utilise les deux pour des besoins différents :
- **Kafka** : bus d'événements domaine, replay possible, découplage long terme
- **SQS** : communication Lambda → Spring Boot, file de travail simple

En entretien : *"Kafka m'achète le replay. Si j'ajoute un service d'analytics 6 mois après le lancement, il peut rejouer tous les événements depuis le début. Avec SQS, ces événements sont perdus."*

### Pourquoi Angular 20 et les Signals ?

Angular 20 marque la stabilité complète des Signals. Le mode **Zoneless** supprime `zone.js` — bundles plus légers, détection de changement granulaire. `resource()` gère les requêtes async sans `async pipe`. `input()` / `output()` remplacent les décorateurs.

En entretien : *"Le mode Zoneless me permet une détection de changement granulaire — seuls les composants qui lisent un Signal modifié se re-rendent. C'est conceptuellement proche de React avec `useMemo`, mais déclaratif dans le template."*

### Pourquoi AWS Lambda avec SnapStart ?

SnapStart réduit le cold start Java de 3-5s à ~200ms. Pour le use case ReportingApi (API Gateway → Lambda → RDS), c'est indispensable pour que l'utilisateur ne ressente pas le cold start.

En entretien : *"SnapStart prend un snapshot de la JVM initialisée et le restaure à chaque invocation froide. Pas besoin de passer à Node.js ou Python pour avoir des cold starts acceptables en Java."*

### Pourquoi `terraform destroy` à la fin de chaque session ?

C'est la pratique des **environnements éphémères** — standard dans les équipes DevOps pour les environnements de preview et de staging.

- Aucun risque de ressource oubliée qui facture
- Force la discipline IaC : tout doit être reproductible via `terraform apply`
- Pratique les cycles apply/destroy qui arrivent en prod pour les migrations

En entretien : *"Mes environnements sont éphémères par design. `terraform apply` recrée tout en 5 minutes. Ça force l'équipe à ne rien configurer manuellement — tout est dans le code versionné."*

### Pourquoi DynamoDB pour les préférences utilisateur ?

Les préférences (thème, tri, filtres sauvegardés) sont accédées par clé primaire `userId`, sans joins, avec un schéma flexible. DynamoDB est always-free jusqu'à 25 GB + 25 WCU/RCU.

En entretien : *"Je choisis le bon outil pour le bon besoin. PostgreSQL pour les données relationnelles transactionnelles, DynamoDB pour les lookups par clé primaire sans structure fixe. Un ORM sur DynamoDB serait un anti-pattern."*

### Pourquoi Step Functions pour le workflow pièces jointes ?

Une seule Lambda qui enchaîne validation + scan + miniature + écriture Aurora n'a pas de retry granulaire. Si l'étape 3 échoue, tout recommence.

Step Functions gère les **retry avec backoff exponentiel par étape**, les branches conditionnelles, et le state entre les étapes sans code de plomberie.

En entretien : *"Step Functions me donne la résilience étape par étape sans écrire un orchestrateur. C'est la différence entre la chorégraphie (chaque service réagit aux événements des autres) et l'orchestration (un chef d'orchestre coordonne)."*

### Pourquoi Gatling pour les tests de performance ?

DSL code-first versionnable, modèle asynchrone Netty (milliers d'utilisateurs sur un seul processus), intégration Maven native, rapports HTML P95/P99 automatiques.

En entretien : *"Mes scénarios Gatling sont dans Git, reviewables en PR, et rejoués dans le pipeline CI. Je peux comparer les rapports entre deux releases et détecter une régression de performance avant la prod."*

---

## 5. Architecture Hexagonale — Ports & Adapters

```
┌──────────────────────────────────────────────────────────────────┐
│                         DOMAINE (Zéro dépendance)                │
│                                                                  │
│  Entités : Task, User, Priority (Records Java 21)                │
│  Value Objects : TaskId, UserId, Title                           │
│  Domain Events : TaskCreated, TaskCompleted, TaskAssigned        │
│                                                                  │
│  Ports ENTRANTS              Ports SORTANTS                      │
│  ┌──────────────────┐       ┌─────────────────────────────────┐  │
│  │ TaskUseCase      │       │ TaskRepository                  │  │
│  │ UserUseCase      │       │ EventPublisher                  │  │
│  └──────────────────┘       │ CachePort                       │  │
│                             │ FileStoragePort                  │  │
│                             │ NotificationPort                 │  │
│                             │ UserPreferencePort               │  │
│                             └─────────────────────────────────┘  │
└──────────┬───────────────────────────────────┬───────────────────┘
           │                                   │
   ADAPTATEURS ENTRANTS               ADAPTATEURS SORTANTS
           │                                   │
┌──────────▼───────────┐   ┌───────────────────▼────────────────────┐
│ REST Controller      │   │ TaskJpaRepository → PostgreSQL / RDS    │
│ Kafka Consumer       │   │ MongoEventStore   → MongoDB             │
│ SQS Consumer         │   │ KafkaEventPublisher → Kafka             │
│ Batch Job Listener   │   │ RedisCache        → Redis               │
└──────────────────────┘   │ S3FileStorage     → MinIO (local) / S3  │
                           │ SesNotification   → SES (email)         │
                           │ DynamoPreference  → DynamoDB            │
                           └────────────────────────────────────────┘
```

### Règle d'or

> Le domaine ne connaît ni Spring, ni JPA, ni Kafka, ni le SDK AWS. Si tu importes `software.amazon.awssdk` dans le domaine, c'est une violation de l'architecture.

---

## 6. Structure du projet

```
todo-enterprise/
│
├── README.md
├── ARGUMENTAIRES-ENTRETIEN.md
│
├── backend/                                ← Maven multi-module
│   ├── pom.xml                             (parent BOM)
│   ├── domain/                             ← Zéro dépendance
│   │   └── src/main/java/
│   │       ├── model/                      (Task, User, Priority — Records)
│   │       ├── port/in/                    (TaskUseCase, UserUseCase)
│   │       ├── port/out/                   (TaskRepository, EventPublisher,
│   │       │                                CachePort, FileStoragePort,
│   │       │                                NotificationPort, UserPreferencePort)
│   │       └── service/                    (TaskDomainService)
│   │
│   ├── application/                        ← Use cases
│   │   └── src/main/java/usecase/
│   │       ├── CreateTaskHandler.java
│   │       ├── CompleteTaskHandler.java
│   │       └── AssignTaskHandler.java
│   │
│   └── infrastructure/                     ← Adapters
│       └── src/main/java/
│           ├── persistence/
│           │   ├── postgres/               (JPA → PostgreSQL local / RDS)
│           │   └── mongo/                  (Spring Data MongoDB)
│           ├── cache/
│           │   └── RedisTaskCacheAdapter.java
│           ├── messaging/
│           │   ├── KafkaTaskEventPublisher.java
│           │   └── SqsMessageConsumer.java
│           ├── storage/
│           │   ├── S3FileStorageAdapter.java     (profil aws)
│           │   └── MinioFileStorageAdapter.java  (profil local)
│           ├── notification/
│           │   └── SesNotificationAdapter.java
│           ├── preference/
│           │   └── DynamoUserPreferenceAdapter.java
│           ├── security/
│           │   └── OidcConfig.java              (Keycloak local / Cognito aws)
│           ├── batch/
│           │   └── TaskArchivingBatchJob.java
│           └── api/rest/
│               ├── TaskController.java
│               ├── TaskRequest.java
│               ├── TaskResponse.java
│               └── TaskMapper.java
│
├── lambda/                                 ← Fonctions AWS Lambda (Java 21)
│   ├── attachment-processor/               ← Déclenchée par Step Functions
│   │   └── src/main/java/
│   │       ├── ValidateMimeHandler.java
│   │       ├── GenerateThumbnailHandler.java
│   │       └── WriteMetadataHandler.java
│   ├── task-archiver/                      ← Déclenchée par EventBridge
│   │   └── src/main/java/
│   │       └── TaskArchiverHandler.java
│   └── reporting-api/                      ← API Gateway + SnapStart
│       └── src/main/java/
│           └── ReportingApiHandler.java
│
├── frontend-angular/                       ← Angular 20 + Signals Zoneless
│   └── Dockerfile                          (multi-stage : Node → Nginx)
│
├── frontend-react/                         ← React 19 + Zustand
│   └── Dockerfile                          (multi-stage : Vite → Nginx)
│
├── helm/
│   ├── todo-api/
│   ├── todo-react/
│   ├── todo-angular/
│   └── values/
│       ├── postgres-values.yaml
│       ├── mongo-values.yaml
│       ├── redis-values.yaml
│       ├── kafka-values.yaml
│       ├── keycloak-values.yaml
│       └── monitoring-values.yaml
│
├── terraform/
│   ├── permanent/                          ← apply une fois, jamais destroy
│   │   ├── s3.tf
│   │   ├── cloudfront.tf
│   │   ├── ecr.tf
│   │   ├── lambda.tf                       (3 fonctions + Step Functions)
│   │   ├── step_functions.tf
│   │   ├── api_gateway.tf
│   │   ├── eventbridge.tf
│   │   ├── sqs.tf
│   │   ├── sns.tf
│   │   ├── ses.tf
│   │   ├── dynamodb.tf
│   │   ├── cognito.tf
│   │   ├── cloudwatch.tf                   (dashboards, alarmes, budget)
│   │   ├── xray.tf
│   │   ├── parameter_store.tf
│   │   ├── iam.tf
│   │   ├── vpc.tf
│   │   └── variables.tf
│   │
│   └── ephemeral/                          ← apply début, destroy fin session
│       ├── rds.tf                          (PostgreSQL db.t3.micro)
│       ├── nat_gateway.tf
│       ├── outputs.tf                      (rds_endpoint exporté)
│       └── variables.tf
│
├── performance-tests/                      ← Simulations Gatling
│   └── src/test/scala/
│       ├── CreateTaskSimulation.scala
│       ├── StressTaskSimulation.scala
│       ├── SoakTaskSimulation.scala
│       └── SpikeTaskSimulation.scala
│
├── .github/
│   └── workflows/
│       ├── ci.yml                          ← Pipeline principal (build → test → analyse → package → deploy)
│       ├── performance.yml                 ← Gatling (déclenché manuellement ou sur schedule)
│       └── terraform-destroy.yml           ← Destruction des ressources éphémères en fin de session
├── docker-compose.yml
└── Makefile
```

---

## 7. Stratégie Terraform — Ressources éphémères vs permanentes

### Principe

| Catégorie | Ressources | Coût |
|---|---|---|
| **Permanent** | S3, CloudFront, ECR, Lambda, Step Functions, API Gateway, EventBridge, SQS, SNS, SES, DynamoDB, Cognito, CloudWatch, X-Ray, Parameter Store, IAM, VPC | < $0.10/mois |
| **Éphémère** | RDS PostgreSQL, NAT Gateway | FREE an 1 / ~$0.12/session an 2+ |

### Commandes de session

```bash
# ── DÉBUT DE SESSION (3-5 minutes) ───────────────────────────────
cd terraform/ephemeral
terraform apply -auto-approve

# Récupérer l'endpoint RDS et l'injecter dans Minikube
RDS_URL=$(terraform output -raw rds_endpoint)
kubectl create secret generic rds-secret \
  --from-literal=url="jdbc:postgresql://${RDS_URL}:5432/tododb" \
  --from-literal=username="todouser" \
  --from-literal=password="todopass" \
  -n todo-app --dry-run=client -o yaml | kubectl apply -f -

# Redémarrer Spring Boot avec le profil AWS
kubectl rollout restart deployment/todo-api -n todo-app

# ── FIN DE SESSION ────────────────────────────────────────────────
cd terraform/ephemeral
terraform destroy -auto-approve
```

### Makefile complet

```makefile
.PHONY: session-start session-end infra-init cluster-up build-api deploy-api dashboard grafana perf

session-start:
	@echo ">>> Provisionnement RDS + NAT Gateway..."
	cd terraform/ephemeral && terraform apply -auto-approve
	@RDS_URL=$$(cd terraform/ephemeral && terraform output -raw rds_endpoint); \
	kubectl create secret generic rds-secret \
	  --from-literal=url="jdbc:postgresql://$$RDS_URL:5432/tododb" \
	  -n todo-app --dry-run=client -o yaml | kubectl apply -f -
	kubectl rollout restart deployment/todo-api -n todo-app
	@echo ">>> Session prête."

session-end:
	@echo ">>> Destruction des ressources éphémères..."
	cd terraform/ephemeral && terraform destroy -auto-approve
	kubectl delete secret rds-secret -n todo-app --ignore-not-found
	@echo ">>> Ressources détruites. Coût session : ~$0.12"

infra-init:
	cd terraform/permanent && terraform init && terraform apply -auto-approve

cluster-up:
	minikube start --profile=todo-enterprise --cpus=6 --memory=10240 --disk-size=40g
	minikube addons enable ingress metrics-server dashboard registry storage-provisioner \
	  --profile=todo-enterprise

build-api:
	eval $$(minikube -p todo-enterprise docker-env) && \
	docker build -t todo-api:latest ./backend

deploy-api:
	helm upgrade --install todo-api ./helm/todo-api -n todo-app

dashboard:
	minikube dashboard --profile=todo-enterprise

grafana:
	kubectl port-forward svc/monitoring-grafana 3000:80 -n monitoring

perf:
	cd performance-tests && mvn gatling:test -Dgatling.simulationClass=CreateTaskSimulation

ecr-status:
	@echo ">>> Taille actuelle des repositories ECR :"
	@for repo in todo-api todo-angular todo-react; do \
	  echo "$$repo :"; \
	  aws ecr describe-images --repository-name $$repo \
	    --query 'sort_by(imageDetails, &imagePushedAt)[*].[imageTags[0],imageSizeInBytes,imagePushedAt]' \
	    --output table 2>/dev/null || echo "  (vide ou inexistant)"; \
	done
```

---

## 7b. FinOps — ECR Lifecycle Policies

> **FinOps** (Financial Operations) est la discipline qui consiste à optimiser les coûts cloud
> de manière continue et automatisée. Les ECR Lifecycle Policies en sont un exemple concret :
> une règle déclarative côté serveur AWS qui nettoie les images inutiles sans script externe.

### Pourquoi c'est critique

ECR est **gratuit jusqu'à 500 MB** par mois. Au-delà : **$0.10/GB/mois**.

Sans politique de nettoyage, les images s'accumulent rapidement :
- Chaque `docker push` sur un tag existant crée une image **sans tag** (dangling)
- Les branches `feature/*` génèrent des images temporaires jamais supprimées
- Un pipeline actif peut remplir 500 MB en quelques semaines

### Stratégie de 5 règles — rester dans le Free Tier

Les règles sont évaluées **dans l'ordre de priorité** (du plus petit au plus grand). La première règle qui correspond à une image s'applique.

```
Règle 1 (priorité 1) : Images sans tag        → supprimer après 1 jour
Règle 2 (priorité 2) : Images de branches     → supprimer après 7 jours
Règle 3 (priorité 3) : Images de snapshot     → garder les 5 dernières
Règle 4 (priorité 4) : Images de production   → garder les 3 dernières
Règle 5 (priorité 5) : Filet de sécurité      → supprimer tout ce qui a > 90 jours
```

### Terraform — `terraform/permanent/ecr.tf`

```hcl
# ─── Repositories ECR ───────────────────────────────────────────────────────

locals {
  ecr_repos = ["todo-api", "todo-angular", "todo-react"]
}

resource "aws_ecr_repository" "apps" {
  for_each             = toset(local.ecr_repos)
  name                 = each.key
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true   # scan de vulnérabilités à chaque push (gratuit)
  }

  tags = local.common_tags
}

# ─── Lifecycle Policy (appliquée à chaque repository) ───────────────────────

resource "aws_ecr_lifecycle_policy" "cleanup" {
  for_each   = aws_ecr_repository.apps
  repository = each.value.name

  policy = jsonencode({
    rules = [

      # RÈGLE 1 — Images sans tag (dangling)
      # Créées à chaque push sur un tag existant.
      # Aucune valeur — supprimer dès le lendemain.
      {
        rulePriority = 1
        description  = "Supprimer les images sans tag après 1 jour"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 1
        }
        action = { type = "expire" }
      },

      # RÈGLE 2 — Images de branches feature / fix / PR
      # Générées par le pipeline CI pour les branches courtes.
      # Inutiles après la fusion de la branche.
      {
        rulePriority = 2
        description  = "Supprimer les images de branches après 7 jours"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["feature-", "fix-", "hotfix-", "pr-", "dev-"]
          countType     = "sinceImagePushed"
          countUnit     = "days"
          countNumber   = 7
        }
        action = { type = "expire" }
      },

      # RÈGLE 3 — Images de snapshot / développement
      # Garder les 5 dernières pour le debug récent.
      {
        rulePriority = 3
        description  = "Garder les 5 dernières images snapshot/dev"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["snapshot-", "test-", "ci-"]
          countType     = "imageCountMoreThan"
          countNumber   = 5
        }
        action = { type = "expire" }
      },

      # RÈGLE 4 — Images de production (v*, latest, stable, main)
      # Garder les 3 dernières = rollback N-1 et N-2 possible.
      {
        rulePriority = 4
        description  = "Garder les 3 dernières images de production"
        selection = {
          tagStatus     = "tagged"
          tagPrefixList = ["v", "latest", "stable", "main", "release-"]
          countType     = "imageCountMoreThan"
          countNumber   = 3
        }
        action = { type = "expire" }
      },

      # RÈGLE 5 — Filet de sécurité (catch-all)
      # Supprime tout ce qui a plus de 90 jours, quelle que soit la règle.
      # Évite les images oubliées qui s'accumulent sur le long terme.
      {
        rulePriority = 5
        description  = "Supprimer toute image de plus de 90 jours (catch-all)"
        selection = {
          tagStatus   = "any"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 90
        }
        action = { type = "expire" }
      }

    ]
  })
}
```

### Résultat attendu sur le stockage ECR

| Catégorie d'image | Politique | Stockage estimé |
|---|---|---|
| Images sans tag (dangling) | Supprimées après 1 jour | ~0 MB |
| Images `feature-*` | Supprimées après 7 jours | ~0 MB (hors branches actives) |
| Images `snapshot-*` | 5 dernières conservées | ~500 MB max |
| Images `v*` / `latest` | 3 dernières conservées | ~600–900 MB |
| **Total** | Sous contrôle automatique | **< 500 MB → Free Tier** |

> La politique est **serverless et automatique** : aucun script cron, aucune Lambda dédiée,
> aucun accès manuel. AWS évalue les règles une fois par jour côté serveur.

### Convention de tagging des images dans le pipeline GitHub Actions

Pour que les règles ECR fonctionnent, les images doivent être taguées de manière cohérente :

```yaml
# .github/workflows/ci.yml — conventions de tags

- name: Build et push avec tags ECR
  env:
    ECR_URL: ${{ steps.login-ecr.outputs.registry }}
  run: |
    # Tag de production (déclenché sur main)
    docker build -t $ECR_URL/todo-api:v${{ github.run_number }} .
    docker build -t $ECR_URL/todo-api:latest .

    # Tag de branche feature (déclenché sur feature/*)
    docker build -t $ECR_URL/todo-api:feature-${{ github.head_ref }} .

    # Tag de snapshot (déclenché sur tous les commits)
    docker build -t $ECR_URL/todo-api:snapshot-${{ github.sha }} .
```

### Vérification de l'efficacité des politiques

```bash
# Voir les images restantes et leur taille dans un repository
aws ecr describe-images \
  --repository-name todo-api \
  --query 'sort_by(imageDetails, &imagePushedAt)[*].{
    Tag:imageTags[0],
    SizeMB:to_string(imageSizeInBytes / 1048576),
    PushedAt:imagePushedAt
  }' \
  --output table

# Taille totale utilisée dans tous les repositories ECR
aws ecr describe-repositories \
  --query 'repositories[*].repositoryName' \
  --output text | tr '\t' '\n' | while read repo; do
    size=$(aws ecr describe-images --repository-name "$repo" \
      --query 'sum(imageDetails[*].imageSizeInBytes)' --output text 2>/dev/null || echo 0)
    echo "$repo : $(echo "scale=1; $size/1048576" | bc) MB"
  done
```

---

## 8. AWS Lambda — 3 use cases pédagogiques

### Lambda 1 — Workflow pièces jointes (Step Functions)

```
Upload fichier → S3 bucket todo-attachments
  → Step Functions démarre la machine d'état :
      État 1 : ValidateMime (Lambda)
          → MIME invalide → état Échec → SES email erreur
      État 2 : GenerateThumbnail (Lambda, si image)
      État 3 : WriteMetadata (Lambda)
          → Écrit dans RDS (nom, taille, type, taskId)
          → Publie dans SNS → fan-out vers SQS + SES
      État 4 : Succès → notification Spring Boot via SQS
```

**Valeur entretien :** Orchestration vs chorégraphie, retry par étape, visibilité de l'exécution dans la console Step Functions.

---

### Lambda 2 — TaskArchiver (EventBridge Scheduler)

```
02h00 chaque nuit → EventBridge → Lambda TaskArchiver
  → Interroge RDS : tâches complétées depuis > 30 jours
  → Sérialise en JSON partitionné par date (S3/archives/year=2026/month=05/)
  → Supprime de RDS
  → CloudWatch : publie métrique custom "tasks_archived_count"
  → Athena peut ensuite requêter les archives avec du SQL
```

**Valeur entretien :** Traitement planifié serverless, partitionnement S3 pour Athena, métriques custom CloudWatch.

---

### Lambda 3 — ReportingApi (API Gateway + SnapStart + X-Ray)

```
GET /reports/summary → API Gateway
  → Lambda ReportingApi (SnapStart activé, X-Ray tracé)
      → Parameter Store : récupère l'endpoint RDS
      → RDS : agrégations SQL (tasks par user, par priorité)
      → DynamoDB : préférences de l'utilisateur (format du rapport)
      → X-Ray : sous-segments pour RDS et DynamoDB
      → Retourne JSON
```

**Valeur entretien :** SnapStart Java 21, distributed tracing X-Ray, Parameter Store, DynamoDB.

| Runtime | Cold start sans SnapStart | Avec SnapStart |
|---|---|---|
| Java 21 | 3 000 – 5 000 ms | ~200 ms |
| Node.js 20 | 100 – 300 ms | N/A |
| Python 3.12 | 100 – 400 ms | N/A |

---

## 9. Estimation des coûts AWS

### Coût par session (2 heures)

| Service | Calcul | An 1 (Free Tier) | An 2+ |
|---|---|---|---|
| RDS PostgreSQL db.t3.micro | 2h × $0.017 | **$0.00** | $0.034 |
| NAT Gateway | 2h × $0.045 | $0.09 | $0.09 |
| Lambda + Step Functions + API GW | Free Tier | $0.00 | $0.00 |
| SES, SNS, SQS, DynamoDB | Free Tier | $0.00 | $0.00 |
| CloudWatch, X-Ray | Free Tier | $0.00 | $0.00 |
| S3 + CloudFront | Fixe mensuel | ~$0.002 | ~$0.002 |
| **Total / session** | | **~$0.09** | **~$0.13** |

### Coût mensuel selon la fréquence

| Fréquence | Sessions/mois | Coût an 1 | Coût an 2+ |
|---|---|---|---|
| 2 sessions / semaine | 8 | **~$0.75** | **~$1.05** |
| 4 sessions / semaine | 16 | **~$1.50** | **~$2.10** |
| Session quotidienne | 30 | **~$2.75** | **~$3.90** |
| Oubli `terraform destroy` (1 nuit) | — | +$0.34 max | +$0.34 max |

> **En résumé : moins de $4/mois pour une stack AWS complète avec 13 services.**

---

## 10. Suivi des coûts AWS en temps réel

### Vue d'ensemble des outils disponibles

| Outil | Quand l'utiliser | Granularité | Coût |
|---|---|---|---|
| **Billing Dashboard** | Vue mensuelle rapide | Jour | Gratuit |
| **Cost Explorer** | Analyse par service / tag / date | Heure | Gratuit |
| **AWS Budgets** | Alerte automatique si seuil dépassé | Journalier | Gratuit (2 budgets) |
| **CloudWatch Billing Alarm** | Alerte instantanée sur facture estimée | Temps réel | Gratuit |
| **Cost Anomaly Detection** | Détection ML d'une dépense anormale | Temps réel | Gratuit |
| **Cost and Usage Report (CUR)** | Analyse fine (Athena/QuickSight) | Heure | Gratuit (stockage S3) |

---

### Étape 1 — Activer Cost Explorer

Cost Explorer doit être activé **une seule fois** — les données des 12 derniers mois sont disponibles après 24h.

```
Console AWS → Billing and Cost Management
  → Cost Explorer → Activer Cost Explorer
```

Ou via CLI :
```bash
aws ce enable-cost-explorer
```

---

### Étape 2 — AWS Budgets (2 budgets gratuits)

> **Free Tier AWS Budgets :** les 2 premiers budgets sont **toujours gratuits**.
> Au-delà : $0.02/budget/jour. Pour ce projet, 2 budgets suffisent — on reste à $0.

On crée **2 budgets complémentaires** :

#### Budget 1 — Coût mensuel global ($10/mois)

Alerte à 50%, 80% (dépenses réelles) et à la prévision de dépassement.

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

aws budgets create-budget \
  --account-id $ACCOUNT_ID \
  --budget '{
    "BudgetName": "todo-cout-mensuel",
    "BudgetLimit": {"Amount": "10", "Unit": "USD"},
    "TimeUnit": "MONTHLY",
    "BudgetType": "COST"
  }' \
  --notifications-with-subscribers '[
    {
      "Notification": {
        "NotificationType": "ACTUAL",
        "ComparisonOperator": "GREATER_THAN",
        "Threshold": 50,
        "ThresholdType": "PERCENTAGE"
      },
      "Subscribers": [{"SubscriptionType": "EMAIL", "Address": "YOUR_EMAIL@example.com"}]
    },
    {
      "Notification": {
        "NotificationType": "ACTUAL",
        "ComparisonOperator": "GREATER_THAN",
        "Threshold": 80,
        "ThresholdType": "PERCENTAGE"
      },
      "Subscribers": [{"SubscriptionType": "EMAIL", "Address": "YOUR_EMAIL@example.com"}]
    },
    {
      "Notification": {
        "NotificationType": "FORECASTED",
        "ComparisonOperator": "GREATER_THAN",
        "Threshold": 100,
        "ThresholdType": "PERCENTAGE"
      },
      "Subscribers": [{"SubscriptionType": "EMAIL", "Address": "YOUR_EMAIL@example.com"}]
    }
  ]'
```

Tu reçois un email à **$5** (50%), **$8** (80%) et si la facture est prévue de **dépasser $10**.

#### Budget 2 — Usage RDS (heures d'instance) — le budget anti-oubli

Ce budget surveille le **nombre d'heures RDS consommées** dans le mois.
Si RDS tourne plus de **4 heures** (seuil = 2 sessions de 2h), tu reçois une alerte.
C'est le filet de sécurité si tu oublies un `terraform destroy`.

```bash
aws budgets create-budget \
  --account-id $ACCOUNT_ID \
  --budget '{
    "BudgetName": "todo-rds-heures",
    "BudgetLimit": {"Amount": "4", "Unit": "Hours"},
    "TimeUnit": "MONTHLY",
    "BudgetType": "USAGE",
    "CostFilters": {
      "UsageTypeGroup": ["RDS:Running Hours"]
    }
  }' \
  --notifications-with-subscribers '[
    {
      "Notification": {
        "NotificationType": "ACTUAL",
        "ComparisonOperator": "GREATER_THAN",
        "Threshold": 100,
        "ThresholdType": "PERCENTAGE"
      },
      "Subscribers": [{"SubscriptionType": "EMAIL", "Address": "YOUR_EMAIL@example.com"}]
    }
  ]'
```

> **Pourquoi ce budget est stratégique :**
> Si tu fais une session de 2h et oublies le `terraform destroy`, RDS continue de tourner.
> Dès que les 4 heures sont dépassées ce mois-ci, tu reçois un email — avant même que
> Cost Explorer le détecte, et bien avant la fin du mois.

#### Vérifier les budgets créés

```bash
# Lister tous les budgets et leur consommation actuelle
aws budgets describe-budgets \
  --account-id $ACCOUNT_ID \
  --query 'Budgets[*].{
    Nom:BudgetName,
    Limite:BudgetLimit.Amount,
    Unite:BudgetLimit.Unit,
    Actuel:CalculatedSpend.ActualSpend.Amount
  }' \
  --output table
```

---

### Étape 3 — Alarme CloudWatch sur la facture estimée

> ⚠️ Cette alarme doit être créée dans la région **us-east-1** (c'est la seule région où AWS publie les métriques de facturation).

```bash
aws cloudwatch put-metric-alarm \
  --region us-east-1 \
  --alarm-name "todo-billing-alert-5usd" \
  --alarm-description "Alerte si la facture AWS dépasse $5" \
  --metric-name EstimatedCharges \
  --namespace AWS/Billing \
  --statistic Maximum \
  --dimensions Name=Currency,Value=USD \
  --period 86400 \
  --evaluation-periods 1 \
  --threshold 5 \
  --comparison-operator GreaterThanThreshold \
  --alarm-actions arn:aws:sns:us-east-1:ACCOUNT_ID:todo-billing-sns \
  --treat-missing-data notBreaching
```

> Pour utiliser cette alarme, créer d'abord un topic SNS dans us-east-1 et s'y abonner par email.

---

### Étape 4 — Cost Anomaly Detection (ML automatique)

AWS analyse tes dépenses avec du Machine Learning et t'alerte si une dépense sort de l'ordinaire — même si elle ne dépasse pas ton budget.

```bash
# Créer un moniteur de détection d'anomalie
aws ce create-anomaly-monitor \
  --anomaly-monitor '{
    "MonitorName": "todo-anomaly-monitor",
    "MonitorType": "DIMENSIONAL",
    "MonitorDimension": "SERVICE"
  }'

# Créer un abonnement aux alertes (seuil : $2 d'anomalie)
aws ce create-anomaly-subscription \
  --anomaly-subscription '{
    "SubscriptionName": "todo-anomaly-alert",
    "MonitorArnList": ["ARN_DU_MONITEUR_CI-DESSUS"],
    "Subscribers": [{"Address": "YOUR_EMAIL@example.com", "Type": "EMAIL"}],
    "Threshold": 2,
    "Frequency": "IMMEDIATE"
  }'
```

---

### Étape 5 — Taguer toutes les ressources pour l'analyse par projet

Toutes les ressources Terraform doivent avoir des tags cohérents pour filtrer les coûts dans Cost Explorer.

```hcl
# terraform/permanent/variables.tf
locals {
  common_tags = {
    Project     = "todo-enterprise"
    Environment = "learning"
    Owner       = "YOUR_GITHUB_USERNAME"
    ManagedBy   = "terraform"
  }
}

# Appliquer à chaque ressource
resource "aws_s3_bucket" "frontend" {
  bucket = "todo-enterprise-frontend"
  tags   = local.common_tags
}
```

Dans Cost Explorer : **Filter by Tag → Project → todo-enterprise** pour voir uniquement les coûts de ce projet.

---

### Étape 6 — Activer Cost Allocation Tags

Sans cette activation, les tags n'apparaissent pas dans Cost Explorer.

```
Console AWS → Billing → Cost Allocation Tags
  → Chercher le tag "Project"
  → Activer → Confirm
```

> Les tags deviennent visibles dans Cost Explorer après **24 heures**.

---

### Étape 7 — Vérification rapide en CLI après chaque session

```bash
# Coût du mois en cours par service
aws ce get-cost-and-usage \
  --time-period Start=$(date +%Y-%m-01),End=$(date +%Y-%m-%d) \
  --granularity MONTHLY \
  --metrics BlendedCost \
  --group-by Type=DIMENSION,Key=SERVICE \
  --query 'ResultsByTime[0].Groups[?Metrics.BlendedCost.Amount>`0`].[Keys[0],Metrics.BlendedCost.Amount]' \
  --output table

# Vérifier qu'aucune ressource éphémère n'est en vie
aws rds describe-db-instances \
  --query 'DBInstances[?TagList[?Key==`Environment`&&Value==`ephemeral`]].DBInstanceIdentifier' \
  --output text
```

---

### Résumé du dispositif de surveillance — tout gratuit

```
AWS Budgets (2 budgets — FREE)
  ├── Budget 1 : Coût $10/mois
  │     ├── 50% → email à $5
  │     ├── 80% → email à $8
  │     └── Prévision 100% → email d'alerte anticipée
  └── Budget 2 : Usage RDS > 4h/mois
        └── Alerte immédiate si RDS tourne trop longtemps (oubli destroy)

CloudWatch Billing Alarm (FREE)
  └── Alerte si facture estimée > $5 (détection en temps réel, us-east-1)

Cost Anomaly Detection ML (FREE)
  └── Alerte si dépense anormale > $2 sur un service

Cost Explorer (FREE)
  └── Analyse filtrée par tag "todo-enterprise"

CLI post-session (0 coût)
  └── aws rds describe-db-instances → vérifie que RDS est bien détruit
```

**Couverture complète :** 4 mécanismes indépendants, zéro coût, déclenchés à des moments différents pour ne laisser passer aucun oubli.

---

## 11. Infrastructure Kubernetes — Namespaces & Services

### Séparation en namespaces

| Namespace | Contenu | Raison |
|---|---|---|
| `ingress-nginx` | NGINX Ingress Controller | Isolation réseau |
| `todo-app` | spring-api, react, angular, keycloak | Applications métier |
| `data` | PostgreSQL, MongoDB, Redis, Kafka | Stateful — lifecycle différent |
| `monitoring` | Prometheus, Grafana, Loki, Alertmanager | Observabilité |

### Types de ressources par service

| Service | Type K8s | Raison |
|---|---|---|
| Spring API | `Deployment` | Sans état, scalable |
| React / Angular | `Deployment` | Fichiers statiques |
| Keycloak | `Deployment` | Stateless si DB externe |
| PostgreSQL | `StatefulSet` + PVC | Identité stable, données persistantes |
| MongoDB | `StatefulSet` + PVC | Replica Set |
| Redis | `StatefulSet` + PVC | Persistence RDB/AOF |
| Kafka | `StatefulSet` + PVC | Partitions persistantes |

> **Point entretien :** Un `StatefulSet` garantit un nom de pod stable (`kafka-0`, `kafka-1`) et un PVC dédié par pod — indispensable pour Kafka qui doit savoir quel broker gère quelle partition au redémarrage.

---

## 12. Plan de déploiement Helm

```bash
# Namespaces
kubectl create namespace todo-app data monitoring

# Repos
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add grafana https://grafana.github.io/helm-charts
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Data Layer
helm install postgres  bitnami/postgresql -n data -f helm/values/postgres-values.yaml
helm install redis     bitnami/redis      -n data -f helm/values/redis-values.yaml
helm install mongo     bitnami/mongodb    -n data -f helm/values/mongo-values.yaml
helm install kafka     bitnami/kafka      -n data -f helm/values/kafka-values.yaml

# Auth
helm install keycloak  bitnami/keycloak   -n todo-app -f helm/values/keycloak-values.yaml

# Applications
helm install todo-api   ./helm/todo-api     -n todo-app
helm install todo-react ./helm/todo-react   -n todo-app
helm install todo-ng    ./helm/todo-angular -n todo-app

# Observabilité
helm install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring -f helm/values/monitoring-values.yaml
helm install loki grafana/loki-stack -n monitoring
```

---

## 13. Pré-requis & Installation

### Outils (Windows 11)

```powershell
winget install Kubernetes.minikube Kubernetes.kubectl Helm.Helm
winget install Hashicorp.Terraform Docker.DockerDesktop Amazon.AWSCLI

# Vérifications
minikube version   # >= 1.33
kubectl version    # >= 1.30
helm version       # >= 3.15
terraform version  # >= 1.8
aws --version      # >= 2.x
```

### Ressources machine recommandées

| Ressource | Minimum | Recommandé |
|---|---|---|
| CPU | 4 cores | 6 cores |
| RAM | 8 GB | 12 GB |
| Disque | 20 GB | 40 GB |

### Démarrage Minikube

```powershell
minikube start `
  --driver=docker --cpus=6 --memory=10240 --disk-size=40g `
  --kubernetes-version=v1.30.0 --profile=todo-enterprise

minikube addons enable ingress metrics-server dashboard registry storage-provisioner `
  --profile=todo-enterprise
```

### Configuration AWS CLI

```bash
aws configure
# Region : eu-west-3
# Output : json

# Test
aws sts get-caller-identity
```

### Fichier hosts Windows

```
# C:\Windows\System32\drivers\etc\hosts
192.168.49.2  todo.local keycloak.local grafana.local kibana.local
```

---

## 14. Commandes Minikube essentielles

```bash
minikube start/stop/delete/status --profile=todo-enterprise
minikube ip --profile=todo-enterprise
minikube dashboard --profile=todo-enterprise
minikube tunnel --profile=todo-enterprise

# Docker → Minikube daemon (PowerShell)
& minikube -p todo-enterprise docker-env | Invoke-Expression

# Kubernetes
kubectl logs -f deployment/todo-api -n todo-app
kubectl exec -it deployment/todo-api -n todo-app -- /bin/sh
kubectl get all -n todo-app
kubectl get all -n data
kubectl get pv,pvc -n data
kubectl port-forward svc/todo-api 8080:8080 -n todo-app
kubectl port-forward svc/monitoring-grafana 3000:80 -n monitoring

# Helm
helm list -A
helm get values todo-api -n todo-app
helm rollback todo-api 1 -n todo-app
```

---

## 15. Pipeline DevSecOps — GitHub Actions

### Phases du pipeline principal (`.github/workflows/ci.yml`)

```
┌─────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│  BUILD  │→ │   TEST   │→ │ ANALYSE  │→ │ PACKAGE  │→ │  DEPLOY  │→ │ GATLING  │
│ Maven   │  │ JUnit 5  │  │ SonarCl. │  │ Docker   │  │ Helm     │  │ load     │
│ Angular │  │ JaCoCo   │  │ OWASP    │  │ push ECR │  │ Lambda   │  │ stress   │
│ React   │  │ Jest     │  │ Trivy    │  │ S3 sync  │  │ deploy   │  │ rapports │
│         │  │ Cypress  │  │ tfsec    │  │          │  │ Terraform│  │ HTML     │
└─────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘
```

### Workflow complet — `.github/workflows/ci.yml`

```yaml
name: CI/CD Pipeline — To-Do Enterprise

on:
  push:
    branches: [main, 'feature/**', 'fix/**']
  pull_request:
    branches: [main]

# Permissions minimales — principe du moindre privilège
permissions:
  contents: read
  id-token: write      # requis pour OIDC AWS (zéro clé stockée)
  security-events: write  # requis pour upload SARIF (CodeQL, Trivy)

env:
  AWS_REGION: eu-west-3
  ECR_REGISTRY: ${{ secrets.AWS_ACCOUNT_ID }}.dkr.ecr.eu-west-3.amazonaws.com
  JAVA_VERSION: '21'
  NODE_VERSION: '20'

jobs:

  # ─── JOB 1 : BUILD ────────────────────────────────────────────────────────
  build:
    name: Build
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Java 21
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: temurin
          cache: maven

      - name: Build Maven (skip tests)
        run: mvn -B package -DskipTests --file backend/pom.xml

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: ${{ env.NODE_VERSION }}
          cache: npm
          cache-dependency-path: frontend-angular/package-lock.json

      - name: Build Angular 20
        working-directory: frontend-angular
        run: npm ci && npm run build --configuration=production

      - name: Build React 19
        working-directory: frontend-react
        run: npm ci && npm run build

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: build-artifacts
          path: |
            backend/infrastructure/target/*.jar
            frontend-angular/dist/
            frontend-react/dist/
          retention-days: 1

  # ─── JOB 2 : TEST ─────────────────────────────────────────────────────────
  test:
    name: Tests
    runs-on: ubuntu-latest
    needs: build

    services:
      postgres:
        image: postgres:16-alpine
        env:
          POSTGRES_DB: tododb_test
          POSTGRES_PASSWORD: test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
      redis:
        image: redis:7-alpine
        options: >-
          --health-cmd "redis-cli ping"
          --health-interval 10s

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: temurin
          cache: maven

      - name: Tests unitaires + intégration + JaCoCo
        run: mvn -B verify --file backend/pom.xml
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/tododb_test
          SPRING_DATASOURCE_PASSWORD: test
          SPRING_REDIS_HOST: localhost

      - name: Upload rapport JaCoCo
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: backend/infrastructure/target/site/jacoco/

      - name: Tests Jest (React)
        working-directory: frontend-react
        run: npm ci && npm test -- --coverage --watchAll=false

      - name: Tests Karma (Angular)
        working-directory: frontend-angular
        run: npm ci && npm test -- --watch=false --browsers=ChromeHeadless

  # ─── JOB 3 : ANALYSE SÉCURITÉ ─────────────────────────────────────────────
  analyse:
    name: Analyse sécurité
    runs-on: ubuntu-latest
    needs: test

    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0   # requis pour SonarCloud (historique complet)

      - uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: temurin
          cache: maven

      # SonarCloud (gratuit pour repos publics — remplace SonarQube self-hosted)
      - name: Analyse SonarCloud + JaCoCo
        uses: sonarsource/sonarcloud-github-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        with:
          args: >
            -Dsonar.projectKey=todo-enterprise
            -Dsonar.coverage.jacoco.xmlReportPaths=backend/infrastructure/target/site/jacoco/jacoco.xml
            -Dsonar.qualitygate.wait=true

      # OWASP Dependency Check
      - name: OWASP Dependency Check
        uses: dependency-check/Dependency-Check_Action@main
        with:
          project: 'todo-enterprise'
          path: 'backend'
          format: 'SARIF'
          args: --failOnCVSS 7

      - name: Upload OWASP SARIF
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: reports/dependency-check-report.sarif

      # Scan IaC Terraform
      - name: tfsec — scan Terraform
        uses: aquasecurity/tfsec-action@v1
        with:
          working_directory: terraform/
          soft_fail: false

  # ─── JOB 4 : PACKAGE DOCKER + ECR ─────────────────────────────────────────
  package:
    name: Package Docker → ECR
    runs-on: ubuntu-latest
    needs: analyse
    if: github.ref == 'refs/heads/main' || startsWith(github.ref, 'refs/heads/feature/')

    steps:
      - uses: actions/checkout@v4
      - uses: actions/download-artifact@v4
        with:
          name: build-artifacts

      # Authentification AWS via OIDC (zéro clé AWS stockée dans GitHub)
      - name: Configure AWS credentials (OIDC)
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::${{ secrets.AWS_ACCOUNT_ID }}:role/github-actions-ecr-role
          aws-region: ${{ env.AWS_REGION }}

      - name: Login ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v2

      - name: Build + Push images Docker avec tags ECR
        env:
          REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          SHA: ${{ github.sha }}
          RUN: ${{ github.run_number }}
          BRANCH: ${{ github.head_ref || github.ref_name }}
        run: |
          for service in todo-api todo-angular todo-react; do
            DIR=$([ "$service" = "todo-api" ] && echo "backend" || echo "frontend-${service#todo-}")
            # Tag production (main uniquement)
            if [ "${{ github.ref }}" = "refs/heads/main" ]; then
              docker build -t $REGISTRY/$service:v$RUN -t $REGISTRY/$service:latest $DIR
              docker push $REGISTRY/$service:v$RUN
              docker push $REGISTRY/$service:latest
            else
              # Tag feature branch
              SLUG=$(echo "$BRANCH" | sed 's/[^a-zA-Z0-9]/-/g' | cut -c1-50)
              docker build -t $REGISTRY/$service:feature-$SLUG $DIR
              docker push $REGISTRY/$service:feature-$SLUG
            fi
            # Tag snapshot sur tous les commits
            docker build -t $REGISTRY/$service:snapshot-${SHA:0:7} $DIR
            docker push $REGISTRY/$service:snapshot-${SHA:0:7}
          done

      # Scan Trivy de l'image Spring Boot après push ECR
      - name: Trivy scan image todo-api
        uses: aquasecurity/trivy-action@0.20.0
        with:
          image-ref: ${{ env.ECR_REGISTRY }}/todo-api:latest
          format: sarif
          output: trivy-results.sarif
          severity: CRITICAL,HIGH
          exit-code: 1   # bloque le pipeline si CRITICAL trouvé

      - name: Upload Trivy SARIF
        uses: github/codeql-action/upload-sarif@v3
        with:
          sarif_file: trivy-results.sarif

  # ─── JOB 5 : DEPLOY ───────────────────────────────────────────────────────
  deploy:
    name: Deploy
    runs-on: ubuntu-latest
    needs: package
    if: github.ref == 'refs/heads/main'

    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS credentials (OIDC)
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::${{ secrets.AWS_ACCOUNT_ID }}:role/github-actions-deploy-role
          aws-region: ${{ env.AWS_REGION }}

      # Sync frontends vers S3
      - uses: actions/download-artifact@v4
        with:
          name: build-artifacts

      - name: Deploy Angular → S3 + invalidation CloudFront
        run: |
          aws s3 sync frontend-angular/dist/ s3://todo-enterprise-frontend/angular/ \
            --delete --cache-control "public, max-age=31536000, immutable"
          aws cloudfront create-invalidation \
            --distribution-id ${{ secrets.CF_DISTRIBUTION_ID }} \
            --paths "/angular/*"

      - name: Deploy React → S3 + invalidation CloudFront
        run: |
          aws s3 sync frontend-react/dist/ s3://todo-enterprise-frontend/react/ \
            --delete --cache-control "public, max-age=31536000, immutable"
          aws cloudfront create-invalidation \
            --distribution-id ${{ secrets.CF_DISTRIBUTION_ID }} \
            --paths "/react/*"

      # Deploy Lambda functions
      - name: Setup Terraform
        uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: '1.8.0'

      - name: Deploy Lambdas via Terraform
        working-directory: terraform/permanent
        run: |
          terraform init
          terraform apply -auto-approve \
            -target=aws_lambda_function.attachment_processor \
            -target=aws_lambda_function.task_archiver \
            -target=aws_lambda_function.reporting_api

      # Helm upgrade Spring Boot sur Minikube (self-hosted runner requis en prod)
      # En dev : déclenché manuellement via make deploy-api
```

### Workflow Gatling — `.github/workflows/performance.yml`

```yaml
name: Tests de performance Gatling

on:
  workflow_dispatch:          # déclenchement manuel
    inputs:
      simulation:
        description: 'Simulation à exécuter'
        required: true
        default: 'CreateTaskSimulation'
        type: choice
        options:
          - CreateTaskSimulation
          - StressTaskSimulation
          - SoakTaskSimulation
          - SpikeTaskSimulation
  schedule:
    - cron: '0 6 * * 1'      # tous les lundis matin — soak test automatique

jobs:
  gatling:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: temurin
          cache: maven

      - name: Lancer simulation Gatling
        working-directory: performance-tests
        run: |
          mvn gatling:test \
            -Dgatling.simulationClass=${{ github.event.inputs.simulation || 'SoakTaskSimulation' }} \
            -Dgatling.baseUrl=${{ secrets.API_BASE_URL }}

      - name: Upload rapport HTML Gatling
        uses: actions/upload-artifact@v4
        with:
          name: gatling-report-${{ github.run_number }}
          path: performance-tests/target/gatling/
          retention-days: 30
```

### Workflow `terraform-destroy` — `.github/workflows/terraform-destroy.yml`

```yaml
name: Terraform Destroy — Ressources éphémères

on:
  workflow_dispatch:     # déclenchement manuel en fin de session

permissions:
  id-token: write
  contents: read

jobs:
  destroy:
    runs-on: ubuntu-latest
    environment: ephemeral   # protection : confirmation requise dans GitHub

    steps:
      - uses: actions/checkout@v4

      - name: Configure AWS credentials (OIDC)
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::${{ secrets.AWS_ACCOUNT_ID }}:role/github-actions-deploy-role
          aws-region: eu-west-3

      - uses: hashicorp/setup-terraform@v3
        with:
          terraform_version: '1.8.0'

      - name: Terraform destroy — RDS + NAT Gateway
        working-directory: terraform/ephemeral
        run: |
          terraform init
          terraform destroy -auto-approve
```

### Authentification AWS via OIDC — le point clé de sécurité

> GitHub Actions s'authentifie à AWS **sans stocker de clé AWS** dans les secrets GitHub.
> GitHub prouve son identité à AWS via un token OIDC éphémère.

```hcl
# terraform/permanent/iam.tf — IAM Role pour GitHub Actions

resource "aws_iam_role" "github_actions_deploy" {
  name = "github-actions-deploy-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = {
        Federated = "arn:aws:iam::${var.account_id}:oidc-provider/token.actions.githubusercontent.com"
      }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringLike = {
          # Restreint au repo GitHub spécifique — sécurité renforcée
          "token.actions.githubusercontent.com:sub" = "repo:TON_USER/todo-enterprise:*"
        }
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  role = aws_iam_role.github_actions_deploy.id
  policy = jsonencode({
    Statement = [
      { Effect = "Allow", Action = ["s3:PutObject", "s3:DeleteObject", "s3:GetObject"],
        Resource = "${aws_s3_bucket.frontend.arn}/*" },
      { Effect = "Allow", Action = ["ecr:GetAuthorizationToken"], Resource = "*" },
      { Effect = "Allow", Action = ["ecr:BatchCheckLayerAvailability", "ecr:PutImage",
        "ecr:InitiateLayerUpload", "ecr:UploadLayerPart", "ecr:CompleteLayerUpload"],
        Resource = "arn:aws:ecr:eu-west-3:${var.account_id}:repository/todo-*" },
      { Effect = "Allow", Action = ["lambda:UpdateFunctionCode", "lambda:PublishVersion"],
        Resource = "arn:aws:lambda:eu-west-3:${var.account_id}:function:todo-*" },
      { Effect = "Allow", Action = ["cloudfront:CreateInvalidation"],
        Resource = "*" }
    ]
  })
}
```

### Quality Gates (bloquants)

| Métrique | Seuil | Outil |
|---|---|---|
| Couverture JaCoCo | ≥ 80% | SonarCloud Quality Gate |
| Code Smells | 0 bloquants | SonarCloud |
| Vulnérabilités dépendances | 0 CVSS ≥ 7 | OWASP Dependency Check |
| Vulnérabilités image Docker | 0 CRITICAL | Trivy |
| Vulnérabilités IaC Terraform | 0 HIGH | tfsec |
| P95 Gatling | ≤ 500 ms | Rapport Gatling HTML |
| Taux d'erreur Gatling | ≤ 1% | Rapport Gatling HTML |

### Secrets GitHub à configurer (Settings → Secrets and variables → Actions)

| Secret | Valeur |
|---|---|
| `AWS_ACCOUNT_ID` | ID de ton compte AWS |
| `SONAR_TOKEN` | Token SonarCloud (gratuit sur sonarcloud.io) |
| `CF_DISTRIBUTION_ID` | ID de ta distribution CloudFront |
| `API_BASE_URL` | URL de l'API pour les tests Gatling |

---

## 16. Roadmap du projet — étapes par étapes

### Phase 1 — Environnement local (Semaine 1)
- [ ] Installer Minikube, kubectl, Helm, Terraform, AWS CLI
- [ ] Démarrer le cluster `todo-enterprise`
- [ ] Déployer le Data Layer via Helm (PostgreSQL, Redis, MongoDB, Kafka)
- [ ] Vérifier la connectivité inter-services

### Phase 2 — Domaine Java 21 (Semaine 2)
- [ ] Maven multi-module : `domain`, `application`, `infrastructure`
- [ ] Entités `Task`, `User`, `Priority` en Records Java 21
- [ ] Ports entrants et sortants (y compris `FileStoragePort`, `NotificationPort`, `UserPreferencePort`)
- [ ] `TaskDomainService` en TDD — objectif 100% couverture sur `domain`

### Phase 3 — Infrastructure & API REST (Semaine 3)
- [ ] Adaptateur JPA PostgreSQL local
- [ ] Adaptateur Kafka (EventPublisher)
- [ ] Adaptateur Redis (cache)
- [ ] API REST Spring MVC
- [ ] Spring Security OIDC (Keycloak local)

### Phase 4 — Frontends (Semaine 4-5)
- [ ] Angular 20 Zoneless — `input()`, `output()`, `resource()`, `@defer`
- [ ] React 19 + Zustand
- [ ] Intégration OIDC (Keycloak redirect)
- [ ] Docker multi-stage des deux frontends

### Phase 5 — Kubernetes & Helm (Semaine 6)
- [ ] Charts Helm pour `todo-api`, `todo-react`, `todo-angular`
- [ ] Ingress NGINX, ConfigMaps, Secrets
- [ ] Rolling update sans downtime

### Phase 6 — Observabilité locale (Semaine 7)
- [ ] kube-prometheus-stack (Prometheus + Grafana)
- [ ] Loki pour les logs
- [ ] Dashboard Grafana custom (latence API, taux d'erreur, heap JVM)
- [ ] Alertes Alertmanager

### Phase 7 — Terraform permanent (Semaine 8)
- [ ] Module `permanent/` : S3, CloudFront, ECR, IAM, VPC, SQS, SNS, SES
- [ ] **ECR Lifecycle Policies** : 5 règles FinOps (dangling 1j, feature 7j, snapshot ×5, prod ×3, catch-all 90j)
- [ ] DynamoDB (table `user-preferences`)
- [ ] Parameter Store (endpoints, config applicative)
- [ ] Déployer les frontends sur S3/CloudFront
- [ ] Pousser les images Docker vers ECR avec les conventions de tags (`v*`, `feature-*`, `snapshot-*`)
- [ ] Vérifier l'espace ECR avec `make ecr-status` — objectif < 500 MB (Free Tier)

### Phase 8 — AWS Lambda + Step Functions (Semaine 9)
- [ ] `ValidateMime` + `GenerateThumbnail` + `WriteMetadata` (Step Functions)
- [ ] `TaskArchiver` (EventBridge) → archive S3 + métrique CloudWatch
- [ ] `ReportingApi` (API Gateway + SnapStart + X-Ray)
- [ ] Adaptateurs Spring : `SesNotificationAdapter`, `DynamoUserPreferenceAdapter`
- [ ] Consumer SQS dans Spring Boot

### Phase 9 — Cognito + CloudWatch + X-Ray (Semaine 10)
- [ ] Cognito User Pool + App Client + Hosted UI
- [ ] Configurer Spring Security OIDC avec Cognito (profil `aws`)
- [ ] Dashboards CloudWatch (métriques Lambda, RDS, Step Functions)
- [ ] Tracer les requêtes API Gateway → Lambda → RDS avec X-Ray
- [ ] Activer X-Ray dans les Lambdas (un flag Terraform)

### Phase 10 — Exposition API + RDS + Terraform éphémère (Semaine 11)
- [ ] Module `ephemeral/` : App Runner + RDS PostgreSQL + NAT Gateway
- [ ] Terraform CloudFront multi-origines : S3 `/angular/*`, `/react/*` + App Runner `/api/*` + API Gateway `/reports/*`
- [ ] Configurer CORS Spring Boot via Parameter Store (local vs AWS)
- [ ] `make session-start` : provisionnement + mise à jour CloudFront origin App Runner
- [ ] `make session-end` : `terraform destroy` propre (App Runner + RDS + NAT)
- [ ] Budget Alert + CloudWatch Billing Alarm + Cost Anomaly Detection
- [ ] Valider le flux complet : CloudFront → App Runner → RDS (profil `aws`)
- [ ] Vérifier que le frontend CloudFront appelle `/api/*` sans erreur CORS

### Phase 11 — Athena + Archives (Semaine 12)
- [ ] Tester `TaskArchiver` Lambda : vérifier les fichiers JSON partitionnés dans S3
- [ ] Créer la table Athena sur les archives S3
- [ ] Requêter les archives avec SQL (tasks complétées par mois, par user)
- [ ] Créer un bucket S3 + Glue Data Catalog via Terraform

### Phase 12 — CI/CD & Qualité (Semaine 13)
- [ ] Créer le repo GitHub public — portfolio visible pour les recruteurs
- [ ] Configurer l'OIDC AWS → GitHub Actions (IAM Role via Terraform, zéro clé stockée)
- [ ] Workflow `ci.yml` : 5 jobs (build → test → analyse → package → deploy)
- [ ] SonarCloud (gratuit pour repo public) + JaCoCo
- [ ] OWASP Dependency-Check + Trivy + tfsec
- [ ] Workflow `performance.yml` : déclenchement manuel + schedule hebdomadaire
- [ ] Workflow `terraform-destroy.yml` : destruction éphémère avec protection d'environnement
- [ ] Configurer les secrets GitHub (`AWS_ACCOUNT_ID`, `SONAR_TOKEN`, `CF_DISTRIBUTION_ID`)
- [ ] Vérifier le badge CI vert sur le README GitHub
- [ ] Atteindre tous les seuils Quality Gate

### Phase 13 — Gatling (Semaine 14)
- [ ] `CreateTaskSimulation` : 100 users, 10 min
- [ ] `StressTaskSimulation` : montée progressive
- [ ] `SoakTaskSimulation` : 50 users, 2h (fuites mémoire)
- [ ] Rapports HTML archivés en artefacts CI
- [ ] Visualiser la JVM pendant les tests via Grafana

### Phase 14 — Spring Batch & Event-Driven avancé (Semaine 15)
- [ ] Job Spring Batch (alternative Lambda Archiver pour volumes importants)
- [ ] Consumer SQS Spring Boot (réception events Lambda)
- [ ] Event Store MongoDB (historique des changements d'état)

---

## 17. Comparatif Minikube vs EKS

| Dimension | Minikube (local) | EKS (AWS) |
|---|---|---|
| **Coût** | 0 € | ~$72/mois minimum |
| **Démarrage** | 2 min | 15-20 min |
| **Helm charts** | Identiques | Identiques |
| **Ingress** | NGINX addon | AWS Load Balancer Controller |
| **StorageClass** | `standard` | `gp3` |
| **Registry** | Minikube / ECR | ECR |

### Ce qui change : uniquement les values.yaml

```yaml
# values-local.yaml
ingress.className: nginx
ingress.host: todo.local
storage.className: standard

# values-prod.yaml (EKS)
ingress.className: alb
ingress.host: todo.example.com
storage.className: gp3
```

---

## 18. Ressources pour aller plus loin

| Sujet | Ressource |
|---|---|
| Architecture Hexagonale | *"Get Your Hands Dirty on Clean Architecture"* — Tom Hombergs |
| Java 21 Virtual Threads | [JEP 444](https://openjdk.org/jeps/444) |
| Helm | [helm.sh/docs](https://helm.sh/docs/) |
| Spring Security OIDC | [docs.spring.io/spring-security](https://docs.spring.io/spring-security/reference/servlet/oauth2/login/index.html) |
| Kafka patterns | *"Designing Event-Driven Systems"* — Ben Stopford (gratuit Confluent) |
| Kubernetes patterns | *"Kubernetes Patterns"* — Bilgin Ibryam & Roland Huß |
| Minikube | [minikube.sigs.k8s.io](https://minikube.sigs.k8s.io/docs/) |
| Angular 20 Signals | [angular.dev/guide/signals](https://angular.dev/guide/signals) |
| Gatling DSL | [gatling.io/docs](https://gatling.io/docs/gatling/reference/current/) |
| Lambda SnapStart | [docs.aws.amazon.com/lambda/snapstart](https://docs.aws.amazon.com/lambda/latest/dg/snapstart.html) |
| RDS PostgreSQL | [docs.aws.amazon.com/rds/postgresql](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/CHAP_PostgreSQL.html) |
| AWS Step Functions | [docs.aws.amazon.com/step-functions](https://docs.aws.amazon.com/step-functions/latest/dg/welcome.html) |
| AWS Cost Explorer | [docs.aws.amazon.com/cost-management](https://docs.aws.amazon.com/cost-management/latest/userguide/what-is-costexplorer.html) |
| AWS Budgets | [docs.aws.amazon.com/cost-management/budgets](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-managing-costs.html) |
| Terraform AWS Provider | [registry.terraform.io/providers/hashicorp/aws](https://registry.terraform.io/providers/hashicorp/aws/latest/docs) |
| GitHub Actions — OIDC AWS | [docs.github.com/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services](https://docs.github.com/en/actions/deployment/security-hardening-your-deployments/configuring-openid-connect-in-amazon-web-services) |
| GitHub Actions Marketplace | [github.com/marketplace?type=actions](https://github.com/marketplace?type=actions) |
| SonarCloud (gratuit repo public) | [sonarcloud.io](https://sonarcloud.io) |

---

*Document généré le 10 mai 2026 — Mis à jour le 10 mai 2026 (GitHub Actions + OIDC AWS + SonarCloud + 3 workflows) — Projet To-Do Enterprise Interview Prep*
