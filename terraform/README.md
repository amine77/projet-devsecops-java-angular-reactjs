# Infrastructure AWS — Guide Terraform

> **Todo Enterprise** · eu-west-3 (Paris) · Compte `583931058666`

Ce guide explique comment provisionner et gérer l'infrastructure AWS du projet avec Terraform. Il couvre l'architecture des deux stacks, les commandes du quotidien, les coûts et le dépannage.

---

## Table des matières

1. [Prérequis](#1-prérequis)
2. [Architecture des deux stacks](#2-architecture-des-deux-stacks)
3. [Bootstrap — fait une seule fois](#3-bootstrap--fait-une-seule-fois)
4. [Stack Permanent — VPC · ECR · IAM](#4-stack-permanent--vpc--ecr--iam)
5. [Stack Ephemeral — EC2 t2.micro + Minikube](#5-stack-ephemeral--ec2-t2micro--minikube)
6. [Routine quotidienne](#6-routine-quotidienne)
7. [Déployer une nouvelle version de l'app](#7-déployer-une-nouvelle-version-de-lapp)
8. [Estimation des coûts](#8-estimation-des-coûts)
9. [Dépannage](#9-dépannage)
10. [Concepts clés à retenir](#10-concepts-clés-à-retenir)

---

## 1. Prérequis

### Outils installés

| Outil | Version minimale | Vérification |
|-------|-----------------|-------------|
| Terraform | 1.9+ | `terraform -version` |
| AWS CLI v2 | 2.x | `aws --version` |
| Docker | 29+ | `docker --version` |

### Authentification AWS

```powershell
# Vérifier qu'on est connecté avec le bon utilisateur
aws sts get-caller-identity
```

Résultat attendu :
```json
{
    "UserId": "AIDA...",
    "Account": "583931058666",
    "Arn": "arn:aws:iam::583931058666:user/todo-terraform-user"
}
```

Si ce n'est pas le bon compte :
```powershell
aws configure
# AWS Access Key ID : <clé de todo-terraform-user>
# AWS Secret Access Key : <secret>
# Default region : eu-west-3
# Default output format : json
```

---

## 2. Architecture des deux stacks

```
terraform/
├── permanent/      ← Créé une fois, JAMAIS détruit
│   ├── vpc.tf          VPC + subnets + IGW + NAT Gateway
│   ├── ecr.tf          3 repos Docker privés (ECR)
│   ├── iam.tf          Rôles IAM (ECS, GitHub Actions OIDC)
│   ├── main.tf         Provider + backend S3
│   ├── variables.tf
│   └── outputs.tf
│
└── ephemeral/      ← Destroy le soir pour économiser
    ├── ec2-minikube.tf  EC2 t2.micro + Minikube (Free Tier)
    ├── scripts/
    │   └── setup-minikube.sh  Installation Docker + Minikube + Helm
    ├── main.tf          Provider + remote state permanent
    ├── variables.tf
    └── outputs.tf
    (ecs.tf, alb.tf, rds.tf, elasticache.tf, security-groups.tf,
     secrets.tf, cloudwatch.tf → désactivés, remplacés par ec2-minikube.tf)
```

### Pourquoi deux stacks ?

**Permanent** = ressources qui coûtent ~0€ quand elles ne tournent pas :
- Un VPC ne coûte rien tant qu'il n'y a pas de trafic
- Des repos ECR coûtent uniquement le stockage des images (~1€/mois)
- Des rôles IAM ne coûtent rien
- ~~NAT Gateway~~ supprimé — voir [Mode économique](#mode-économique--pas-de-nat-gateway) ci-dessous

**Ephemeral** = ressources facturées à l'heure même sans trafic :

| Ressource | Coût heure | Coût si 24/7 | Coût si 8h/j × 5j/sem |
|-----------|-----------|-------------|----------------------|
| RDS db.t3.micro | ~0.018€/h | ~13€/mois | ~3€/mois |
| ElastiCache cache.t3.micro | ~0.017€/h | ~12€/mois | ~2.5€/mois |
| ECS Fargate (3 services) | ~0.03€/h | ~22€/mois | ~5€/mois |
| ALB | ~0.025€/h | ~18€/mois | ~4€/mois |
| **Total** | | **~65€/mois** | **~14€/mois** |

> 💡 **Strategy ephemeral** : `terraform destroy` avant de dormir → économie de 75%.

### Mode économique — pas de NAT Gateway

Le NAT Gateway coûtait ~32€/mois **en permanence**, même sans rien faire tourner. Il a été remplacé par :

- **`assign_public_ip = true`** sur les tâches ECS Fargate : elles sont dans les subnets publics et accèdent directement aux APIs AWS (ECR, CloudWatch, Secrets Manager) sans intermédiaire
- **VPC Endpoint S3 Gateway** (gratuit) : les couches des images Docker transitent dans le réseau AWS plutôt que par internet public

La sécurité est identique : les Security Groups n'autorisent que l'ALB à parler aux conteneurs sur leur port de service. L'IP publique d'une tâche ECS n'est pas accessible de l'extérieur.

```
AVANT (avec NAT)                     APRÈS (sans NAT)
─────────────────────────────        ──────────────────────────────
Subnet privé                         Subnet public
  ECS ──→ NAT Gateway (32€/mois)       ECS (IP publique) ──→ Internet
         ──→ Internet                              ──→ ECR, CloudWatch...
         ──→ ECR, CloudWatch...
```

Pour revenir au mode production avec NAT Gateway : voir [Remettre un NAT Gateway](#quand-remettre-un-nat-gateway).

---

## 3. Bootstrap — fait une seule fois

> ✅ **Ces étapes ont déjà été effectuées** pour ce projet (compte `583931058666`, région `eu-west-3`). Section conservée pour référence.

Le bootstrap crée les ressources **hors Terraform** qui servent justement à stocker l'état de Terraform :

### 3.1 Créer le bucket S3 (état Terraform)

```bash
aws s3api create-bucket \
  --bucket todo-enterprise-tfstate-583931058666 \
  --region eu-west-3 \
  --create-bucket-configuration LocationConstraint=eu-west-3
```

### 3.2 Activer le versioning

```bash
aws s3api put-bucket-versioning \
  --bucket todo-enterprise-tfstate-583931058666 \
  --versioning-configuration Status=Enabled
```

### 3.3 Bloquer tout accès public

```bash
aws s3api put-public-access-block \
  --bucket todo-enterprise-tfstate-583931058666 \
  --public-access-block-configuration \
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
```

### 3.4 Chiffrer le bucket

```bash
aws s3api put-bucket-encryption \
  --bucket todo-enterprise-tfstate-583931058666 \
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {
        "SSEAlgorithm": "AES256"
      }
    }]
  }'
```

### 3.5 Créer la table DynamoDB (verrou du state)

```bash
aws dynamodb create-table \
  --table-name todo-enterprise-tfstate-lock \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region eu-west-3
```

> **Pourquoi DynamoDB ?**  
> Quand deux `terraform apply` se lancent en même temps (ex: deux développeurs, ou un apply manuel + un CI), ils écriraient simultanément dans le même fichier `.tfstate`. DynamoDB joue le rôle de **verrou distribué** : le premier apply verrouille la table, le second attend. Résultat : état Terraform jamais corrompu.

---

## 4. Stack Permanent — VPC · ECR · IAM

### Premier déploiement

```powershell
cd terraform/permanent

# Copier le fichier de variables
Copy-Item terraform.tfvars.example terraform.tfvars

# Initialiser Terraform (télécharge les providers, configure le backend S3)
terraform init

# Vérifier ce qui va être créé SANS rien faire
terraform plan

# Créer les ressources (~3-5 minutes)
terraform apply
```

Répondre `yes` à la confirmation, ou ajouter `-auto-approve` pour les scripts.

### Ce que ça crée

```
aws_vpc.main                          VPC 10.0.0.0/16
aws_subnet.public[0]                  10.0.1.0/24  (eu-west-3a, ALB)
aws_subnet.public[1]                  10.0.2.0/24  (eu-west-3b, ALB)
aws_subnet.private[0]                 10.0.11.0/24 (eu-west-3a, ECS/RDS)
aws_subnet.private[1]                 10.0.12.0/24 (eu-west-3b, ECS/RDS)
aws_internet_gateway.main             IGW → internet
aws_nat_gateway.main                  NAT → internet sortant (subnets privés)
aws_ecr_repository.backend            583931058666.dkr.ecr.eu-west-3.amazonaws.com/todo-backend
aws_ecr_repository.frontend_angular   .../todo-frontend-angular
aws_ecr_repository.frontend_react     .../todo-frontend-react
aws_iam_role.ecs_task_execution       Rôle ECS (pull ECR, logs, secrets)
aws_iam_role.ecs_task                 Rôle App (SES, S3, DynamoDB)
aws_iam_role.github_actions           Rôle OIDC GitHub Actions
aws_iam_openid_connect_provider.github Provider OIDC
```

### Récupérer les outputs après apply

```powershell
terraform output github_actions_role_arn
# → arn:aws:iam::583931058666:role/todo-enterprise-github-actions-role

terraform output ecr_backend_url
# → 583931058666.dkr.ecr.eu-west-3.amazonaws.com/todo-backend
```

### ⚠️ Configurer les secrets GitHub Actions

Après le premier apply permanent, copier ces valeurs dans **GitHub > Settings > Secrets and variables > Actions** :

| Secret GitHub | Valeur (depuis `terraform output`) |
|--------------|-----------------------------------|
| `AWS_ACCOUNT_ID` | `583931058666` |
| `AWS_REGION` | `eu-west-3` |
| `AWS_ROLE_TO_ASSUME` | `terraform output github_actions_role_arn` |

---

## 5. Stack Ephemeral — EC2 t2.micro + Minikube

> **Migration Phase 7** : L'architecture ECS Fargate + RDS + ElastiCache + ALB a été remplacée par une instance EC2 t2.micro (Free Tier) faisant tourner Minikube. Coût : **$0/mois** (an 1) au lieu de ~$66/mois.

### Architecture

```
EC2 t2.micro (1 vCPU, 1 GB RAM + 2 GB swap)
  └── Minikube (--driver=docker)
       ├── Namespace todo-app
       │     ├── Pod todo-backend     (Spring Boot — image ECR)
       │     ├── Pod todo-frontend-angular (Nginx — image ECR)
       │     └── Pod todo-frontend-react   (Nginx — image ECR)
       └── Namespace data
             ├── StatefulSet postgresql  (chart Bitnami)
             └── StatefulSet redis       (chart Bitnami)
```

### Pré-requis : pousser les images Docker dans ECR

Avant le premier `apply`, Minikube a besoin d'images Docker dans ECR.

**Option A — via le CI GitHub Actions** (recommandé) :
Merger une PR sur master → le workflow `cd.yml` build et pousse automatiquement les images.

**Option B — manuellement depuis la machine** :
```powershell
# Authentification ECR
aws ecr get-login-password --region eu-west-3 | `
  docker login --username AWS --password-stdin `
  583931058666.dkr.ecr.eu-west-3.amazonaws.com

# Build + push backend
cd backend
docker build -f infrastructure/Dockerfile -t todo-backend:latest .
docker tag todo-backend:latest `
  583931058666.dkr.ecr.eu-west-3.amazonaws.com/todo-backend:latest
docker push 583931058666.dkr.ecr.eu-west-3.amazonaws.com/todo-backend:latest
```

### Générer la clé SSH (une seule fois)

```bash
ssh-keygen -t ed25519 -C "todo-minikube" -f ~/.ssh/todo-minikube
```

Copier le contenu de `~/.ssh/todo-minikube.pub` dans `terraform.tfvars` :
```hcl
ec2_ssh_public_key = "ssh-ed25519 AAAA..."
```

### Démarrer l'infrastructure (le matin)

```powershell
cd terraform/ephemeral

# Première fois seulement
Copy-Item terraform.tfvars.example terraform.tfvars
# Renseigner ec2_ssh_public_key dans terraform.tfvars
terraform init

# Chaque matin
terraform apply
```

Durée : **2-3 minutes** pour créer l'EC2 + EIP. Puis **5-8 minutes** supplémentaires pour que le script `setup-minikube.sh` installe Docker, Minikube, kubectl, Helm.

### Ce que ça crée

```
data.aws_ami.ubuntu_2404            Ubuntu 24.04 LTS (Canonical officiel)
aws_security_group.minikube         SG : SSH (22), K8s API (6443), HTTP (80), NodePort (30000-32767)
aws_iam_role.ec2_minikube           Rôle IAM → ECR ReadOnly (pull images)
aws_iam_instance_profile.ec2_minikube  Profile IAM attaché à l'EC2
aws_key_pair.minikube               Clé SSH publique
aws_instance.minikube               EC2 t2.micro / Ubuntu 24.04 / EBS 30 GB gp3
aws_eip.minikube                    IP publique fixe (Elastic IP)
```

### Étapes post-apply (à faire une seule fois)

```powershell
# 1. Afficher l'IP et les instructions
terraform output kubectl_config_instructions

# 2. Attendre 8 minutes que Minikube s'installe, puis se connecter
ssh -i ~/.ssh/todo-minikube ubuntu@<IP_PUBLIQUE>

# 3. Vérifier que Minikube tourne
minikube status
kubectl get nodes

# 4. Récupérer le kubeconfig en base64
cat ~/.kube/config | base64 -w0

# 5. Copier dans GitHub Secrets
# Repo → Settings → Secrets and variables → Actions
# → KUBECONFIG_B64 = <valeur base64>
# → EC2_HOST = <IP_PUBLIQUE>
```

### Installer PostgreSQL et Redis sur Minikube

Une fois le kubeconfig configuré :

```bash
# Depuis la machine locale (avec kubectl configuré) ou depuis l'EC2 en SSH

# Ajouter le repo Bitnami
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# PostgreSQL dans le namespace data
helm install postgres bitnami/postgresql \
  --namespace data \
  --create-namespace \
  -f helm/values/postgres-values.yaml \
  --wait

# Redis dans le namespace data
helm install redis bitnami/redis \
  --namespace data \
  --create-namespace \
  -f helm/values/redis-values.yaml \
  --wait
```

### Déployer l'application manuellement

```bash
# Premier déploiement (ou mise à jour)
helm upgrade --install todo-enterprise ./helm/todo-enterprise \
  --namespace todo-app \
  --create-namespace \
  --set ecr.registry=583931058666.dkr.ecr.eu-west-3.amazonaws.com \
  --set backend.image.tag=latest \
  --set frontendAngular.image.tag=latest \
  --set frontendReact.image.tag=latest \
  --wait --atomic
```

### Accéder à l'application

```bash
# Vérifier les pods
kubectl get pods -n todo-app

# Vérifier l'Ingress
kubectl get ingress -n todo-app

# Ajouter dans /etc/hosts (ou C:\Windows\System32\drivers\etc\hosts)
# <IP_PUBLIQUE>  todo.local

# Accéder : http://todo.local
```

### URL d'accès après déploiement

```powershell
terraform output ec2_public_ip
# → X.X.X.X (IP publique fixe)

terraform output ssh_command
# → ssh -i ~/.ssh/todo-minikube ubuntu@X.X.X.X

terraform output kubernetes_api_url
# → https://X.X.X.X:8443
```

### Arrêter l'infrastructure (le soir)

```powershell
cd terraform/ephemeral

# Tout détruire (1-2 minutes)
terraform destroy
```

Répondre `yes`. L'EC2, l'EIP et le Security Group sont supprimés. **Le VPC, ECR et les rôles IAM restent intacts.**

> ⚠️ `terraform destroy` supprime le volume EBS : les données PostgreSQL et Redis sont perdues.  
> Si vous avez des données importantes, faire une sauvegarde avant :
> ```bash
> kubectl exec -n data postgres-postgresql-0 -- \
>   pg_dump -U todouser tododb > backup.sql
> ```

---

## 6. Routine quotidienne

### Matin — Démarrer

```powershell
# 1. Démarrer l'infrastructure (~10 min)
cd terraform/ephemeral
terraform apply -auto-approve

# 2. Vérifier que les services ECS sont healthy
aws ecs describe-services `
  --cluster todo-enterprise-cluster `
  --services todo-backend-service todo-frontend-angular-service todo-frontend-react-service `
  --query "services[*].{Name:serviceName,Running:runningCount,Desired:desiredCount,Status:status}" `
  --output table

# 3. Accéder à l'application
terraform output alb_dns_name
```

### Soir — Arrêter

```powershell
# Couper les services ECS à 0 (arrêt immédiat, pas de facturation Fargate)
# Option 1 : scale à 0 sans destroy (garde RDS actif)
aws ecs update-service --cluster todo-enterprise-cluster --service todo-backend-service --desired-count 0
aws ecs update-service --cluster todo-enterprise-cluster --service todo-frontend-angular-service --desired-count 0
aws ecs update-service --cluster todo-enterprise-cluster --service todo-frontend-react-service --desired-count 0

# Option 2 : destroy complet (économie maximale, RDS aussi arrêté)
cd terraform/ephemeral
terraform destroy -auto-approve
```

> **Option 1 vs Option 2** :
> - Option 1 : RDS continue de tourner (~0.018€/h). Redémarrage ECS en 1-2 min.
> - Option 2 : Tout arrêté. Redémarrage de tout en 10 min. Données RDS perdues.

---

## 7. Déployer une nouvelle version de l'app

### Via GitHub Actions (méthode normale)

```bash
git push origin master
# → CI/CD déclenché automatiquement
# → Images Docker buildées et poussées vers ECR
# → ECS déploie les nouvelles images (rolling update)
```

Suivre l'avancement dans GitHub → Actions → CD — Deploy to AWS.

### Manuellement (pour tester rapidement)

```powershell
# 1. Build + push une image
$IMAGE_TAG = git rev-parse --short HEAD  # SHA court du dernier commit

docker build -f backend/infrastructure/Dockerfile -t todo-backend:$IMAGE_TAG backend/
docker tag todo-backend:$IMAGE_TAG `
  583931058666.dkr.ecr.eu-west-3.amazonaws.com/todo-backend:$IMAGE_TAG
docker push 583931058666.dkr.ecr.eu-west-3.amazonaws.com/todo-backend:$IMAGE_TAG

# 2. Mettre à jour la variable image_tag et ré-appliquer
cd terraform/ephemeral
# Dans terraform.tfvars :
#   backend_image_tag = "abc1234"  ← mettre le nouveau SHA

terraform apply -target=aws_ecs_task_definition.backend -target=aws_ecs_service.backend
```

### Forcer un redéploiement sans changer l'image

```powershell
# Utile si un container est bloqué
aws ecs update-service `
  --cluster todo-enterprise-cluster `
  --service todo-backend-service `
  --force-new-deployment
```

---

## 8. Estimation des coûts

### Coûts permanents (toujours facturés)

| Ressource | Coût mensuel |
|-----------|-------------|
| ~~NAT Gateway~~ | ~~32€~~ → **supprimé** |
| VPC Endpoint S3 Gateway | **0€** (gratuit) |
| ECR stockage (~3 images × 300 MB) | ~0.09€ |
| **Total permanent** | **~0€/mois** |

> Sans NAT Gateway, le stack permanent ne coûte pratiquement rien.

### Coûts ephemeral — AVANT (ECS Fargate + RDS + ALB)

| Ressource | Coût/heure | 8h × 5j/sem | 24h/7j/sem |
|-----------|-----------|-------------|-----------|
| RDS db.t3.micro | 0.018€ | ~2.9€ | ~13€ |
| ElastiCache cache.t3.micro | 0.017€ | ~2.7€ | ~12€ |
| ECS Fargate backend (0.25 vCPU, 0.5GB) | ~0.013€ | ~2.1€ | ~9€ |
| ECS Fargate angular (0.25 vCPU, 0.25GB) | ~0.010€ | ~1.6€ | ~7€ |
| ECS Fargate react (0.25 vCPU, 0.25GB) | ~0.010€ | ~1.6€ | ~7€ |
| ALB | 0.025€ | ~4€ | ~18€ |
| **Total ancien stack** | | **~15€/mois** | **~66€/mois** |

### Coûts ephemeral — APRÈS (EC2 t2.micro + Minikube)

| Ressource | Coût | Détail |
|-----------|------|--------|
| EC2 t2.micro | **$0/mois** | Free Tier : 750h/mois (an 1) |
| EBS 30 GB gp3 | **$0/mois** | Free Tier : 30 GB/mois (an 1) |
| Elastic IP (associée) | **$0/mois** | Gratuit si instance en cours d'exécution |
| PostgreSQL (pod Minikube) | **$0** | Inclus dans l'EC2 |
| Redis (pod Minikube) | **$0** | Inclus dans l'EC2 |
| **Total nouveau stack (an 1)** | | **$0/mois** |
| **Total nouveau stack (an 2+)** | | **~$11.50/mois** |

> **Attention EIP** : si l'EC2 est arrêté sans `terraform destroy`, l'EIP coûte ~$0.005/h (~$3.60/mois). Toujours faire `terraform destroy` pour libérer l'EIP.

### Comparaison avant/après

```
Ancien stack (ECS + RDS + ALB) :  ~66€/mois (24h/7j)
Nouveau stack (EC2 Minikube) :    $0/mois (an 1)   → économie de 100% (Free Tier)
                                  ~11.50$/mois (an 2+) → économie de 83%
```

### Alertes de budget AWS (recommandé)

```bash
# Créer une alerte si les coûts dépassent 10$/mois (détecte tout dépassement Free Tier)
aws budgets create-budget \
  --account-id 583931058666 \
  --budget '{
    "BudgetName": "todo-enterprise-monthly",
    "BudgetLimit": {"Amount": "10", "Unit": "USD"},
    "TimeUnit": "MONTHLY",
    "BudgetType": "COST"
  }' \
  --notifications-with-subscribers '[{
    "Notification": {
      "NotificationType": "ACTUAL",
      "ComparisonOperator": "GREATER_THAN",
      "Threshold": 80
    },
    "Subscribers": [{"SubscriptionType": "EMAIL", "Address": "amine.charrad@gmail.com"}]
  }]'
```

---

## 9. Dépannage

### `terraform init` échoue — bucket S3 introuvable

```
Error: Failed to get existing workspaces: S3 bucket does not exist.
```

**Cause** : le bucket S3 de bootstrap n'existe pas encore ou le nom est incorrect.  
**Solution** : vérifier que le bootstrap (section 3) a été fait, et que le nom correspond exactement à `todo-enterprise-tfstate-583931058666`.

---

### `terraform apply` échoue — `OptimisticLockException`

```
Error: error locking state: ConditionalCheckFailedException
```

**Cause** : un apply précédent s'est mal terminé et le verrou DynamoDB est resté bloqué.  
**Solution** :
```bash
# Forcer la suppression du verrou
aws dynamodb delete-item \
  --table-name todo-enterprise-tfstate-lock \
  --key '{"LockID": {"S": "todo-enterprise-tfstate-583931058666/ephemeral/terraform.tfstate"}}'
```

---

### Les services ECS restent en `PENDING` indéfiniment

**Diagnostics** :
```powershell
# 1. Voir les événements ECS
aws ecs describe-services \
  --cluster todo-enterprise-cluster \
  --services todo-backend-service \
  --query "services[0].events[:5]"

# 2. Voir les tâches arrêtées (et la raison)
aws ecs list-tasks \
  --cluster todo-enterprise-cluster \
  --service-name todo-backend-service \
  --desired-status STOPPED \
  --query "taskArns"

# Puis pour chaque ARN :
aws ecs describe-tasks \
  --cluster todo-enterprise-cluster \
  --tasks <ARN> \
  --query "tasks[0].stoppedReason"
```

**Causes fréquentes** :
| Erreur | Cause | Solution |
|--------|-------|---------|
| `CannotPullContainerError` | Image inexistante dans ECR | Pousser l'image d'abord (section 5) |
| `ResourceInitializationError` | Pas d'accès à Secrets Manager | Vérifier le rôle IAM `ecs_task_execution_role` |
| `OutOfMemoryError` | Container trop gourmand | Augmenter `backend_memory` dans `terraform.tfvars` |
| `Essential container exited` | App crash au démarrage | Voir les logs CloudWatch |

---

### Voir les logs de l'application

```powershell
# Logs backend en temps réel (équivalent docker logs -f)
aws logs tail /ecs/todo-enterprise/backend --follow

# Derniers 100 messages
aws logs get-log-events \
  --log-group-name /ecs/todo-enterprise/backend \
  --log-stream-name "ecs/todo-backend/$(aws ecs list-tasks --cluster todo-enterprise-cluster --service-name todo-backend-service --query 'taskArns[0]' --output text | awk -F/ '{print $NF}')" \
  --limit 100
```

---

### Le health check ALB échoue (service marqué `unhealthy`)

```powershell
# Vérifier les target groups
aws elbv2 describe-target-health \
  --target-group-arn $(aws elbv2 describe-target-groups \
    --names todo-backend-tg \
    --query "TargetGroups[0].TargetGroupArn" \
    --output text)
```

**Causes fréquentes** :
- Spring Boot n'a pas encore fini de démarrer (attendre le `startPeriod` de 60s)
- Le security group ECS ne permet pas le trafic depuis l'ALB sur le port 8080
- L'endpoint `/actuator/health` retourne autre chose que `200 OK`

---

### `terraform destroy` bloqué sur RDS

RDS a une protection `deletion_protection = false`, mais le destroy peut prendre jusqu'à **15 minutes**. C'est normal, laisser tourner.

Si vraiment bloqué :
```bash
aws rds delete-db-instance \
  --db-instance-identifier todo-enterprise-db \
  --skip-final-snapshot \
  --delete-automated-backups
```

---

## 10. Concepts clés à retenir

### Remote State & `terraform_remote_state`

Le stack **ephemeral** lit les outputs du stack **permanent** via :

```hcl
data "terraform_remote_state" "permanent" {
  backend = "s3"
  config = {
    bucket = "todo-enterprise-tfstate-583931058666"
    key    = "permanent/terraform.tfstate"
    region = "eu-west-3"
  }
}

# Utilisation
vpc_id = data.terraform_remote_state.permanent.outputs.vpc_id
```

Cela évite de hardcoder des IDs AWS qui changent à chaque recréation.

---

### IAM OIDC — Zéro clé statique pour GitHub Actions

Au lieu de stocker `AWS_ACCESS_KEY_ID` + `AWS_SECRET_ACCESS_KEY` dans GitHub Secrets (qui peuvent fuiter), on utilise OpenID Connect :

```
GitHub Actions
  │── génère un JWT signé par GitHub OIDC provider
  │── envoie le JWT à AWS STS (AssumeRoleWithWebIdentity)
  └── reçoit des credentials temporaires (15 min de validité)
         └── IAM vérifie : sub = "repo:amine77/projet-devsecops-java-angular-reactjs:ref:refs/heads/master"
```

Configuration Terraform correspondante :
```hcl
condition {
  test     = "StringLike"
  variable = "token.actions.githubusercontent.com:sub"
  values   = ["repo:amine77/projet-devsecops-java-angular-reactjs:ref:refs/heads/master"]
}
```

---

### ECS Rolling Deployment — Zero Downtime

Quand on met à jour une task definition, ECS ne coupe pas l'ancien container avant que le nouveau soit healthy :

```
État initial :  [v1] [v1]
Déploiement :   [v1] [v1] [v2]   ← nouveau lancé
Health check :  [v1] [v1] [v2✓]  ← nouveau healthy
Suppression :   [v1]      [v2✓]  ← ancien supprimé
Final :              [v2] [v2]
```

Paramètres Terraform correspondants :
```hcl
deployment_minimum_healthy_percent = 50   # tolère 1 instance en moins
deployment_maximum_percent         = 200  # accepte 2× le desired count
```

---

### Secrets Manager — Injection dans ECS

Les secrets ne passent **jamais** en clair dans les variables d'environnement Docker. ECS les lit depuis Secrets Manager au démarrage et les injecte dans le process :

```hcl
# Dans la task definition ECS
secrets = [
  {
    name      = "SPRING_DATASOURCE_PASSWORD"
    valueFrom = "arn:aws:secretsmanager:eu-west-3:583931058666:secret:todo-enterprise/database:password::"
  }
]
```

Format de l'ARN : `<secret_arn>:<json_key>::`  
Le rôle `ecs_task_execution_role` doit avoir `secretsmanager:GetSecretValue` sur cet ARN.

---

---

### Quand remettre un NAT Gateway ?

Pour passer en production avec des tâches ECS dans des subnets **privés** (recommandé si conformité stricte) :

**1. Dans `terraform/permanent/vpc.tf`**, décommenter :
```hcl
resource "aws_eip" "nat" {
  domain     = "vpc"
  depends_on = [aws_internet_gateway.main]
}

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id
  depends_on    = [aws_internet_gateway.main]
}
```

Et ajouter la route dans `aws_route_table.private` :
```hcl
route {
  cidr_block     = "0.0.0.0/0"
  nat_gateway_id = aws_nat_gateway.main.id
}
```

**2. Dans `terraform/ephemeral/ecs.tf`**, modifier les 3 services :
```hcl
network_configuration {
  subnets          = local.private_subnet_ids   # ← privés
  assign_public_ip = false                       # ← pas d'IP publique
}
```

**3. Appliquer** :
```bash
terraform apply   # dans permanent/ puis ephemeral/
```

Coût ajouté : ~32€/mois (1 NAT Gateway). Pour la HA en prod : 2 NAT Gateways (~64€/mois).

---

*Mis à jour : Phase 7 — Migration ECS → EC2 Minikube (Free Tier, $0/mois)*
