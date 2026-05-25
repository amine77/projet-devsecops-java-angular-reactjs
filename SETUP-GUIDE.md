# Guide de mise en place — Todo Enterprise DevSecOps
## Du compte AWS vierge au premier déploiement automatique

> **Public cible** : développeur qui repart de zéro.
> Ce guide suppose que vous n'avez pas encore de compte AWS, pas de clé SSH dédiée, et que le repo est déjà cloné localement.

---

## Table des matières

1. [Architecture et coûts](#1-architecture-et-coûts)
2. [Outils à installer en local](#2-outils-à-installer-en-local)
3. [Créer et configurer le compte AWS](#3-créer-et-configurer-le-compte-aws)
4. [Forker / cloner le repo et adapter les variables](#4-forker--cloner-le-repo-et-adapter-les-variables)
5. [Bootstrap Terraform — créer le backend S3 manuellement](#5-bootstrap-terraform--créer-le-backend-s3-manuellement)
6. [Stack Terraform permanent — VPC, ECR, IAM](#6-stack-terraform-permanent--vpc-ecr-iam)
7. [Configurer les Secrets GitHub](#7-configurer-les-secrets-github)
8. [Stack Terraform éphémère — EC2 Minikube](#8-stack-terraform-éphémère--ec2-minikube)
9. [Post-installation EC2 : récupérer le kubeconfig](#9-post-installation-ec2--récupérer-le-kubeconfig)
10. [Premier déploiement via GitHub Actions](#10-premier-déploiement-via-github-actions)
11. [Vérifier que tout fonctionne](#11-vérifier-que-tout-fonctionne)
12. [Opérations quotidiennes](#12-opérations-quotidiennes)
13. [Dépannage](#13-dépannage)

---

## 1. Architecture et coûts

```
GitHub
  ├── CI : build, tests, scan sécurité (gratuit)
  └── CD : build Docker → push ECR → helm upgrade → Minikube

AWS (eu-west-3 — Paris)
  ├── ECR : 3 repos Docker privés (~0 €/mois pour quelques images)
  ├── EC2 t2.micro : 1 vCPU, 1 GB RAM + 2 GB swap   ← FREE TIER 750h/mois
  │     └── Minikube (Kubernetes mono-nœud)
  │           ├── namespace todo-app
  │           │     ├── Pod Spring Boot (backend)
  │           │     ├── Pod Angular (nginx)
  │           │     └── Pod React (nginx)
  │           └── namespace data
  │                 ├── StatefulSet PostgreSQL (Bitnami chart)
  │                 └── StatefulSet Redis (Bitnami chart)
  └── Elastic IP : IP publique fixe pour le kubeconfig
```

| Service | Free Tier | Après 12 mois |
|---------|-----------|----------------|
| EC2 t2.micro | **0 €/mois** | ~11 €/mois |
| EBS 30 GB gp3 | **0 €/mois** | ~2.40 €/mois |
| Elastic IP (associée) | **0 €/mois** | **0 €/mois** |
| ECR (~3 GB images) | ~0.30 €/mois | ~0.30 €/mois |
| **TOTAL** | **~0 €/mois** | **~14 €/mois** |

> ⚠️ **Important** : si vous éteignez l'EC2, l'Elastic IP non associée coûte ~0.005 $/h (~3.60 $/mois). Faites `terraform destroy` si vous arrêtez le projet.

---

## 2. Outils à installer en local

### Windows (PowerShell en administrateur)

```powershell
# Winget — gestionnaire de paquets Windows (inclus dans Windows 11)
winget install --id Hashicorp.Terraform    --accept-source-agreements
winget install --id Amazon.AWSCLI          --accept-source-agreements
winget install --id Git.Git                --accept-source-agreements
winget install --id OpenJS.NodeJS.LTS      --accept-source-agreements   # Node 22
winget install --id Microsoft.OpenJDK.21   --accept-source-agreements   # Java 21
winget install --id Apache.Maven           --accept-source-agreements
winget install --id Docker.DockerDesktop   --accept-source-agreements
```

> Relancez PowerShell après chaque installation pour recharger le PATH.

### Vérifier les versions minimales requises

```powershell
terraform --version   # >= 1.9
aws --version         # >= 2.x
git --version         # >= 2.x
node --version        # v22.x
java --version        # openjdk 21
mvn --version         # >= 3.9
docker --version      # >= 25.x (Docker Desktop doit être démarré)
```

---

## 3. Créer et configurer le compte AWS

### 3.1 Créer le compte

1. Aller sur **https://aws.amazon.com/fr/** → **Créer un compte AWS**
2. Renseigner email, mot de passe, nom du compte
3. Choisir **Compte personnel** (Free Tier)
4. Renseigner une carte bancaire (nécessaire, mais rien ne sera facturé dans les limites Free Tier)
5. Vérifier le numéro de téléphone
6. Choisir le plan **Gratuit (Basic)**
7. Connexion à la console : https://console.aws.amazon.com

### 3.2 Récupérer votre Account ID

Dans la console AWS, cliquez sur votre nom en haut à droite → **Mon compte**.

Notez votre **Account ID** (12 chiffres). Vous en aurez besoin souvent.

```
Exemple : 583931058666
```

### 3.3 Créer un utilisateur IAM pour Terraform (CLI)

> Ne jamais utiliser le compte root pour Terraform. Créer un utilisateur IAM avec des droits limités.

1. Console AWS → **IAM** → **Utilisateurs** → **Créer un utilisateur**
2. Nom : `terraform-admin`
3. Cocher **Fournir un accès à l'utilisateur à la AWS Management Console** : Non
4. Permissions : attacher la politique **AdministratorAccess** (acceptable pour le bootstrap initial — à restreindre ensuite)
5. Créer → **Créer une clé d'accès** → **Interface de ligne de commande (CLI)**
6. Télécharger le fichier CSV ou noter :
   - `AWS_ACCESS_KEY_ID`
   - `AWS_SECRET_ACCESS_KEY`

### 3.4 Configurer AWS CLI

```powershell
aws configure
# AWS Access Key ID     : <votre AWS_ACCESS_KEY_ID>
# AWS Secret Access Key : <votre AWS_SECRET_ACCESS_KEY>
# Default region name   : eu-west-3
# Default output format : json
```

Vérification :
```powershell
aws sts get-caller-identity
# Doit afficher votre Account ID et le nom d'utilisateur terraform-admin
```

---

## 4. Forker / cloner le repo et adapter les variables

### 4.1 Forker le repo

1. Aller sur https://github.com/amine77/projet-devsecops-java-angular-reactjs
2. Cliquer **Fork** → choisir votre compte GitHub personnel
3. Cloner votre fork :

```powershell
git clone https://github.com/<VOTRE_USERNAME>/projet-devsecops-java-angular-reactjs.git
cd projet-devsecops-java-angular-reactjs
```

### 4.2 Adapter les variables à votre compte

Remplacer partout `583931058666` (Account ID d'origine) et `amine77` (username d'origine) par vos valeurs.

**Fichier `terraform/permanent/variables.tf`** :
```hcl
variable "aws_account_id" {
  default = "<VOTRE_ACCOUNT_ID>"   # ex: 123456789012
}

variable "github_org" {
  default = "<VOTRE_USERNAME_GITHUB>"
}

variable "github_repo" {
  default = "projet-devsecops-java-angular-reactjs"
}
```

**Fichier `terraform/permanent/main.tf`** — backend S3 :
```hcl
backend "s3" {
  bucket         = "todo-enterprise-tfstate-<VOTRE_ACCOUNT_ID>"
  key            = "permanent/terraform.tfstate"
  region         = "eu-west-3"
  dynamodb_table = "todo-enterprise-tfstate-lock"
  encrypt        = true
}
```

**Fichier `terraform/ephemeral/main.tf`** — backend S3 :
```hcl
backend "s3" {
  bucket = "todo-enterprise-tfstate-<VOTRE_ACCOUNT_ID>"
  key    = "ephemeral/terraform.tfstate"
  region = "eu-west-3"
  # ...
}
```

**Fichier `helm/todo-enterprise/values.yaml`** :
```yaml
ecr:
  registry: "<VOTRE_ACCOUNT_ID>.dkr.ecr.eu-west-3.amazonaws.com"
```

> 💡 **Astuce** : utilisez la recherche globale de votre éditeur (`Ctrl+Shift+H`) pour remplacer `583931058666` → votre Account ID, et `amine77` → votre username GitHub.

---

## 5. Bootstrap Terraform — créer le backend S3 manuellement

> Cette étape crée le bucket S3 et la table DynamoDB qui stockeront l'état Terraform. Elle se fait **une seule fois**, manuellement, car Terraform ne peut pas gérer son propre backend.

```powershell
# Variables — adaptez ces valeurs
$ACCOUNT_ID = "<VOTRE_ACCOUNT_ID>"    # ex: 123456789012
$REGION     = "eu-west-3"
$BUCKET     = "todo-enterprise-tfstate-$ACCOUNT_ID"
$TABLE      = "todo-enterprise-tfstate-lock"
```

### Étape 1 : Créer le bucket S3

```powershell
aws s3api create-bucket `
  --bucket $BUCKET `
  --region $REGION `
  --create-bucket-configuration LocationConstraint=$REGION
```

### Étape 2 : Activer le versioning (permet de revenir à un état précédent)

```powershell
aws s3api put-bucket-versioning `
  --bucket $BUCKET `
  --versioning-configuration Status=Enabled
```

### Étape 3 : Chiffrer le bucket (bonne pratique)

```powershell
aws s3api put-bucket-encryption `
  --bucket $BUCKET `
  --server-side-encryption-configuration '{
    "Rules": [{
      "ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "AES256"}
    }]
  }'
```

### Étape 4 : Bloquer l'accès public (sécurité)

```powershell
aws s3api put-public-access-block `
  --bucket $BUCKET `
  --public-access-block-configuration `
    "BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true"
```

### Étape 5 : Créer la table DynamoDB (verrouillage du state)

```powershell
aws dynamodb create-table `
  --table-name $TABLE `
  --attribute-definitions AttributeName=LockID,AttributeType=S `
  --key-schema AttributeName=LockID,KeyType=HASH `
  --billing-mode PAY_PER_REQUEST `
  --region $REGION
```

### Vérification

```powershell
aws s3 ls | Select-String $BUCKET
aws dynamodb describe-table --table-name $TABLE --region $REGION --query "Table.TableStatus"
# Doit afficher "ACTIVE"
```

---

## 6. Stack Terraform permanent — VPC, ECR, IAM

Cette stack crée les ressources qui ne sont **jamais** détruites : réseau, repos ECR, rôles IAM.

```powershell
cd terraform/permanent
```

### Initialiser Terraform

```powershell
terraform init
# Télécharge les providers AWS et configure le backend S3
# Doit afficher : "Terraform has been successfully initialized!"
```

### Vérifier le plan

```powershell
terraform plan
# Doit afficher une liste de ressources à créer (~15 ressources)
# Vérifiez qu'il n'y a PAS de ressources à détruire
```

### Appliquer

```powershell
terraform apply
# Tapez "yes" quand demandé
# Durée : ~3-5 minutes
```

### Récupérer les outputs importants

```powershell
terraform output github_actions_role_arn
# Exemple : arn:aws:iam::123456789012:role/todo-enterprise-github-actions-role
# → Notez cette valeur pour la mettre dans les Secrets GitHub

terraform output ecr_registry
# Exemple : 123456789012.dkr.ecr.eu-west-3.amazonaws.com
```

---

## 7. Configurer les Secrets GitHub

Dans votre repo GitHub → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.

### Secrets obligatoires

| Nom du secret | Valeur | Où trouver |
|---------------|--------|------------|
| `AWS_ACCOUNT_ID` | `123456789012` | Console AWS → Mon compte |
| `AWS_REGION` | `eu-west-3` | Fixe (Paris) |
| `AWS_ROLE_TO_ASSUME` | `arn:aws:iam::123456789012:role/todo-enterprise-github-actions-role` | `terraform output github_actions_role_arn` |
| `VITE_API_URL` | `http://<EC2_IP>/api` | Après étape 8 |
| `VITE_OIDC_AUTHORITY` | `http://<EC2_IP>/auth/realms/todo` | Après étape 8 |
| `KUBECONFIG_B64` | (contenu base64 du kubeconfig) | Après étape 9 |

> **Note** : `VITE_API_URL` et `VITE_OIDC_AUTHORITY` peuvent être mis avec une valeur temporaire (`http://localhost/api`) pour le premier push — vous les mettrez à jour après avoir récupéré l'IP EC2.

### Vérifier l'OIDC

Pour que GitHub Actions puisse s'authentifier à AWS via OIDC (sans clés statiques), le rôle IAM doit être configuré pour accepter les tokens du repo GitHub. La stack Terraform permanent a fait ça automatiquement via `iam.tf`.

Vérification dans la console AWS :
1. IAM → Rôles → `todo-enterprise-github-actions-role`
2. Onglet **Relations de confiance**
3. Vous devez voir `token.actions.githubusercontent.com` et votre repo GitHub dans les conditions

---

## 8. Stack Terraform éphémère — EC2 Minikube

Cette stack crée l'EC2 t2.micro qui fera tourner Minikube.

### 8.1 Générer une clé SSH dédiée

```powershell
# Créer le dossier .ssh si nécessaire
New-Item -ItemType Directory -Force -Path "$env:USERPROFILE\.ssh"

# Générer la paire de clés
ssh-keygen -t ed25519 -C "todo-minikube" -f "$env:USERPROFILE\.ssh\todo-minikube"
# Appuyez Entrée deux fois (pas de passphrase pour simplifier)

# Afficher la clé publique (vous en aurez besoin dans la prochaine étape)
Get-Content "$env:USERPROFILE\.ssh\todo-minikube.pub"
# Exemple : ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIBx... todo-minikube
```

### 8.2 Créer terraform.tfvars

```powershell
cd d:\DEV\projets\projet-devsecops-java-angular-reactjs\terraform\ephemeral

Copy-Item terraform.tfvars.example terraform.tfvars
```

Éditer `terraform/ephemeral/terraform.tfvars` :

```hcl
environment = "dev"

# Coller le contenu de ~/.ssh/todo-minikube.pub ici :
ec2_ssh_public_key = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA... todo-minikube"

ec2_instance_type = "t2.micro"
```

### 8.3 Initialiser et appliquer

```powershell
cd terraform/ephemeral

terraform init
terraform plan
terraform apply
# Tapez "yes"
# Durée : ~2 minutes (l'instance se crée, mais Minikube met ~8 min à se configurer)
```

### 8.4 Récupérer l'IP publique

```powershell
terraform output ec2_public_ip
# Exemple : 15.188.42.73
# → Notez cette IP, vous en aurez besoin pour les secrets GitHub et le fichier hosts

terraform output ssh_command
# Exemple : ssh -i ~/.ssh/todo-minikube ubuntu@15.188.42.73
```

### 8.5 Mettre à jour les Secrets GitHub avec l'IP réelle

Dans GitHub → Settings → Secrets :
- `VITE_API_URL` → `http://15.188.42.73/api`
- `VITE_OIDC_AUTHORITY` → `http://15.188.42.73/auth/realms/todo`

---

## 9. Post-installation EC2 : récupérer le kubeconfig

> ⏱️ **Attendez ~8 minutes** après le `terraform apply` avant de continuer. L'EC2 installe Docker, Minikube, kubectl, Helm et démarre le cluster au premier boot.

### 9.1 Vérifier les logs d'installation

```powershell
# Se connecter en SSH
ssh -i "$env:USERPROFILE\.ssh\todo-minikube" ubuntu@<EC2_IP>

# Suivre les logs d'installation (si encore en cours)
tail -f /var/log/setup-minikube.log

# Attendre le message :
# === Installation terminée ===
# === Minikube prêt sur X.X.X.X ===
```

### 9.2 Vérifier que Minikube tourne

```bash
# Sur l'EC2 (en SSH)
minikube status
# Doit afficher :
# host: Running
# kubelet: Running
# apiserver: Running
# kubeconfig: Configured

kubectl get nodes
# NAME       STATUS   ROLES           AGE   VERSION
# minikube   Ready    control-plane   Xm    v1.30.0
```

### 9.3 Récupérer le kubeconfig en base64

```bash
# Sur l'EC2 (en SSH)
cat ~/.kube/config | base64 -w0
# Affiche une longue chaîne base64 — copier TOUT le texte
```

### 9.4 Ajouter le kubeconfig dans GitHub Secrets

1. GitHub → Settings → Secrets and variables → Actions → **New repository secret**
2. Nom : `KUBECONFIG_B64`
3. Valeur : coller le texte base64 complet

### 9.5 Quitter la session SSH

```bash
exit
```

---

## 10. Premier déploiement via GitHub Actions

### 10.1 Déclencher le pipeline CD

```powershell
# Sur votre machine locale, dans le dossier du projet
git add .
git commit -m "chore: premier déploiement"
git push origin master
```

> Le workflow `CD — Deploy to AWS (Minikube)` se déclenche automatiquement sur chaque push vers `master`.

### 10.2 Suivre l'exécution

1. Aller sur votre repo GitHub → **Actions**
2. Cliquer sur le workflow **CD — Deploy to AWS (Minikube)**
3. Observer les 3 jobs :
   - **Build et Push vers ECR** (~5-10 min) — build les 3 images Docker et les pousse vers ECR
   - **Deployer sur Minikube (EC2)** (~3-5 min) — helm upgrade sur l'EC2
   - **Notification** (~10 sec) — résumé du déploiement

### 10.3 Jobs CI qui tournent en parallèle

Simultanément, les workflows CI s'exécutent :
- **CI Backend** — tests unitaires, tests d'intégration, SonarCloud, OWASP, build Docker
- **CI Frontend** — TypeScript check, ESLint, tests Vitest, build production, build Docker
- **Security Scan** — Trivy filesystem + Docker, CodeQL

---

## 11. Vérifier que tout fonctionne

### 11.1 Vérifier les pods Kubernetes

```powershell
# Se connecter en SSH à l'EC2
ssh -i "$env:USERPROFILE\.ssh\todo-minikube" ubuntu@<EC2_IP>

# Voir tous les pods applicatifs
kubectl get pods -n todo-app
# NAME                                       READY   STATUS    RESTARTS
# todo-enterprise-backend-xxxxxxxxx-xxxxx    1/1     Running   0
# todo-enterprise-angular-xxxxxxxxx-xxxxx    1/1     Running   0
# todo-enterprise-react-xxxxxxxxx-xxxxx      1/1     Running   0

# Voir les pods de données
kubectl get pods -n data
# NAME                        READY   STATUS    RESTARTS
# postgres-postgresql-0       1/1     Running   0
# redis-master-0              1/1     Running   0

# Voir l'Ingress (point d'entrée HTTP)
kubectl get ingress -n todo-app
# NAME                          CLASS   HOSTS       ADDRESS       PORTS
# todo-enterprise-ingress       nginx   todo.local   192.168.x.x   80
```

### 11.2 Accéder à l'application

L'application utilise NGINX Ingress avec le host `todo.local`. Pour y accéder :

**Option A — NodePort direct (le plus simple)** :
```bash
# Sur l'EC2
INGRESS_IP=$(kubectl get svc ingress-nginx-controller -n ingress-nginx -o jsonpath='{.spec.clusterIP}')
# Ou accéder via l'IP NodePort :
kubectl get svc -n ingress-nginx
```

**Option B — Port-forward depuis votre machine locale** :
```powershell
# Sur votre machine, forward le port 80 de l'EC2 vers localhost:8080
ssh -i "$env:USERPROFILE\.ssh\todo-minikube" -L 8080:localhost:80 ubuntu@<EC2_IP> -N
# Puis ouvrir http://localhost:8080 dans votre navigateur
```

**Option C — NodePort via l'IP publique EC2** :
```bash
# Sur l'EC2
kubectl port-forward -n ingress-nginx svc/ingress-nginx-controller 8080:80 --address 0.0.0.0 &
# Puis ouvrir http://<EC2_IP>:8080 dans votre navigateur
```

### 11.3 Vérifier les logs en cas de problème

```bash
# Logs du backend Spring Boot
kubectl logs -n todo-app deployment/todo-enterprise-backend --tail=50

# Logs de l'Ingress Controller
kubectl logs -n ingress-nginx deployment/ingress-nginx-controller --tail=30

# Describe un pod qui ne démarre pas
kubectl describe pod -n todo-app <nom-du-pod>

# Voir les events Kubernetes (erreurs d'ImagePull, OOMKill, etc.)
kubectl get events -n todo-app --sort-by='.lastTimestamp'
```

---

## 12. Opérations quotidiennes

### Démarrer une session de travail

```powershell
# L'EC2 est éteint (pour économiser) → le redémarrer
aws ec2 start-instances --instance-ids <INSTANCE_ID> --region eu-west-3

# Attendre ~2 min pour que l'instance soit prête
aws ec2 wait instance-running --instance-ids <INSTANCE_ID> --region eu-west-3

# Récupérer la nouvelle IP (l'EIP reste fixe → même IP)
aws ec2 describe-addresses --region eu-west-3 --query "Addresses[0].PublicIp"

# Vérifier Minikube
ssh -i "$env:USERPROFILE\.ssh\todo-minikube" ubuntu@<EC2_IP> "minikube status"
```

> 💡 **L'Elastic IP (EIP) reste fixe** même quand l'EC2 est arrêté → le kubeconfig dans GitHub reste valide. Pas besoin de le mettre à jour à chaque redémarrage.

### Arrêter l'EC2 le soir (économies)

```powershell
# Récupérer l'ID de l'instance
$INSTANCE_ID = aws ec2 describe-instances `
  --filters "Name=tag:Name,Values=todo-minikube" `
  --query "Reservations[0].Instances[0].InstanceId" `
  --region eu-west-3 --output text

# Arrêter l'instance (EBS et EIP restent alloués → l'état est préservé)
aws ec2 stop-instances --instance-ids $INSTANCE_ID --region eu-west-3
```

### Déployer après un changement de code

```powershell
# Simplement pousser sur master — le pipeline CD se déclenche automatiquement
git add .
git commit -m "feat: ma nouvelle fonctionnalité"
git push origin master
```

### Détruire toute l'infrastructure (fin de projet)

```powershell
# 1. Détruire l'EC2 (stack éphémère)
cd terraform/ephemeral
terraform destroy
# Tapez "yes"

# 2. Détruire le réseau et les repos ECR (stack permanente)
# ⚠️ Les repos ECR doivent être vides pour être supprimés
# Vider les repos ECR en premier :
foreach ($repo in @("todo-backend", "todo-frontend-angular", "todo-frontend-react")) {
  $images = aws ecr list-images --repository-name $repo --region eu-west-3 --query "imageIds" --output json
  if ($images -ne "[]") {
    aws ecr batch-delete-image --repository-name $repo --region eu-west-3 --image-ids $images
  }
}

cd ../permanent
terraform destroy
# Tapez "yes"

# 3. Supprimer le backend S3 manuellement (Terraform ne gère pas son propre backend)
$ACCOUNT_ID = "<VOTRE_ACCOUNT_ID>"
aws s3 rm s3://todo-enterprise-tfstate-$ACCOUNT_ID --recursive
aws s3api delete-bucket --bucket todo-enterprise-tfstate-$ACCOUNT_ID --region eu-west-3
aws dynamodb delete-table --table-name todo-enterprise-tfstate-lock --region eu-west-3
```

---

## 13. Dépannage

### Problème : `terraform apply` échoue avec "AccessDenied"

```
Error: creating S3 bucket: AccessDenied
```

**Cause** : l'utilisateur IAM `terraform-admin` n'a pas les droits suffisants.

**Solution** : vérifier la politique IAM attachée → elle doit être `AdministratorAccess` pour le bootstrap.

---

### Problème : GitHub Actions échoue avec "Unable to get OIDC token"

```
Error: Unable to get OIDC token: Error making request
```

**Causes possibles** :
1. La stack permanent Terraform n'a pas été appliquée (le provider OIDC GitHub n'existe pas)
2. Le secret `AWS_ROLE_TO_ASSUME` est vide ou incorrect
3. Le rôle IAM n'autorise pas le repo GitHub exact

**Vérification** :
```powershell
# Vérifier que le rôle existe
aws iam get-role --role-name todo-enterprise-github-actions-role --region eu-west-3

# Vérifier la Trust Policy
aws iam get-role --role-name todo-enterprise-github-actions-role `
  --query "Role.AssumeRolePolicyDocument" --output json
# Doit contenir votre username/repo GitHub
```

---

### Problème : les pods restent en `ImagePullBackOff`

```
Failed to pull image "123456789012.dkr.ecr.eu-west-3.amazonaws.com/todo-backend:latest":
  unauthorized: authentication required
```

**Cause** : le Secret `ecr-secret` dans le namespace `todo-app` est expiré (le token ECR expire après 12h).

**Solution manuelle** (le pipeline CD le renouvelle automatiquement à chaque déploiement) :

```bash
# Sur l'EC2 en SSH
ECR_TOKEN=$(aws ecr get-login-password --region eu-west-3)
kubectl create secret docker-registry ecr-secret \
  --docker-server=<ACCOUNT_ID>.dkr.ecr.eu-west-3.amazonaws.com \
  --docker-username=AWS \
  --docker-password="${ECR_TOKEN}" \
  --namespace=todo-app \
  --dry-run=client -o yaml | kubectl apply -f -

# Forcer le redémarrage des pods
kubectl rollout restart deployment -n todo-app
```

---

### Problème : le pod backend est en `CrashLoopBackOff`

**Vérifier les logs** :

```bash
kubectl logs -n todo-app deployment/todo-enterprise-backend --previous
# --previous affiche les logs du dernier conteneur crashé
```

**Causes fréquentes** :
- **PostgreSQL pas encore prêt** : augmenter `initialDelaySeconds` dans les probes ou attendre que le pod PostgreSQL soit `Running`
- **Secret postgres-credentials manquant** : vérifier `kubectl get secret postgres-credentials -n todo-app`
- **Problème de mémoire (OOMKilled)** : le t2.micro a 1 GB RAM + 2 GB swap. Spring Boot démarre lentement mais doit finir par démarrer.

**Vérifier l'état des secrets** :

```bash
kubectl get secrets -n todo-app
# Doit afficher : ecr-secret et postgres-credentials
```

---

### Problème : Minikube ne démarre pas sur l'EC2

```bash
# Vérifier les logs d'installation
cat /var/log/setup-minikube.log

# Si le script est encore en cours :
ps aux | grep -E "minikube|docker"

# Redémarrer manuellement si nécessaire :
minikube delete  # supprimer le cluster existant
minikube start \
  --driver=docker \
  --memory=800 \
  --cpus=1 \
  --kubernetes-version=v1.30.0 \
  --apiserver-ips=$(curl -sf http://169.254.169.254/latest/meta-data/public-ipv4) \
  --wait=all \
  --timeout=10m
```

---

### Problème : `helm upgrade` échoue avec "context deadline exceeded"

```
Error: UPGRADE FAILED: context deadline exceeded
```

**Cause** : Spring Boot ou PostgreSQL met trop de temps à démarrer (t2.micro = peu de RAM).

**Solutions** :
1. Augmenter le timeout dans `cd.yml` : `--timeout=10m`
2. Vérifier l'état des pods : `kubectl get pods -n todo-app -w` (watch en temps réel)
3. Helm rollback automatique (`--atomic` dans le pipeline) → relancer le pipeline après que PostgreSQL soit stable

---

## Checklist récapitulative

Avant votre premier déploiement, vérifiez chaque point :

- [ ] Outils installés et versions correctes (`terraform --version`, `aws --version`, etc.)
- [ ] Compte AWS créé, Account ID noté
- [ ] AWS CLI configuré (`aws sts get-caller-identity` fonctionne)
- [ ] Repo forké et variables adaptées (Account ID, GitHub username)
- [ ] Backend S3 créé (bucket + DynamoDB)
- [ ] Stack permanent appliquée (`terraform apply` dans `terraform/permanent`)
- [ ] ARN du rôle GitHub Actions noté (`terraform output github_actions_role_arn`)
- [ ] Clé SSH générée (`~/.ssh/todo-minikube` + `~/.ssh/todo-minikube.pub`)
- [ ] `terraform.tfvars` créé dans `terraform/ephemeral` avec la clé publique SSH
- [ ] Stack éphémère appliquée (`terraform apply` dans `terraform/ephemeral`)
- [ ] IP publique EC2 notée (`terraform output ec2_public_ip`)
- [ ] Attente de ~8 min pour que Minikube s'installe
- [ ] `minikube status` → Running (en SSH sur l'EC2)
- [ ] Kubeconfig en base64 récupéré (`cat ~/.kube/config | base64 -w0`)
- [ ] Secrets GitHub configurés : `AWS_ACCOUNT_ID`, `AWS_REGION`, `AWS_ROLE_TO_ASSUME`, `KUBECONFIG_B64`, `VITE_API_URL`, `VITE_OIDC_AUTHORITY`
- [ ] Premier push sur master → pipeline CD vert ✅
- [ ] `kubectl get pods -n todo-app` → tous `Running` ✅

---

*Guide généré le 2026-05-25 — Projet : todo-enterprise-devsecops*
