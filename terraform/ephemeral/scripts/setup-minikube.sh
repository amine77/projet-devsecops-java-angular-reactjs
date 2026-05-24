#!/bin/bash
# ══════════════════════════════════════════════════════════════
#  SCRIPT D'INSTALLATION — EC2 Ubuntu 24.04 LTS
#  Exécuté via user_data au premier démarrage de l'instance
# ══════════════════════════════════════════════════════════════
#
#  INSTALLE :
#  1. Swap 2 GB (compense le RAM limité du t2.micro)
#  2. Docker (runtime pour Minikube --driver=docker)
#  3. kubectl (CLI Kubernetes)
#  4. Helm 3 (gestionnaire de packages Kubernetes)
#  5. AWS CLI v2 (pour authentification ECR)
#  6. Minikube (cluster Kubernetes mono-nœud)
#
#  DURÉE : ~5-8 minutes au premier démarrage
#  LOGS : journalctl -u cloud-final -f
#         ou : cat /var/log/cloud-init-output.log

set -euo pipefail
exec > >(tee /var/log/setup-minikube.log) 2>&1

echo "=== [$(date)] Début installation Todo Enterprise Minikube ==="

# ── 1. SWAP (2 GB) ────────────────────────────────────────────
#  t2.micro = 1 GB RAM → insuffisant pour Minikube + Spring Boot
#  + 2 Nginx. Le swap permet d'utiliser le disque EBS (30 GB)
#  comme extension de RAM. Moins rapide mais suffisant pour dev.
echo "=== Swap 2 GB ==="
fallocate -l 2G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
echo '/swapfile none swap sw 0 0' >> /etc/fstab

# Optimiser l'utilisation du swap :
# vm.swappiness=10 → n'utiliser le swap qu'en dernier recours
echo 'vm.swappiness=10' >> /etc/sysctl.conf
sysctl -p

# ── 2. MISES À JOUR SYSTÈME ───────────────────────────────────
echo "=== Mise à jour système ==="
apt-get update -y
apt-get install -y \
  curl wget apt-transport-https ca-certificates \
  gnupg lsb-release unzip jq

# ── 3. DOCKER ─────────────────────────────────────────────────
#  Minikube utilise Docker comme driver (--driver=docker)
#  Le daemon Docker tourne sur l'EC2 ; Minikube crée un container
#  "minikube" qui fait tourner Kubernetes à l'intérieur.
echo "=== Installation Docker ==="
curl -fsSL https://get.docker.com | sh

# Ajouter l'utilisateur ubuntu au groupe docker
usermod -aG docker ubuntu

systemctl enable docker
systemctl start docker

# Attendre que Docker soit prêt
for i in {1..10}; do
  docker info &>/dev/null && break
  echo "Attente Docker... ($i/10)"
  sleep 3
done

