# ══════════════════════════════════════════════════════════════
#  VPC & RÉSEAU — terraform/permanent/vpc.tf
#  Mode : ÉCONOMIQUE (sans NAT Gateway)
# ══════════════════════════════════════════════════════════════
#
#  Architecture réseau — option zéro NAT Gateway :
#
#  Internet
#    │
#    ├── [IGW] Internet Gateway (gratuit)
#    │
#    ├── PUBLIC SUBNETS (10.0.1.0/24, 10.0.2.0/24)
#    │   → ALB  — reçoit le trafic externe
#    │   → ECS  — tâches Fargate avec IP publique (assign_public_ip=true)
#    │             elles accèdent directement à ECR, CloudWatch, Secrets Manager
#    │
#    └── PRIVATE SUBNETS (10.0.11.0/24, 10.0.12.0/24)
#        → RDS PostgreSQL  — jamais exposée à internet
#        → ElastiCache Redis — jamais exposée à internet
#        (les BDD n'ont pas besoin d'internet sortant)
#
#  POURQUOI SUPPRIMER LE NAT GATEWAY ?
#  → NAT Gateway = ~32€/mois MÊME sans rien faire tourner
#  → Les tâches ECS dans un subnet public avec IP publique peuvent atteindre
#    directement les APIs AWS (ECR, CloudWatch, Secrets Manager)
#  → Les Security Groups protègent toujours les tâches : seul l'ALB peut
#    leur parler sur leur port de service — la sécurité est inchangée
#
#  VPC ENDPOINT S3 (GRATUIT) :
#  → Les images Docker ECR sont stockées dans S3 en interne
#  → Sans endpoint S3 : les couches d'image transitent par internet public
#    (lent + coût de transfert de données sortantes)
#  → Avec endpoint S3 Gateway : trafic S3 reste dans le réseau AWS, gratuit
#
#  QUAND REMETTRE UN NAT GATEWAY ?
#  → En production avec des ressources en subnets privés qui appellent internet
#  → Si on veut des IPs statiques sortantes (whitelist chez un partenaire)
#  → Suffisant : changer assign_public_ip=false + réactiver aws_nat_gateway

# ── VPC Principal ─────────────────────────────────────────────

resource "aws_vpc" "main" {
  cidr_block = var.vpc_cidr

  # enable_dns_hostnames : permet aux instances d'avoir des noms DNS
  # Requis pour que RDS, ElastiCache, etc. soient accessibles par nom
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = { Name = "todo-enterprise-vpc" }
}

# ── Internet Gateway ──────────────────────────────────────────
# Passerelle entre le VPC et Internet (nécessaire pour les subnets publics)

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = "todo-enterprise-igw" }
}

# ── Subnets publics (ALB + ECS) ───────────────────────────────
# count = 2 : un subnet par AZ
# cidrsubnet(vpc_cidr, 8, index) :
#   → /16 + 8 bits = /24 (254 adresses par subnet)
#   → 10.0.0.0/16 → 10.0.1.0/24, 10.0.2.0/24

resource "aws_subnet" "public" {
  count = length(var.availability_zones)

  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 1)
  availability_zone = var.availability_zones[count.index]

  # map_public_ip_on_launch : les instances lancées ici reçoivent une IP publique
  # Nécessaire pour les tâches ECS Fargate (assign_public_ip=true) qui accèdent
  # directement aux APIs AWS sans passer par un NAT Gateway
  map_public_ip_on_launch = true

  tags = {
    Name = "todo-enterprise-public-${var.availability_zones[count.index]}"
    # Tag pour l'ALB Kubernetes (si utilisé plus tard avec EKS)
    "kubernetes.io/role/elb" = "1"
  }
}

# ── Subnets privés (RDS, Redis uniquement) ───────────────────

resource "aws_subnet" "private" {
  count = length(var.availability_zones)

  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 11)
  availability_zone = var.availability_zones[count.index]

  # map_public_ip_on_launch = false : pas d'IP publique (sécurité)
  map_public_ip_on_launch = false

  tags = {
    Name = "todo-enterprise-private-${var.availability_zones[count.index]}"
    "kubernetes.io/role/internal-elb" = "1"
  }
}

# ── Table de routage publique ─────────────────────────────────
# Règle : tout le trafic (0.0.0.0/0) sort via l'Internet Gateway

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "todo-enterprise-rt-public" }
}

# Association : chaque subnet public utilise la table de routage publique
resource "aws_route_table_association" "public" {
  count          = length(aws_subnet.public)
  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# ── Table de routage privée (RDS + Redis uniquement) ─────────
# Pas de route vers internet : les BDD n'ont pas besoin de sortir.
# Le trafic reste dans le VPC via la route "local" implicite (10.0.0.0/16).
# Le VPC Endpoint S3 ci-dessous ajoute automatiquement une entrée dans
# cette table pour que les appels S3 restent dans le réseau AWS.

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id
  # Pas de route 0.0.0.0/0 → pas d'accès internet depuis les subnets privés
  # (la route "local" 10.0.0.0/16 est toujours implicite)
  tags   = { Name = "todo-enterprise-rt-private" }
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# ── VPC Endpoint S3 — Gateway (GRATUIT) ──────────────────────
# Pourquoi S3 ? ECR stocke les couches des images Docker dans S3.
# Sans cet endpoint : les pulls ECR depuis les tâches ECS (subnets publics)
# transitent par internet → coût de transfert sortant (0.09$/GB).
# Avec cet endpoint Gateway : trafic S3 reste dans le réseau AWS, gratuit.
#
# Type Gateway (vs Interface) :
# → Gateway   : gratuit, ajoute une entrée dans la route table, pour S3 et DynamoDB
# → Interface : payant (~7€/mois/endpoint), crée une ENI dans le subnet, pour tout le reste
# On utilise Gateway car c'est suffisant pour S3 et gratuit.

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.aws_region}.s3"
  vpc_endpoint_type = "Gateway"

  # Associe l'endpoint aux deux tables de routage (publique ET privée)
  # → Les tâches ECS (subnet public) et RDS (subnet privé) peuvent accéder à S3
  route_table_ids = [
    aws_route_table.public.id,
    aws_route_table.private.id,
  ]

  tags = { Name = "todo-enterprise-s3-endpoint" }
}
