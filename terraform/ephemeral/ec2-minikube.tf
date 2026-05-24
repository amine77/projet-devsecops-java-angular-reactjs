# ══════════════════════════════════════════════════════════════
#  EC2 MINIKUBE — terraform/ephemeral/ec2-minikube.tf
# ══════════════════════════════════════════════════════════════
#
#  Remplace l'architecture ECS Fargate + RDS + ElastiCache + ALB
#  par une instance EC2 t2.micro (Free Tier) faisant tourner Minikube.
#
#  ARCHITECTURE :
#  EC2 t2.micro (1 vCPU, 1 GB RAM + 2 GB swap)
#    └── Minikube (--driver=docker)
#         ├── Namespace todo-app
#         │     ├── Pod todo-backend (Spring Boot — image ECR)
#         │     ├── Pod todo-frontend-angular (Nginx — image ECR)
#         │     └── Pod todo-frontend-react (Nginx — image ECR)
#         └── Namespace data
#               ├── StatefulSet postgresql (chart Bitnami)
#               └── StatefulSet redis (chart Bitnami)
#
#  POURQUOI PAS RDS + ElastiCache ?
#  → PostgreSQL et Redis tournent comme pods Kubernetes (StatefulSets)
#  → Données persistées sur EBS (30 GB Free Tier)
#  → Coût additionnel : $0 (tout sur le même EC2)
#  → Parfait pour l'apprentissage : mêmes concepts que la prod Kubernetes
#
#  ACCÈS DEPUIS GITHUB ACTIONS :
#  → Port 6443 (API server Kubernetes) ouvert dans le Security Group
#  → kubeconfig stocké en base64 dans le secret GitHub KUBECONFIG_B64
#  → helm upgrade depuis le runner GitHub Actions → déploiement en ~30s
#
#  COÛT :
#  → EC2 t2.micro : $0/mois (Free Tier 12 mois, 750h/mois)
#  → EBS 30 GB gp3 : $0/mois (Free Tier)
#  → EIP : $0/mois si associé à une instance en cours d'exécution
#  → Total : $0/mois (an 1) → ~$11.50/mois (an 2+)

# ── Data source : AMI Ubuntu 24.04 LTS ───────────────────────

data "aws_ami" "ubuntu_2404" {
  most_recent = true

  # 099720109477 = Canonical (éditeur officiel Ubuntu)
  owners = ["099720109477"]

  filter {
    name   = "name"
    # Ubuntu 24.04 LTS (Noble Numbat) — gp3 SSD — x86_64
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

# ── Security Group : EC2 Minikube ─────────────────────────────
#
#  Ports ouverts :
#  22   → SSH (accès admin)
#  6443 → Kubernetes API server (GitHub Actions + développeur)
#  80   → HTTP (accès à l'application via NodePort ou Ingress)
#  30000-32767 → NodePort range Kubernetes
#
#  SÉCURITÉ : En production, restreindre le SSH à ton IP.
#  Pour ce projet pédagogique, on ouvre à 0.0.0.0/0 (acceptable
#  pour un environnement de dev/apprentissage éphémère).

resource "aws_security_group" "minikube" {
  name        = "todo-minikube-sg"
  description = "Security Group EC2 Minikube — todo-enterprise"
  vpc_id      = local.vpc_id

  # SSH
  ingress {
    description = "SSH admin"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    # En prod : remplacer par ton IP fixe : ["X.X.X.X/32"]
  }

  # Kubernetes API server
  # Port utilisé par kubectl, helm, et le pipeline GitHub Actions
  ingress {
    description = "Kubernetes API server (kubectl, helm, GitHub Actions)"
    from_port   = 6443
    to_port     = 6443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    # GitHub Actions utilise des IPs variables → 0.0.0.0/0 nécessaire
  }

  # HTTP pour l'application
  ingress {
    description = "HTTP application"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # NodePort range Kubernetes (30000-32767)
  # Utilisé pour exposer les services sans Ingress (debug)
  ingress {
    description = "Kubernetes NodePort range"
    from_port   = 30000
    to_port     = 32767
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  # Trafic sortant libre (nécessaire pour pull ECR, apt-get, etc.)
  egress {
    description = "Trafic sortant libre"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "todo-minikube-sg" }
}

# ── IAM Role : EC2 → ECR (pull des images Docker) ─────────────
#
#  L'EC2 doit pouvoir puller les images Docker depuis ECR
#  pour que Minikube puisse les démarrer comme pods.
#
#  Politique attachée : AmazonEC2ContainerRegistryReadOnly
#  → ecr:GetAuthorizationToken
#  → ecr:BatchGetImage
#  → ecr:GetDownloadUrlForLayer

resource "aws_iam_role" "ec2_minikube" {
  name = "todo-ec2-minikube-role"

  # Trust policy : seul le service EC2 peut assumer ce rôle
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action    = "sts:AssumeRole"
      Effect    = "Allow"
      Principal = { Service = "ec2.amazonaws.com" }
    }]
  })

  tags = { Name = "todo-ec2-minikube-role" }
}