# ── 4. KUBECTL ────────────────────────────────────────────────
echo "=== Installation kubectl ==="
K8S_VERSION=$(curl -Ls https://dl.k8s.io/release/stable.txt)
curl -fsSL "https://dl.k8s.io/release/${K8S_VERSION}/bin/linux/amd64/kubectl" \
  -o /usr/local/bin/kubectl
chmod +x /usr/local/bin/kubectl
kubectl version --client

# ── 5. HELM 3 ─────────────────────────────────────────────────
#  Helm = gestionnaire de packages Kubernetes
#  Permet d'installer PostgreSQL, Redis, et nos apps en une commande
echo "=== Installation Helm 3 ==="
curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
helm version

# ── 6. AWS CLI v2 ─────────────────────────────────────────────
#  Nécessaire pour s'authentifier à ECR :
#  aws ecr get-login-password | docker login --username AWS ...
echo "=== Installation AWS CLI v2 ==="
curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o /tmp/awscliv2.zip
unzip -q /tmp/awscliv2.zip -d /tmp/aws-install
/tmp/aws-install/aws/install
rm -rf /tmp/awscliv2.zip /tmp/aws-install
aws --version

# ── 7. MINIKUBE ───────────────────────────────────────────────
echo "=== Installation Minikube ==="
curl -fsSL https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64 \
  -o /usr/local/bin/minikube
chmod +x /usr/local/bin/minikube
minikube version

# ── 8. DÉMARRAGE MINIKUBE ─────────────────────────────────────
#  Lancé en tant que l'utilisateur ubuntu (pas root)
#  car Minikube ne supporte pas root en mode docker driver
#
#  OPTIONS CLÉS :
#  --driver=docker      → utilise Docker comme hyperviseur (pas de VM)
#  --memory=800         → limite le RAM alloué à Minikube
#  --cpus=1             → 1 vCPU (tout ce que t2.micro a)
#  --apiserver-ips      → inclut l'IP publique dans le certificat TLS
#                          du serveur API → kubeconfig fonctionnel depuis l'extérieur
#  --wait=all           → attend que tous les composants soient prêts

echo "=== Démarrage Minikube ==="

# Récupérer l'IP publique depuis les métadonnées AWS EC2
PUBLIC_IP=$(curl -sf http://169.254.169.254/latest/meta-data/public-ipv4 || echo "")
echo "IP publique EC2 : ${PUBLIC_IP}"

sudo -u ubuntu bash << MINIKUBE_EOF
  minikube start \
    --driver=docker \
    --memory=800 \
    --cpus=1 \
    --kubernetes-version=v1.30.0 \
    --apiserver-ips=${PUBLIC_IP} \
    --wait=all \
    --timeout=10m

  echo "=== Vérification Minikube ==="
  minikube status
  kubectl get nodes

  # ── 9. ADDONS ─────────────────────────────────────────────────
  #  ingress : NGINX Ingress Controller (routing HTTP)
  #  storage-provisioner : création automatique de PersistentVolumes
  echo "=== Activation des addons ==="
  minikube addons enable ingress
  minikube addons enable storage-provisioner
  minikube addons enable metrics-server

  # ── 10. NAMESPACES ────────────────────────────────────────────
  #  todo-app : applications (backend + frontends)
  #  data     : bases de données (PostgreSQL, Redis)
  echo "=== Création des namespaces ==="
  kubectl create namespace todo-app  --dry-run=client -o yaml | kubectl apply -f -
  kubectl create namespace data      --dry-run=client -o yaml | kubectl apply -f -

  # ── 11. REPOS HELM ────────────────────────────────────────────
  echo "=== Configuration des repos Helm ==="
  helm repo add bitnami https://charts.bitnami.com/bitnami
  helm repo update

  # ── 12. AUTHENTIFICATION ECR ──────────────────────────────────
  #  L'instance profile IAM donne accès à ECR.
  #  Configurer Docker pour utiliser les credentials AWS.
  echo "=== Configuration credentials ECR ==="
  AWS_REGION=eu-west-3
  AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
  ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

  # Créer un ImagePullSecret pour que Kubernetes puisse puller depuis ECR
  # Renouveler périodiquement car le token ECR expire après 12h
  aws ecr get-login-password --region ${AWS_REGION} | \
    docker login --username AWS --password-stdin ${ECR_REGISTRY}

  ECR_TOKEN=$(aws ecr get-login-password --region ${AWS_REGION})
  kubectl create secret docker-registry ecr-secret \
    --docker-server=${ECR_REGISTRY} \
    --docker-username=AWS \
    --docker-password="${ECR_TOKEN}" \
    --namespace=todo-app \
    --dry-run=client -o yaml | kubectl apply -f -

  echo "=== Kubeconfig généré ==="
  cat ~/.kube/config
MINIKUBE_EOF

# ── 13. MISE À JOUR DU KUBECONFIG AVEC L'IP PUBLIQUE ────────
#  Le kubeconfig généré par Minikube référence par défaut
#  127.0.0.1. Il faut le remplacer par l'IP publique pour
#  que GitHub Actions puisse s'y connecter.
echo "=== Mise à jour kubeconfig avec IP publique ==="
sudo -u ubuntu sed -i "s|127.0.0.1|${PUBLIC_IP}|g" /home/ubuntu/.kube/config || true
sudo -u ubuntu sed -i "s|https://192.168.[0-9]*.[0-9]*:8443|https://${PUBLIC_IP}:8443|g" \
  /home/ubuntu/.kube/config || true

# Afficher le kubeconfig encodé en base64 pour le copier dans GitHub Secrets
echo ""
echo "======================================================"
echo "IMPORTANT : Copier cette valeur dans GitHub Secrets"
echo "           sous le nom KUBECONFIG_B64"
echo "======================================================"
sudo -u ubuntu cat /home/ubuntu/.kube/config | base64 -w0
echo ""
echo "======================================================"

echo "=== [$(date)] Installation terminée ==="
echo "=== Minikube prêt sur ${PUBLIC_IP} ==="
