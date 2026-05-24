# ══════════════════════════════════════════════════════════════
#  VPC & RÉSEAU — terraform/permanent/vpc.tf
# ══════════════════════════════════════════════════════════════
#
#  Architecture réseau en 2 couches :
#
#  Internet
#    │
#    ├── [IGW] Internet Gateway (gratuit)
#    │
#    ├── PUBLIC SUBNETS (10.0.1.0/24, 10.0.2.0/24)
#    │   → ALB (Application Load Balancer) — accessible depuis internet
#    │   → NAT Gateway — permet aux ressources privées d'accéder à internet
#    │
#    └── PRIVATE SUBNETS (10.0.11.0/24, 10.0.12.0/24)
#        → ECS Fargate Tasks — pas exposées directement
#        → RDS PostgreSQL — jamais exposée à internet
#        → ElastiCache Redis — jamais exposée à internet
#
#  POURQUOI DES SUBNETS PRIVÉS ?
#  → Sécurité : la BDD n'est pas accessible depuis internet
#  → Réduction de la surface d'attaque
#  → Conformité PCI DSS / RGPD
#
#  NAT GATEWAY vs NAT INSTANCE :
#  → NAT Gateway : managed AWS, HA, ~32€/mois, pas de maintenance
#  → NAT Instance : EC2 t3.micro, ~8€/mois, mais fragile
#  → En dev : 1 seul NAT Gateway (pas de HA) pour économiser
#  → En prod : 1 NAT Gateway par AZ (~64€/mois mais HA réelle)

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

# ── Subnets publics (ALB + NAT) ───────────────────────────────
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
  # Nécessaire pour les ressources dans les subnets publics (ex : bastion, NAT)
  map_public_ip_on_launch = true

  tags = {
    Name = "todo-enterprise-public-${var.availability_zones[count.index]}"
    # Tag pour l'ALB Kubernetes (si utilisé plus tard avec EKS)
    "kubernetes.io/role/elb" = "1"
  }
}

# ── Subnets privés (ECS, RDS, Redis) ─────────────────────────

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

# ── Elastic IP pour NAT Gateway ───────────────────────────────
# NAT Gateway nécessite une IP publique fixe

resource "aws_eip" "nat" {
  domain = "vpc"
  tags   = { Name = "todo-enterprise-nat-eip" }

  depends_on = [aws_internet_gateway.main]
}

# ── NAT Gateway (un seul, dans le premier subnet public) ──────
# En dev : 1 NAT = économies (-32€/mois vs 2 NATs)
# En prod : un NAT par AZ pour la haute disponibilité

resource "aws_nat_gateway" "main" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public[0].id    # Toujours dans eu-west-3a

  tags = { Name = "todo-enterprise-nat" }

  depends_on = [aws_internet_gateway.main]
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

# ── Table de routage privée ───────────────────────────────────
# Règle : le trafic internet sort via le NAT Gateway (pas directement)

resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.main.id
  }

  tags = { Name = "todo-enterprise-rt-private" }
}

resource "aws_route_table_association" "private" {
  count          = length(aws_subnet.private)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}