# Politique gérée AWS — accès lecture seule à ECR
resource "aws_iam_role_policy_attachment" "ec2_ecr_readonly" {
  role       = aws_iam_role.ec2_minikube.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonEC2ContainerRegistryReadOnly"
}

# Instance profile = liaison entre le rôle IAM et l'EC2
resource "aws_iam_instance_profile" "ec2_minikube" {
  name = "todo-ec2-minikube-profile"
  role = aws_iam_role.ec2_minikube.name
}

# ── Key Pair SSH ───────────────────────────────────────────────
#
#  La clé publique est fournie via terraform.tfvars.
#  Générer une paire de clés si elle n'existe pas :
#    ssh-keygen -t ed25519 -C "todo-minikube" -f ~/.ssh/todo-minikube
#  Puis mettre la clé publique dans terraform.tfvars :
#    ec2_ssh_public_key = "ssh-ed25519 AAAA..."

resource "aws_key_pair" "minikube" {
  key_name   = "todo-minikube-key"
  public_key = var.ec2_ssh_public_key

  tags = { Name = "todo-minikube-key" }
}

# ── Instance EC2 t2.micro ─────────────────────────────────────

resource "aws_instance" "minikube" {
  ami                    = data.aws_ami.ubuntu_2404.id
  instance_type          = "t2.micro"   # Free Tier : 750h/mois gratuit
  subnet_id              = local.public_subnet_ids[0]
  vpc_security_group_ids = [aws_security_group.minikube.id]
  iam_instance_profile   = aws_iam_instance_profile.ec2_minikube.name
  key_name               = aws_key_pair.minikube.key_name

  # Volume EBS root : 30 GB (maximum Free Tier)
  # gp3 = génération 3, meilleures performances que gp2 pour le même prix
  root_block_device {
    volume_size           = 30
    volume_type           = "gp3"
    encrypted             = true
    delete_on_termination = true   # EBS supprimé avec l'EC2 → pas de coût résiduel
  }

  # Script d'installation exécuté au premier démarrage
  # → installe Docker, Minikube, kubectl, Helm, AWS CLI
  # → configure le swap (2 GB) pour compenser le RAM limité du t2.micro
  # → démarre Minikube avec l'IP publique de l'instance
  user_data = file("${path.module}/scripts/setup-minikube.sh")

  # Ignorer les changements d'AMI et user_data après création
  # (évite un re-provisionnement si l'AMI est mise à jour)
  lifecycle {
    ignore_changes = [ami, user_data]
  }

  tags = { Name = "todo-minikube" }
}

# ── Elastic IP ────────────────────────────────────────────────
#
#  IP publique FIXE associée à l'EC2.
#  Sans EIP, l'IP publique change à chaque redémarrage.
#  Avec EIP, le kubeconfig stocké dans GitHub reste valide.
#
#  COÛT : $0 si associée à une instance en cours d'exécution.
#         ~$0.005/h si NON associée (EC2 arrêté) → ~$3.60/mois
#  → Toujours faire terraform destroy pour libérer l'EIP.

resource "aws_eip" "minikube" {
  instance = aws_instance.minikube.id
  domain   = "vpc"

  tags = { Name = "todo-minikube-eip" }

  # L'EIP dépend de l'IGW pour fonctionner dans un subnet public
  depends_on = [data.terraform_remote_state.permanent]
}
