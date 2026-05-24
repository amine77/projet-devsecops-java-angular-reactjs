# Argumentaires Entretien Technique — To-Do List Enterprise

> Mémo de révision structuré. Pour chaque choix : la question telle que posée en entretien,
> la réponse en bullet points, les contre-arguments à anticiper, les relances probables.

---

## Table des matières

1. [Architecture & Design](#1-architecture--design)
2. [Backend Java](#2-backend-java)
3. [Frontend](#3-frontend)
4. [Data & Messaging](#4-data--messaging)
5. [AWS — Services de base](#5-aws--services-de-base)
6. [AWS — Serverless & Orchestration](#6-aws--serverless--orchestration)
7. [AWS — Observabilité & Configuration](#7-aws--observabilité--configuration)
8. [AWS — Données & Analytics](#8-aws--données--analytics)
9. [AWS — Sécurité & Identité](#9-aws--sécurité--identité)
10. [AWS — Gestion des coûts & FinOps](#10-aws--gestion-des-coûts)
11. [Kubernetes & Infrastructure](#11-kubernetes--infrastructure)
12. [Tests & Qualité](#12-tests--qualité)
13. [DevSecOps & CI/CD](#13-devsecops--cicd)
14. [Questions pièges classiques](#14-questions-pièges-classiques)

---

## 0. Conception fonctionnelle

---

### Décris le modèle fonctionnel de l'application

**Question en entretien :** *"C'est quoi exactement le domaine métier de cette application ?"*

**Réponse :**
> "C'est une application de gestion d'actes (to-dos) avec un workflow d'approbation à un seul niveau. Trois rôles :
>
> - **Gestionnaire** : crée des actes dans son périmètre, peut les modifier ou supprimer tant qu'ils sont au statut 'À faire'. Il ne peut pas voir les actes de ses collègues.
> - **Manager** : responsable d'une équipe, il valide, rejette ou clôture les actes via un tableau de bord. Il ne crée pas d'actes lui-même.
> - **Super-Administrateur** : accès total à toutes les données et à la configuration.
>
> Le workflow est intentionnellement simple : un seul niveau d'approbation, pas de délégation, pas de multi-validation. C'est suffisant pour le contexte métier et ça évite une complexité technique injustifiée."

---

### Pourquoi c'est le Manager qui place en Done et pas le Gestionnaire ?

**Réponse :**
> "Dans ce modèle, le Gestionnaire est l'opérationnel — il saisit et suit ses actes. Le Manager est le décideur — il approuve, rejette, et valide la complétion. Donner au Gestionnaire la capacité de marquer Done introduirait une ambiguïté : un acte 'Done' par le Gestionnaire signifie-t-il qu'il est réellement terminé du point de vue de l'organisation ?
>
> En centralisant les transitions de statut chez le Manager, on garantit que chaque changement d'état est une décision managériale — pas un auto-reporting. C'est le principe de **séparation des responsabilités** appliqué au workflow métier."

---

### Pourquoi un seul niveau d'approbation ?

**Réponse :**
> "Le principe YAGNI — You Aren't Gonna Need It. Le contexte métier décrit ne nécessite pas de validation multi-niveaux. Ajouter un deuxième niveau (Manager → Direction) aurait complexifié le domaine, le workflow, les permissions Spring Security et l'interface Angular/React, sans valeur ajoutée réelle pour ce projet.
>
> Si le besoin évolue, l'architecture hexagonale permet d'ajouter un niveau sans toucher au domaine existant — on ajoute un port et un adaptateur, on modifie les statuts. C'est l'avantage de l'isolation du domaine."

---

### Pourquoi la suppression est-elle limitée au statut 'À faire' ?

**Réponse :**
> "C'est une règle d'intégrité métier (RG-05). Une to-do rejetée a déjà fait l'objet d'une décision managériale — elle appartient à l'historique de l'équipe. La supprimer effacerait une trace d'audit.
>
> En pratique : si un Gestionnaire veut abandonner une to-do rejetée, il ne la modifie pas — elle reste dans l'historique avec le statut 'Rejetée'. C'est intentionnel : le Manager peut voir toutes les to-dos rejetées pour analyser les patterns de son équipe.
>
> Techniquement, ça se traduit par une précondition dans le domain service : `if (task.status() != TaskStatus.A_FAIRE) throw new TaskDeletionNotAllowedException(id)`."

---

### Comment tu modélises le RBAC (Role-Based Access Control) dans Spring Security ?

**Réponse :**
> "Deux niveaux de contrôle :
>
> **Niveau endpoint (URL)** via `@PreAuthorize` :
> ```java
> @PostMapping('/{id}/validate')
> @PreAuthorize('hasAnyRole(\"MANAGER\", \"SUPER_ADMINISTRATEUR\")')
> public void validate(@PathVariable UUID id) { ... }
> ```
>
> **Niveau données (ownership)** dans le domain service :
> ```java
> // Un Manager ne peut agir que sur les to-dos de son équipe
> if (!task.teamId().equals(currentUser.teamId())) {
>     throw new UnauthorizedAccessException();
> }
> ```
>
> Le premier niveau filtre par rôle. Le second filtre par périmètre de données. Les deux sont nécessaires : un Manager authentifié avec le bon rôle ne doit pas pouvoir agir sur les to-dos d'une autre équipe.
>
> Les rôles sont portés par le JWT Cognito/Keycloak via un claim `roles`. Spring Security les mappe automatiquement avec `JwtAuthenticationConverter`."

---

## 1. Architecture & Design

---

### Pourquoi l'Architecture Hexagonale ?

**Question en entretien :** *"Pourquoi avoir choisi cette architecture ? C'est du sur-engineering pour une To-Do List."*

**Réponse :**
> "C'est volontairement over-engineered à des fins pédagogiques. L'architecture hexagonale isole la logique métier dans un domaine sans dépendances — ni Spring, ni JPA, ni SDK AWS.
>
> Trois bénéfices concrets dans ce projet :
> - **Testabilité** : 100% de couverture sur le module `domain` avec des mocks Java purs. Pas de Spring Context, pas de base de données. Les tests s'exécutent en millisecondes.
> - **Portabilité** : `FileStoragePort` a deux implémentations — `MinioFileStorageAdapter` (Minikube) et `S3FileStorageAdapter` (AWS). Spring active l'une selon le profil. Zéro modification dans le domaine.
> - **Évolutivité** : migrer de PostgreSQL local vers RDS n'a pas touché une ligne de logique métier. On a juste ajouté un adaptateur.
>
> En production, j'appliquerais ce niveau de structure à partir du moment où le domaine dépasse une dizaine de règles métier ou que l'équipe dépasse 5 développeurs."

**Contre-argument :** *"Mais ça complexifie la structure du projet."*
> "Oui, il y a plus de fichiers. Mais chaque fichier a une responsabilité unique et claire. Un junior qui rejoint l'équipe peut lire uniquement `domain/` pour comprendre ce que fait l'application — sans connaître Spring, Kafka ou AWS. C'est un investissement en lisibilité."

**Questions de relance :**
- *"Quelle est la différence entre un port entrant et sortant ?"*
  > Port entrant = ce que l'application expose aux acteurs primaires (use cases). Port sortant = ce que l'application consomme des acteurs secondaires (repository, event publisher, notification). Les adaptateurs entrants appellent les ports entrants. Les adaptateurs sortants implémentent les ports sortants.
- *"En quoi c'est différent du Clean Architecture de Robert Martin ?"*
  > Même philosophie (dépendances pointent vers le domaine), terminologie différente. Clean Architecture ajoute les couches Entities/Use Cases/Interface Adapters/Frameworks. L'hexagonale est plus pragmatique : domaine au centre, ports tout autour.

---

### Pourquoi DDD (Domain-Driven Design) ?

**Réponse :**
> "J'applique les tactiques DDD sans forcément l'aspect stratégique complet :
> - **Records Java 21** pour les Value Objects (`TaskId`, `Title`, `Priority`) : immutables, comparés par valeur, zéro boilerplate.
> - **Domain Events** (`TaskCreated`, `TaskCompleted`) : ce qui s'est passé dans le domaine. C'est ce qui alimente Kafka et déclenche les Lambdas.
> - **Aggregate Root** (`Task`) : toute modification d'une pièce jointe passe par `task.addAttachment(...)` — la tâche garantit ses invariants (ex: nombre max de pièces jointes)."

**Question de relance :** *"Qu'est-ce qu'un Aggregate Root ?"*
> C'est l'entité racine d'un ensemble cohérent d'objets. On ne modifie jamais les entités internes directement — on passe toujours par la racine, qui maintient les invariants métier.

---

## 2. Backend Java

---

### Pourquoi Java 21 et pas Java 17 ?

**Réponse :**
> "Trois apports concrets :
>
> **Virtual Threads (JEP 444)** : Spring Boot 3.2+ les active avec `spring.threads.virtual.enabled=true`. Chaque requête HTTP tourne sur un virtual thread — léger, managé par la JVM. Pour une API qui fait de l'I/O vers RDS, Redis, Kafka et SQS en parallèle, on supporte des milliers de connexions concurrentes sans saturer un thread pool.
>
> **Records** : mes Value Objects et DTOs sont des Records. Le compilateur génère constructeur, `equals()`, `hashCode()`, `toString()`. Code plus lisible, moins d'erreurs.
>
> **Pattern Matching + Sealed Classes** : les `switch` expressions avec pattern matching rendent le code de mapping expressif. Les Sealed Classes modélisent des hiérarchies fermées — parfait pour les Domain Events."

**Question de relance :** *"Différence entre virtual thread et platform thread ?"*
> Un platform thread est un wrapper autour d'un thread OS (~1MB de stack, coûteux). Un virtual thread est managé par la JVM, monté sur un carrier thread. Quand un virtual thread se bloque sur I/O, il libère son carrier thread qui exécute un autre virtual thread. Résultat : des millions de virtual threads avec quelques dizaines de carrier threads.

---

### Pourquoi Spring Boot et pas Quarkus ?

**Réponse :**
> "Spring Boot est le standard de facto. Pour ce projet pédagogique, il maximise la reconnaissance en entretien. Quarkus et Micronaut ont des avantages réels : démarrage plus rapide, empreinte mémoire réduite, GraalVM natif. Mais avec Virtual Threads, Spring Boot 3.2+ ferme l'écart en performance. Et pour le cas serverless où le démarrage rapide est critique, Lambda SnapStart résout le cold start Java sans recompiler en natif."

---

### Pourquoi Spring Batch ET Lambda Archiver — pas l'un ou l'autre ?

**Réponse :**
> "Ils répondent à des besoins différents :
>
> **Lambda TaskArchiver** (EventBridge nuit) : traitement simple, < 15 minutes, déclenché par événement. Pas de serveur à gérer, facturation à l'invocation.
>
> **Spring Batch** : traitement complexe avec chunking (N enregistrements par transaction), retry/skip par enregistrement, JobRepository qui trace chaque exécution. Si l'archivage dépasse 15 minutes ou nécessite des reprises granulaires en cas d'erreur partielle, Spring Batch est indispensable.
>
> En entretien, avoir les deux montre que je sais choisir le bon outil selon les contraintes — pas juste appliquer le pattern à la mode."

---

## 3. Frontend

---

### Pourquoi deux frontends (Angular + React) ?

**Réponse :**
> "Deux raisons. Pédagogiquement : comparer les deux frameworks sur le même domaine métier révèle leurs différences architecturales réelles — Angular impose une structure (modules, DI), React laisse la liberté (qui exige de la discipline). Stratégiquement pour l'entretien : je peux parler des deux avec des exemples concrets issus du même projet."

---

### Pourquoi Angular 20 et les Signals ?

**Réponse :**
> "Angular 20 marque la stabilité complète des Signals. C'est un changement de paradigme depuis Zone.js.
>
> - **Zoneless** : suppression de `zone.js`. La détection de changement est granulaire — seuls les composants qui lisent un Signal modifié se re-rendent. Bundles plus légers.
> - **`input()` / `output()`** : remplacent `@Input()` / `@Output()`. Le composant devient une fonction réactive.
> - **`resource()`** : gère les requêtes HTTP async avec état de chargement intégré. Plus besoin d'`async pipe` ni de `subscribe` dans les composants.
> - **`linkedSignal()`** : signal dérivé mais modifiable — pour les états locaux qui se synchronisent avec une prop mais peuvent être changés par l'utilisateur.
> - **`@defer`** : lazy loading déclaratif de blocs de template (`on viewport`, `on interaction`)."

**Question de relance :** *"Différence entre `computed()` et `linkedSignal()` ?"*
> `computed()` est en lecture seule — recalculé automatiquement quand ses dépendances changent. `linkedSignal()` est aussi dérivé, mais reste modifiable manuellement. Utile pour un filtre pré-rempli par l'URL mais que l'utilisateur peut changer.

---

### Pourquoi Zustand plutôt que Redux ?

**Réponse :**
> "Redux se justifie pour des équipes de +50 développeurs avec des domaines métier complexes bien séparés. Son DevTools et son architecture flux unidirectionnel strict sont précieux dans ce contexte.
>
> Zustand offre la même puissance avec 1/5 du code : pas de Provider, pas de connect, pas d'action creators. Compatible Immer pour l'immutabilité, compatible React Query pour le server state. Les abonnements sont granulaires — un composant ne re-render que si la portion du store qu'il lit change.
>
> Je connais Redux. Je l'utiliserais si l'équipe l'avait adopté. Mais je ne l'introduirais pas dans un nouveau projet sans raison spécifique."

---

## 4. Data & Messaging

---

### Pourquoi trois bases de données ?

**Réponse :**
> "Chaque base répond à un besoin différent :
>
> **PostgreSQL / RDS** : données relationnelles transactionnelles. Tasks, users, assignations. ACID, contraintes d'intégrité, joins complexes.
>
> **MongoDB** : Event Store. Chaque changement d'état d'une tâche est un document. Schéma évolutif — un nouvel attribut d'événement n'exige pas de migration.
>
> **Redis** : cache. La liste des tâches d'un utilisateur est mise en cache avec TTL. Invalidation explicite à chaque modification.
>
> En production, je commencerais par PostgreSQL. J'ajouterais Redis quand les métriques montrent un besoin. MongoDB quand le schéma devient trop variable pour du relationnel."

---

### Pourquoi Kafka pour les événements domaine et SQS pour Lambda → Spring Boot ?

**Réponse :**
> "Kafka et SQS sont complémentaires :
>
> **Kafka** = log d'événements distribué. Les messages sont **persistés et rejouables**. Un nouveau service déployé 6 mois après peut rejouer tous les événements depuis le début. Rétention configurable (7 jours par défaut). C'est le bon choix pour le bus d'événements domaine (`TaskCreated`, `TaskCompleted`).
>
> **SQS** = file de travail. Les messages sont supprimés après consommation. Parfait pour la communication Lambda → Spring Boot : la Lambda publie un message dans SQS, Spring Boot le consomme et met à jour l'état. Simple, managé, aucun serveur Kafka à gérer côté AWS.
>
> Les deux ont leur place. Utiliser SQS pour le bus domaine, c'est perdre le replay. Utiliser Kafka pour tout, c'est un cluster à opérer."

---

### Pourquoi DynamoDB pour les préférences utilisateur ?

**Réponse :**
> "Les préférences (thème sombre, tri par défaut, filtres sauvegardés) ont des caractéristiques précises : accès uniquement par clé primaire `userId`, pas de joins, schéma flexible (chaque utilisateur peut avoir des préférences différentes), latence < 10ms requise.
>
> DynamoDB répond exactement à ça. Et il est **always-free** jusqu'à 25 GB + 25 WCU/RCU — aucun coût dans ce projet.
>
> Utiliser PostgreSQL pour ça serait possible, mais ce serait un anti-pattern : on utiliserait un outil conçu pour les relations complexes sur un cas d'accès par clé primaire simple."

**Question de relance :** *"C'est quoi le single-table design ?"*
> Toutes les entités dans une seule table DynamoDB, différenciées par des préfixes sur les clés (`USER#123`, `PREF#123#theme`). Cela optimise les coûts (moins de tables = moins d'overhead) et les patterns d'accès. C'est le design recommandé par AWS pour DynamoDB.

---

## 5. AWS — Services de base

---

### Pourquoi RDS PostgreSQL et pas Aurora ?

**Réponse :**
> "Pour ce projet pédagogique avec `terraform destroy` à chaque session :
>
> - **RDS db.t3.micro** : dans le Free Tier la première année (750h/mois). Provisioning en ~3-5 min. Coût an 2+ : $0.017/heure.
> - **Aurora Serverless v2** : minimum 0.5 ACU facturé en continu (~$43/mois si on oublie de l'arrêter). Provisioning : 5-10 min.
>
> Je connais les avantages d'Aurora : performance ×5, failover < 30s, scaling ACU, stockage distribué sur 6 réplicas en 3 AZs. Je l'utiliserais en production. Ici, RDS Free Tier est le choix rationnel — même driver JDBC, même Spring Data JPA."

---

### Pourquoi S3 + CloudFront pour les frontends et pas un serveur Nginx ?

**Réponse :**
> "Nginx dans Kubernetes fonctionne très bien en dev local (Minikube). Pour l'exposition publique, S3 + CloudFront apporte :
> - **Coût quasi nul** : CloudFront Free Tier (1TB/mois) + S3 static hosting.
> - **Distribution mondiale** : 400+ edge locations, latence réduite partout.
> - **Haute disponibilité** : S3 = 99.999999999% de durabilité sans infrastructure à gérer.
> - **HTTPS automatique** : certificat CloudFront inclus, pas d'ACM à configurer.
>
> Pédagogiquement, j'ai pratiqué les `bucket policies`, les `CloudFront Behaviors`, et les `Cache Policies` — dont les spécificités pour les SPA (redirection 404 → index.html)."

---

### Pourquoi pas d'AWS Load Balancer (ALB) dans ce projet ?

**Question en entretien :** *"Tu aurais pu mettre un ALB devant ton API, pourquoi tu ne l'as pas fait ?"*

**Réponse :**
> "Un ALB est pertinent dans trois cas : plusieurs instances EC2 à équilibrer, ECS avec plusieurs tâches, ou EKS avec plusieurs pods. Dans ce projet, je n'utilise pas EKS — l'API Spring Boot tourne sur App Runner, qui intègre son propre mécanisme de load balancing et de scaling.
>
> L'ALB a un coût minimum de **~$16/mois** indépendamment du trafic — c'est son tarif horaire ($0.0225/h × 720h). Pour un usage pédagogique avec `terraform destroy` à chaque session, c'est injustifié.
>
> App Runner répond exactement au besoin : je lui donne une image ECR, il expose un endpoint HTTPS avec TLS managé, scale de 0 à N instances, et coûte **~$0.10 pour une session de 2h**."

**Si l'interviewer insiste — quand utiliserais-tu un ALB ?**
> "Dès que j'aurais plusieurs services derrière (routing par chemin vers différents microservices EC2), ou dès que je passerais sur EKS avec l'AWS Load Balancer Controller. Dans ce cas, l'ALB deviendrait l'Ingress Kubernetes — exactement comme NGINX Ingress sur Minikube, mais managé par AWS."

---

### Comment tu exposes l'API et les frontends sans CORS sur deux domaines différents ?

**Question en entretien :** *"Ton frontend est sur CloudFront, ton API sur App Runner — tu as un problème de CORS non ?"*

**Réponse :**
> "C'est précisément pour ça que j'ai configuré CloudFront comme **point d'entrée unique** avec plusieurs origines :
>
> ```
> https://d1234.cloudfront.net/angular/*  → S3 (Angular)
> https://d1234.cloudfront.net/react/*    → S3 (React)
> https://d1234.cloudfront.net/api/*      → App Runner (Spring Boot)
> https://d1234.cloudfront.net/reports/*  → API Gateway (Lambda)
> ```
>
> Depuis le navigateur, tout vient du même domaine `d1234.cloudfront.net`. Le navigateur ne voit pas de cross-origin — pas de CORS à gérer côté Spring Boot pour la production.
>
> En dev local, les frontends tournent sur `localhost:4200` et `localhost:3000` et appellent `http://todo.local/api` — là j'ai du CORS, mais je le configure via Parameter Store avec les origines autorisées."

**Question de relance :** *"C'est quoi un CloudFront Behavior ?"*
> Un Behavior est une règle de routage CloudFront : pour un path pattern (`/api/*`), on définit l'origine cible (App Runner), la politique de cache (désactivée pour une API), et les méthodes HTTP autorisées (GET, POST, PUT, DELETE). CloudFront évalue les Behaviors dans l'ordre de priorité — le comportement par défaut (S3) s'applique si aucun path pattern ne correspond.

---

### Pourquoi App Runner et pas EC2 + ALB pour l'API ?

**Réponse :**
> "EC2 + ALB nécessite : une instance EC2 à gérer (patches, monitoring, SSH), un ALB ($16/mois minimum), un Target Group, un Security Group, un Auto Scaling Group si on veut scaler. C'est 5-6 ressources Terraform pour faire ce qu'App Runner fait en 1.
>
> App Runner est le service AWS pour déployer des conteneurs sans gérer l'infrastructure. Il prend une image ECR et expose un endpoint HTTPS en 3 minutes. Scaling automatique, healthcheck, rollback. Coût : $0.064/vCPU-heure + $0.007/GB-heure.
>
> Pour ce projet pédagogique éphémère, c'est le bon trade-off. En production avec du trafic réel et plusieurs microservices, je passerais sur ECS Fargate + ALB ou EKS + ALB Controller."

---

### Pourquoi ECR plutôt que Docker Hub ?

**Réponse :**
> "ECR est le registry Docker natif AWS. Quatre avantages dans ce projet :
> - **Sécurité** : les images restent dans le VPC AWS. Pas de dépendance externe lors du déploiement.
> - **IAM** : l'accès est contrôlé par IAM roles, pas par des credentials username/password.
> - **Scanning** : ECR intègre un scanning de vulnérabilités activable en Terraform (`scan_on_push = true`).
> - **Lifecycle Policies** : nettoyage automatique côté serveur AWS — pas de script cron, pas de Lambda dédiée."

---

### FinOps — ECR Lifecycle Policies : comment tu évites la facture de stockage Docker ?

**Question en entretien :** *"Comment tu gères la croissance du stockage dans ton registry Docker ? Ça peut vite coûter cher."*

**Réponse :**
> "C'est une question de FinOps — Financial Operations. J'utilise les **ECR Lifecycle Policies**, un mécanisme natif AWS déclaratif : on définit des règles en JSON (ou Terraform), AWS les évalue côté serveur une fois par jour, sans aucun script à maintenir.
>
> J'ai défini **5 règles** dans `terraform/permanent/ecr.tf`, appliquées à chaque repository (`todo-api`, `todo-angular`, `todo-react`) :
>
> | Priorité | Type d'image | Règle | Raison |
> |---|---|---|---|
> | 1 | Sans tag (dangling) | Supprimer après **1 jour** | Créées à chaque `docker push` sur un tag existant — aucune valeur |
> | 2 | `feature-*`, `fix-*`, `pr-*` | Supprimer après **7 jours** | Images de branches courtes, inutiles après merge |
> | 3 | `snapshot-*`, `ci-*` | Garder les **5 dernières** | Utile pour déboguer les builds récents |
> | 4 | `v*`, `latest`, `stable` | Garder les **3 dernières** | Rollback N-1 et N-2 possible en production |
> | 5 | Tout (catch-all) | Supprimer après **90 jours** | Filet de sécurité pour les images oubliées |
>
> Résultat : le stockage ECR reste sous **500 MB → Free Tier gratuit**, quel que soit le rythme des déploiements."

**Pourquoi c'est une approche FinOps ?**
> "FinOps, c'est traiter le coût cloud comme une contrainte de conception — pas comme une facture à subir en fin de mois. Ici, la politique de nettoyage est dans le code Terraform, versionnée en Git, reviewée en PR. Si demain on ajoute un repository ECR, la Lifecycle Policy s'applique automatiquement via `for_each`. Le coût est maîtrisé par construction."

**Contre-argument :** *"Tu aurais pu juste écrire un script cron."*
> "Un script cron exige : une machine pour l'exécuter, des credentials AWS stockés quelque part, une gestion des erreurs et des alertes si le script échoue. La Lifecycle Policy n'a aucune de ces dépendances — c'est AWS qui l'exécute, avec les mêmes garanties SLA qu'ECR. C'est le principe **serverless-first** appliqué aux tâches d'infrastructure."

**Convention de tagging associée :**
> "La politique ne fonctionne que si les images sont taguées de manière cohérente. J'ai défini une convention dans le pipeline GitHub Actions :
> - `v$CI_PIPELINE_IID` + `latest` → branches `main` (→ règle prod : garder 3)
> - `feature-$CI_COMMIT_REF_SLUG` → branches `feature/*` (→ règle branches : supprimer après 7j)
> - `snapshot-$CI_COMMIT_SHORT_SHA` → tous les commits (→ règle snapshot : garder 5)
>
> Sans cette convention, les règles par préfixe de tag ne correspondent à rien."

**Questions de relance :**
- *"Quelle est la différence entre une Lifecycle Policy ECR et une S3 Lifecycle Rule ?"*
  > Même concept, appliqué à des ressources différentes. S3 Lifecycle Rules gèrent la transition des objets entre classes de stockage (Standard → Glacier) et leur expiration. ECR Lifecycle Policies gèrent l'expiration des images Docker. Les deux sont déclaratives, évaluées côté serveur, et définissables en Terraform.
- *"Combien de repositories ECR tu as dans ce projet ?"*
  > Trois : `todo-api` (Spring Boot, ~250 MB/image), `todo-angular` (~50 MB/image Nginx), `todo-react` (~50 MB/image Nginx). Avec 3 images prod par repo, ça fait ~1 050 MB → au-dessus du Free Tier. Les règles ramènent ça à < 500 MB en nettoyant les dangling et les branches.

---

### Comment tu gères les secrets AWS dans ce projet ?

**Réponse :**
> "Trois couches :
>
> **Parameter Store** (non-sensible) : endpoints RDS, noms de buckets, configuration TTL Redis. Récupérés au démarrage Spring Boot via `spring-cloud-aws`. Gratuit pour les paramètres Standard.
>
> **Secrets Manager** (si sensible) : credentials RDS. Rotation automatique possible. $0.40/secret/mois.
>
> **Kubernetes Secrets** : le secret RDS créé par `terraform output` est injecté comme variable d'environnement dans le pod Spring Boot.
>
> Ce qu'on évite : credentials en clair dans `application.properties`, dans les Dockerfiles, dans les logs, dans le code versionné."

---

## 6. AWS — Serverless & Orchestration

---

### Pourquoi AWS Lambda avec SnapStart (Java 21) ?

**Réponse :**
> "Lambda est idéal pour les traitements événementiels ponctuels : pas de serveur, facturation à l'invocation, scaling automatique de 0 à des milliers.
>
> Le problème historique de Lambda Java : cold start de 3-5 secondes. SnapStart le résout : AWS prend un snapshot de la JVM après l'initialisation et le restaure à chaque cold start. Résultat : ~200ms — comparable à Node.js ou Python.
>
> Je n'utiliserais pas Lambda pour l'API principale (limite 15 min, pas de WebSocket natif, connexions DB à gérer via RDS Proxy). Mais pour le traitement de pièces jointes, l'archivage nocturne et le reporting à la demande, c'est le bon outil."

| Runtime | Sans SnapStart | Avec SnapStart |
|---|---|---|
| Java 21 | 3 000 – 5 000 ms | ~200 ms |
| Node.js 20 | 100 – 300 ms | N/A |
| Python 3.12 | 100 – 400 ms | N/A |

---

### Pourquoi Step Functions pour le workflow pièces jointes ?

**Réponse :**
> "Une seule Lambda qui enchaîne validation + thumbnail + écriture RDS n'a pas de retry granulaire. Si l'étape 3 échoue, tout recommence depuis le début.
>
> Step Functions apporte :
> - **Retry par étape** avec backoff exponentiel configurable
> - **Branches conditionnelles** : si MIME invalide → état Échec → SES email erreur
> - **Visibilité** : chaque exécution est tracée dans la console AWS avec les inputs/outputs de chaque état
> - **Pas de code de plomberie** : pas besoin d'écrire un orchestrateur maison
>
> C'est la différence entre la **chorégraphie** (chaque service réagit aux événements des autres — Kafka) et l'**orchestration** (Step Functions coordonne les étapes)."

**Question de relance :** *"Chorégraphie vs Orchestration — quand choisir quoi ?"*
> **Chorégraphie (Kafka/SNS)** : pas de point unique de contrôle. Chaque service est autonome. Difficile à déboguer (l'état est réparti). Bien pour des flux simples et évolutifs.
> **Orchestration (Step Functions)** : une machine d'état centrale visible et traçable. Plus facile à déboguer. Bien pour des workflows complexes avec branches, retry, et état partagé.

---

### Pourquoi SNS en fan-out vers SQS + SES ?

**Réponse :**
> "Le fan-out SNS → (SQS + SES) découple les consommateurs du producteur.
>
> Concrètement : quand une tâche est assignée, on publie un seul message dans SNS. SNS le distribue simultanément à :
> - **SQS** → Spring Boot le consomme pour mettre à jour l'état applicatif
> - **SES** → email envoyé à l'utilisateur assigné
>
> Si on ajoute un service de push mobile demain, on ajoute une subscription SNS → pas de modification du producteur."

---

## 7. AWS — Observabilité & Configuration

---

### Pourquoi CloudWatch et pas juste Grafana/Loki ?

**Réponse :**
> "Grafana + Loki + Prometheus couvrent l'observabilité Minikube. CloudWatch complète pour la partie AWS :
> - **Logs Lambda** automatiquement envoyés dans CloudWatch Logs — pas de configuration
> - **Métriques custom** : la Lambda `TaskArchiver` publie `tasks_archived_count` — visible dans CloudWatch et déclenche une alarme si 0 tâches archivées (anomalie)
> - **Alarmes** : si le taux d'erreur Lambda dépasse 5% → SNS → email
>
> Les deux coexistent : Grafana pour les métriques Minikube (JVM, Spring Boot), CloudWatch pour les métriques AWS (Lambda, RDS, Step Functions)."

---

### Pourquoi AWS X-Ray pour le distributed tracing ?

**Réponse :**
> "X-Ray trace le chemin complet d'une requête à travers plusieurs services AWS. Pour la Lambda `ReportingApi` :
>
> ```
> API Gateway → Lambda (sous-segment : init)
>             → RDS PostgreSQL (sous-segment : requête SQL + durée)
>             → DynamoDB (sous-segment : GetItem + durée)
> ```
>
> Sans X-Ray, si la requête prend 800ms, je ne sais pas si c'est RDS, DynamoDB ou le code Lambda. Avec X-Ray, la Service Map me montre exactement où est le goulot.
>
> Free Tier : 100 000 traces/mois — largement suffisant pour un projet pédagogique."

---

### Pourquoi Parameter Store et pas juste des variables d'environnement ?

**Réponse :**
> "Les variables d'environnement sont statiques — définies au démarrage du pod. Si l'endpoint RDS change (recréation Terraform), il faut redéployer le pod.
>
> Parameter Store permet une **configuration dynamique centralisée** :
> - Toutes les instances Spring Boot lisent la même configuration
> - Un changement de valeur est immédiatement disponible (avec Spring Cloud AWS refresh)
> - Hiérarchie claire : `/todo-app/local/datasource/url` vs `/todo-app/aws/datasource/url`
> - Audit : Parameter Store trace qui a changé quoi et quand
>
> Gratuit pour les paramètres Standard (< 4KB)."

---

## 8. AWS — Données & Analytics

---

### Pourquoi Athena pour analyser les archives S3 ?

**Réponse :**
> "La Lambda `TaskArchiver` écrit les tâches complétées en JSON partitionné sur S3 :
> ```
> s3://todo-archives/tasks/year=2026/month=05/day=10/tasks.json
> ```
>
> Athena permet de requêter ces fichiers directement avec du SQL standard — sans charger les données dans une base :
> ```sql
> SELECT user_id, COUNT(*) as total
> FROM todo_archives.tasks
> WHERE year='2026' AND month='05'
> GROUP BY user_id
> ```
>
> Coût : $5/TB scanné. Avec des données partitionnées et compressées (Parquet), une requête coûte quelques centimes. Athena + S3 = data warehouse serverless pour de petits volumes."

**Question de relance :** *"Pourquoi partitionner par date ?"*
> Le partitionnement réduit la quantité de données scannées par Athena — et donc le coût. Une requête sur `year=2026/month=05` ne scanne que les fichiers de mai 2026, pas tout l'historique. C'est le principe de **partition pruning**.

---

## 9. AWS — Sécurité & Identité

---

### Pourquoi Cognito et pas Keycloak en AWS ?

**Réponse :**
> "Keycloak sur Minikube est parfait en dev local. En session AWS, Cognito est le service OIDC managé — pas de serveur à gérer, scaling automatique, Free Tier 50 000 MAU.
>
> Ce qui est remarquable : **Spring Security ne voit pas la différence**. Le seul changement est :
> ```yaml
> # local
> spring.security.oauth2.resourceserver.jwt.issuer-uri: http://keycloak.../realms/todo
>
> # aws
> spring.security.oauth2.resourceserver.jwt.issuer-uri: ${COGNITO_ISSUER_URI}
> ```
>
> C'est le contrat OIDC en action. Mon application est compatible avec Keycloak, Cognito, Auth0 ou Okta sans modifier une ligne de code métier."

---

### Comment tu sécurises les Lambdas (IAM) ?

**Réponse :**
> "Principe du **moindre privilège** appliqué via IAM Roles :
>
> - La Lambda `AttachmentProcessor` a un role avec uniquement : `s3:GetObject` sur `todo-attachments`, `rds-db:connect`, `sqs:SendMessage` sur la queue spécifique.
> - Elle n'a **pas** accès à d3:ListBuckets, ni à d'autres queues SQS, ni à DynamoDB.
>
> En Terraform :
> ```hcl
> resource 'aws_iam_role_policy' 'attachment_processor' {
>   policy = jsonencode({
>     Statement = [{
>       Effect   = 'Allow'
>       Action   = ['s3:GetObject']
>       Resource = '${aws_s3_bucket.attachments.arn}/*'
>     }]
>   })
> }
> ```
>
> En entretien : *'IAM, c'est le principe du moindre privilège appliqué à chaque ressource. Si une Lambda est compromise, l'attaquant n'a accès qu'aux ressources pour lesquelles elle a été explicitement autorisée.'*"

---

## 10. AWS — Gestion des coûts

---

### Comment tu maîtrises les coûts AWS dans ce projet ?

**Réponse :**
> "Cinq mécanismes combinés, tous gratuits :
>
> **1. Architecture éphémère** : App Runner, RDS et NAT Gateway sont créés en début de session et détruits à la fin via `terraform destroy`. Je ne paie que pendant l'utilisation réelle — ~$0.22/session.
>
> **2. AWS Budgets — 2 budgets (Free Tier : 2 budgets gratuits)** :
> - Budget Coût : $10/mois — alerte email à 50% ($5), 80% ($8), et prévision de dépassement
> - Budget Usage RDS : 4 heures/mois — alerte si RDS tourne plus de 4h, ce qui signale un `terraform destroy` oublié. C'est le filet de sécurité le plus précis car il détecte l'anomalie *avant* que Cost Explorer ne la quantifie.
>
> **3. CloudWatch Billing Alarm** : alarme en temps réel si la facture estimée dépasse $5 (région us-east-1).
>
> **4. Cost Anomaly Detection (ML)** : alerte si une dépense sort des patterns habituels — même sans dépasser le budget. Gratuit.
>
> **5. CLI post-session** : une commande qui vérifie qu'aucune ressource éphémère ne tourne encore.
>
> Résultat : moins de $4/mois pour 15 services AWS, dont 13 entièrement dans le Free Tier."

**Point fort à développer — le Budget Usage RDS :**
> "La plupart des gens créent un budget de coût. Moi j'ai ajouté un **budget d'usage en heures RDS**. La différence : le budget coût se déclenche quand la facture monte. Le budget usage se déclenche quand RDS tourne trop longtemps — indépendamment du coût. Si j'oublie le `terraform destroy` un vendredi soir et que RDS tourne tout le week-end, je reçois l'alerte usage dès que le seuil de 4h est dépassé, bien avant que le coût soit visible dans Cost Explorer."

---

### Qu'est-ce qu'AWS Budgets et pourquoi tu utilises 2 budgets différents ?

**Question en entretien :** *"Tu connais AWS Budgets ? Comment tu l'utilises ?"*

**Réponse :**
> "AWS Budgets permet de définir des seuils sur les coûts ou l'usage, et d'envoyer des alertes email ou SNS quand ces seuils sont atteints ou prévus d'être atteints.
>
> **Free Tier : les 2 premiers budgets sont gratuits.** Au-delà, $0.02/budget/jour. J'ai donc exactement 2 budgets, choisis stratégiquement :
>
> **Budget 1 — Coût mensuel ($10/mois)** :
> - Alerte à 50% → email à $5 (signal précoce)
> - Alerte à 80% → email à $8 (action requise)
> - Alerte prévisionnelle à 100% → email avant de dépasser
> - Type : COST — surveille la facture totale du projet
>
> **Budget 2 — Usage RDS en heures (4h/mois)** :
> - Alerte à 100% → email dès que RDS dépasse 4 heures dans le mois
> - Type : USAGE — surveille le nombre d'heures d'instance RDS
> - Objectif : détecter un `terraform destroy` oublié *avant* que le coût soit visible
>
> La différence entre les deux est importante : le budget coût réagit quand l'argent est dépensé. Le budget usage réagit quand RDS tourne trop longtemps — même si la facture en dollars n'a pas encore atteint le seuil."

**Question de relance :** *"Quels autres types de budgets AWS Budgets supporte-t-il ?"*
> Quatre types : **COST** (montant en dollars), **USAGE** (unités d'usage — heures, GB, requêtes), **RESERVATION** (utilisation des Reserved Instances), **SAVINGS_PLANS** (utilisation des Savings Plans). Pour un projet pédagogique, COST et USAGE couvrent tout ce dont on a besoin avec les 2 budgets gratuits.

**Question de relance :** *"Comment tu automatises la réaction si le budget est dépassé ?"*
> AWS Budgets Actions permet d'automatiser une réponse : appliquer une politique IAM restrictive, stopper des instances EC2, ou envoyer un message SNS qui déclenche une Lambda. Par exemple, si le budget est à 100%, une Lambda peut appeler `terraform destroy` automatiquement via l'API AWS. Pour ce projet pédagogique, l'alerte email suffit — mais en entreprise, l'action automatique évite les dépassements en production.

---

### Qu'est-ce que tu regardes en premier sur la console AWS pour vérifier ta facture ?

**Réponse :**
> "Dans l'ordre :
>
> 1. **Billing Dashboard** (console → Billing) : coût du mois en cours et tendance vs mois précédent. Vue en 10 secondes.
>
> 2. **Cost Explorer** filtré par tag `Project=todo-enterprise` : répartition par service. Si RDS apparaît avec 20h de facturation alors que j'ai fait 2 sessions de 2h, un `terraform destroy` a été oublié.
>
> 3. **CLI post-session** :
> ```bash
> aws rds describe-db-instances \
>   --query 'DBInstances[?TagList[?Key==`Environment`&&Value==`ephemeral`]].DBInstanceIdentifier' \
>   --output text
> ```
> Si cette commande retourne quelque chose, RDS tourne encore et facture."

---

### Pourquoi tagger toutes les ressources Terraform ?

**Réponse :**
> "Sans tags, Cost Explorer montre '$2.30 sur RDS' sans distinguer si c'est ce projet ou un autre. Avec le tag `Project=todo-enterprise`, je filtre exactement les coûts de ce projet.
>
> Les tags permettent aussi :
> - **Cost Allocation** : répartir les coûts par équipe ou projet dans une organisation
> - **Audit de sécurité** : identifier toutes les ressources d'un projet pour un scan
> - **Automatisation** : une Lambda peut lister et stopper toutes les ressources avec le tag `Environment=ephemeral` si elles ont plus de 4h d'uptime
>
> En entretien : *'Le tagging est une discipline d'équipe — pas juste une bonne pratique. Sans tags, la facturation AWS devient incompréhensible passé 10 services.'*"

---

### Qu'est-ce que le FinOps et comment tu l'appliques concrètement ?

**Question en entretien :** *"Tu connais le FinOps ? Comment tu l'appliques dans tes projets ?"*

**Réponse :**
> "FinOps (Financial Operations) est la discipline qui consiste à traiter le coût cloud comme une contrainte de conception — exactement comme la performance ou la sécurité. On ne subit pas la facture en fin de mois, on la pilote en continu.
>
> Dans ce projet, j'applique le FinOps à trois niveaux :
>
> **1. Architecture éphémère** : les seules ressources coûteuses à l'heure (RDS, NAT Gateway) sont détruites à chaque fin de session via `terraform destroy`. Le coût AWS passe de ~$50/mois (ressources permanentes) à ~$4/mois (usage réel).
>
> **2. Lifecycle Policies ECR** : les images Docker s'accumulent sans gestion proactive. J'ai défini 5 règles Terraform qui s'exécutent côté serveur AWS sans script à maintenir. ECR reste dans le Free Tier 500 MB quelle que soit la fréquence des déploiements.
>
> **3. Cost visibility as Code** : Budget Alert + CloudWatch Alarm + Cost Anomaly Detection sont définis en Terraform avec les tags `Project=todo-enterprise`. N'importe quel membre de l'équipe peut voir exactement ce que coûte ce projet en filtrant par tag dans Cost Explorer.
>
> Le principe FinOps que je retiens : **le coût optimal n'est pas le coût minimum, c'est le coût qui reflète la valeur produite**. Payer $4/mois pour pratiquer 13 services AWS et préparer des entretiens Architecte Cloud, c'est un investissement — pas un gaspillage."

**Les 3 pratiques FinOps à citer en entretien :**

| Pratique | Ce qu'on fait | Économie |
|---|---|---|
| **Environments éphémères** | `terraform destroy` après chaque session | ~$46/mois évités (RDS permanent) |
| **ECR Lifecycle Policies** | 5 règles déclaratives Terraform | Free Tier maintenu, $0 stockage images |
| **Cost tagging + alertes** | Tags `Project`, Budget Alert $10, Anomaly Detection | Visibilité totale, 0 surprise |

**Question de relance :** *"C'est quoi la différence entre FinOps et juste 'faire attention aux coûts' ?"*
> "Faire attention aux coûts, c'est regarder la facture et couper ce qui semble cher. FinOps, c'est une discipline avec des outils, des processus et des responsabilités. La différence clé : dans FinOps, l'ingénieur est responsable du coût de ce qu'il déploie — pas juste le département Finance. On mesure, on optimise, on documente. Les Lifecycle Policies ECR en Terraform, c'est du FinOps : le coût est géré par celui qui déploie, via le code."

---

## 11. Kubernetes & Infrastructure

---

### Pourquoi Minikube et pas directement EKS ?

**Réponse :**
> "Minikube donne accès à toutes les primitives Kubernetes : Deployments, StatefulSets, PVC, Ingress, ConfigMaps, Secrets, RBAC, HPA, NetworkPolicies. Les Helm charts écrits pour Minikube fonctionnent sur EKS en changeant deux valeurs : StorageClass (`standard` → `gp3`) et IngressClass (`nginx` → `alb`).
>
> EKS coûte $72/mois minimum (cluster fee $0.10/h × 720h) avant d'ajouter les nodes. Pour l'apprentissage, c'est un coût injustifié."

---

### Pourquoi Helm et pas des YAML bruts ?

**Réponse :**
> "Des YAML bruts sont corrects pour un déploiement unique. Helm apporte la valeur dès qu'on a plusieurs environnements :
> - **Templating** : les valeurs qui changent (image tag, replicas, URLs) sont dans `values.yaml`. Le template YAML reste le même.
> - **Versionnage** : chaque release est versionnée. `helm rollback todo-api 2` revient en 30 secondes.
> - **Lifecycle hooks** : exécuter des migrations DB avant de déployer l'application.
> - **Dépendances** : un chart peut dépendre d'un autre (todo-api dépend de PostgreSQL)."

---

### StatefulSet vs Deployment — quand choisir quoi ?

**Réponse :**
> "**Deployment** : pods interchangeables, sans état. Si un pod est tué, un nouveau est créé avec un nom aléatoire. Parfait pour Spring Boot, Angular, React.
>
> **StatefulSet** : garantit un nom stable (`kafka-0`, `kafka-1`), un volume dédié par pod, et un ordre de démarrage/arrêt. Indispensable pour :
> - **Kafka** : chaque broker doit retrouver ses partitions après redémarrage
> - **PostgreSQL** : le primary ne peut pas être interchangeable avec un replica
> - **MongoDB** : le Replica Set élit un primary via son identité"

---

## 12. Tests & Qualité

---

### Décris ta stratégie de tests — pyramide complète

**Réponse :**
> "**Unitaires (base)** — module `domain/` :
> - 100% de couverture sur la logique métier
> - Mocks Java purs, pas de Spring
> - Exécution en millisecondes
>
> **Intégration (milieu)** — module `infrastructure/` :
> - Testcontainers pour PostgreSQL, MongoDB, Redis, Kafka
> - Spring `@SpringBootTest` avec contexte réel
> - Vérifient que les adaptateurs fonctionnent avec les vraies technos
>
> **E2E (sommet)** :
> - Cypress pour les parcours utilisateur frontend
> - Gatling pour les scénarios de performance
> - Peu nombreux, chemins critiques uniquement"

---

### Pourquoi Gatling et pas JMeter ou k6 ?

**Réponse :**
> "**vs JMeter** : JMeter génère des fichiers XML. Les scénarios Gatling sont du code Scala — versionnables, reviewables, refactorisables en PR.
>
> **vs k6** : k6 est excellent pour des équipes JavaScript. Gatling a une intégration Maven native (`mvn gatling:test`) sans configuration supplémentaire dans un projet Java.
>
> Le modèle asynchrone Gatling (Netty) simule des milliers d'utilisateurs avec un seul processus. Les rapports HTML génèrent automatiquement P50/P95/P99, throughput et répartition des erreurs."

---

### Qu'est-ce qu'un test de soak et pourquoi l'utiliser ?

**Réponse :**
> "Un soak test maintient une charge nominale longue durée (2-8h). Il détecte les problèmes qui n'apparaissent qu'avec le temps :
> - **Fuites mémoire** : heap JVM augmente progressivement → OutOfMemoryError
> - **Fuites de connexions** : pool PostgreSQL ou Redis se sature
> - **Dégradation des performances** : temps de réponse augmente progressivement (GC pressure)
> - **Expiration de tokens** : JWT expirent, le renouvellement échoue
>
> Un test de stress ne dure que 30 min — il passe à côté de ces problèmes."

---

## 13. DevSecOps & CI/CD

---

### C'est quoi DevSecOps et comment tu l'appliques ?

**Réponse :**
> "DevSecOps intègre la sécurité dans chaque étape du pipeline — pas juste à la fin.
>
> Dans ce projet avec GitHub Actions :
> - **SAST** : SonarCloud analyse le code à chaque commit (gratuit pour repo public)
> - **Dependency scanning** : OWASP Dependency-Check contre la base CVE. Une CVE CVSS ≥ 7 bloque le pipeline.
> - **Container scanning** : Trivy analyse l'image Docker après le build ECR — résultats uploadés en SARIF dans GitHub Security tab
> - **IaC scanning** : tfsec analyse les fichiers Terraform (S3 bucket public ? security group 0.0.0.0/0 ?)
> - **Authentification sans secret** : OIDC AWS — aucune clé `AWS_ACCESS_KEY_ID` stockée dans GitHub
>
> La sécurité n'est pas une phase — c'est une propriété de chaque étape."

---

### Pourquoi GitHub Actions et pas GitLab CI ?

**Question en entretien :** *"Tu aurais pu utiliser GitLab CI, pourquoi GitHub Actions ?"*

**Réponse :**
> "Pour ce projet pédagogique, GitHub Actions a trois avantages décisifs :
>
> **1. Visibilité recruteur** : GitHub est le premier endroit qu'un tech lead regarde. Un badge CI vert sur un repo public, c'est un portfolio vivant. GitLab est moins consulté spontanément.
>
> **2. Actions officielles AWS** : l'écosystème GitHub marketplace a des actions maintenues par AWS (`aws-actions/configure-aws-credentials`, `aws-actions/amazon-ecr-login`). Intégration ECR, S3, Lambda en quelques lignes YAML — pas de script shell à écrire.
>
> **3. OIDC AWS natif** : GitHub Actions s'authentifie à AWS sans stocker de clé `AWS_ACCESS_KEY_ID` dans les secrets. GitHub prouve son identité à AWS via un token OIDC éphémère. C'est la pratique de sécurité recommandée par AWS — aucune clé à rotation, aucun risque de fuite.
>
> **Minutes gratuites** : 2 000 minutes/mois sur repo privé (vs 400 pour GitLab). Pour un projet solo, c'est 10× plus de marge."

**Point fort à développer — OIDC sans clé AWS :**
> "Le mécanisme OIDC est identique aux IAM Roles for Service Accounts (IRSA) en Kubernetes. GitHub est un IdP (Identity Provider) reconnu par AWS. Quand le workflow s'exécute, GitHub génère un JWT signé. AWS l'échange contre des credentials STS temporaires valables 1 heure. Aucune clé permanente stockée nulle part."

```
GitHub Actions → JWT OIDC signé → AWS STS AssumeRoleWithWebIdentity
                                → Credentials temporaires (1h)
                                → Actions AWS avec le role défini en Terraform
```

**Contre-argument :** *"Mais GitLab CI est plus utilisé en entreprise."*
> "Exact, surtout dans les grandes entreprises françaises avec du GitLab self-hosted. Je connais les deux : les concepts sont identiques (jobs, stages, artifacts, environments). GitLab CI a un avantage sur les pipelines DAG complexes avec `needs`. Dans un entretien où on me demande GitLab CI, je peux adapter mes connaissances GitHub Actions en 30 minutes. L'inverse est aussi vrai."

**Questions de relance :**
- *"C'est quoi la différence entre `on: push` et `on: workflow_dispatch` ?"*
  > `push` déclenche automatiquement le workflow à chaque push. `workflow_dispatch` permet le déclenchement manuel depuis l'interface GitHub ou l'API — utile pour les tests Gatling ou le `terraform destroy` qui ne doivent pas tourner à chaque commit.
- *"Comment tu protèges le job `terraform destroy` contre un déclenchement accidentel ?"*
  > Via les **GitHub Environments** avec protection. Le workflow `terraform-destroy.yml` pointe vers l'environment `ephemeral` qui exige une approbation manuelle dans GitHub avant d'exécuter le job. Impossible de détruire accidentellement les ressources sans cliquer sur "Approve".

---

### Pourquoi SonarCloud et pas SonarQube self-hosted ?

**Réponse :**
> "SonarQube self-hosted nécessite un serveur (ou un pod Kubernetes) à maintenir, des mises à jour, une base de données. SonarCloud est le SaaS de SonarSource — **gratuit pour les repos GitHub publics**, configuration en 5 minutes via l'action GitHub.
>
> Pour ce projet pédagogique, SonarCloud donne exactement les mêmes Quality Gates que SonarQube : couverture, code smells, vulnérabilités, duplications. Et les résultats apparaissent directement dans la PR GitHub comme commentaires — intégration native."

---

### Pourquoi Terraform et pas du ClickOps (console AWS) ?

**Réponse :**
> "Quatre raisons :
>
> **Reproductibilité** : `terraform apply` recrée exactement le même environnement. Pas de 'ça marchait avant'.
>
> **Versionning** : l'infrastructure est dans Git. Chaque changement est tracé — qui a changé quoi, pourquoi, avec une PR reviewée.
>
> **Destruction propre** : `terraform destroy` supprime exactement ce qu'il a créé. Le ClickOps laisse des ressources orphelines qui facturent.
>
> **Documentation vivante** : le code Terraform est la documentation de l'infrastructure — toujours à jour."

---

## 14. Questions pièges classiques

---

### "Ce projet a trop de technologies. En production tu ferais quoi ?"

> "En production, je partirais du problème. Pour une To-Do List réelle avec une équipe de 5 développeurs :
> - Spring Boot + PostgreSQL + Redis si équipe Java
> - Kafka si plusieurs services doivent réagir aux événements
> - Lambda pour les traitements événementiels ponctuels (upload, notifications)
> - MongoDB seulement si le schéma est vraiment variable
>
> Ici, j'ai tout mis volontairement pour pratiquer. La valeur est dans la capacité à justifier chaque choix — y compris quand le choix est de NE PAS l'utiliser."

---

### "Kafka c'est compliqué à opérer. Pourquoi pas SQS pour tout ?"

> "SQS est plus simple à opérer sur AWS — pas de brokers, scaling automatique. Pour la communication Lambda → Spring Boot dans ce projet, j'utilise justement SQS.
>
> Kafka apporte le replay — SQS ne l'a pas. Si j'ajoute un service d'analytics 6 mois après le lancement, il rejoue tous les événements depuis Kafka. Avec SQS, ces événements sont perdus à la consommation.
>
> Ce sont deux outils complémentaires, pas concurrents."

---

### "Les Virtual Threads, c'est quoi l'avantage par rapport à WebFlux ?"

> "WebFlux impose un modèle de programmation réactif (non-bloquant, chaînes d'opérateurs). Puissant, mais difficile à déboguer et à onboarder pour des développeurs non familiers.
>
> Les Virtual Threads permettent d'écrire du code impératif classique — blocking I/O — avec les performances du non-bloquant. Le code reste lisible et debuggable. La JVM gère la multiplexion.
>
> Pour la grande majorité des APIs REST avec I/O, Virtual Threads sont suffisants et bien moins risqués à maintenir que du WebFlux."

---

### "RDS c'est moins bien qu'Aurora. Pourquoi ne pas utiliser Aurora ?"

> "Aurora est plus performant en production : ×5 vs RDS standard, failover < 30s, scaling ACU. Je le saurais l'utiliser et je le recommanderais pour une application à fort trafic.
>
> Dans ce projet, RDS Free Tier + `terraform destroy` est le bon compromis : je paie $0 la première année, je pratique le même code Spring Data JPA, et j'évite de payer $43/mois si j'oublie de stopper un cluster Aurora. Je sais pourquoi je ne prends pas Aurora — c'est ça le signal en entretien."

---

### "DynamoDB c'est compliqué à modéliser. Pourquoi ne pas tout mettre dans PostgreSQL ?"

> "DynamoDB est différent de PostgreSQL — pas meilleur, pas moins bien. Il excelle pour des patterns d'accès simples et prédictibles par clé primaire, avec des schémas flexibles et un besoin de latence < 10ms.
>
> Les préférences utilisateur correspondent exactement à ce profil. Les mettre dans PostgreSQL fonctionnerait, mais ce serait sous-utiliser DynamoDB et sur-utiliser PostgreSQL.
>
> La difficulté de DynamoDB vient du single-table design pour les relations complexes. Pour un simple lookup par `userId`, c'est immédiat."

---

*Document créé le 10 mai 2026 — Mis à jour le 10 mai 2026 (GitHub Actions + OIDC AWS + SonarCloud + argumentaires complets) — Projet To-Do Enterprise Interview Prep*
