# ══════════════════════════════════════════════════════════════
#  SECURITY GROUPS — terraform/ephemeral/security-groups.tf
# ══════════════════════════════════════════════════════════════
#
#  Stratégie : Defense in depth (défense en profondeur)
#  Chaque ressource n'accepte le trafic QUE de la source légitime.
#
#  RÈGLES D'ACCÈS :
#  Internet → ALB (80/443)
#  ALB → ECS Backend (8080)
#  ALB → ECS Frontends (80)
#  ECS Backend → RDS (5432)
#  ECS Backend → Redis (6379)
#
#  JAMAIS :
#  → Internet → ECS directement
#  → Internet → RDS directement
#  → Internet → Redis directement
#
#  RÉFÉRENCE PAR SG vs CIDR :
#  → On référence le SG source (pas le CIDR) : plus précis et dynamique
#  → Si l'ALB change d'IP (scalabilité), la règle reste valide

# ── Security Group : ALB ──────────────────────────────────────

resource "aws_security_group" "alb" {
  name        = "todo-alb-sg"
  description = "Security Group Application Load Balancer"
  vpc_id      = local.vpc_id

  # Entrant : HTTP et HTTPS depuis n'importe où (internet public)
  ingress {
    description = "HTTP depuis internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  ingress {
    description = "HTTPS depuis internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    ipv6_cidr_blocks = ["::/0"]
  }

  # Sortant : vers les targets (ECS tasks)
  egress {
    description = "Sortant vers les targets ECS"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"    # -1 = tous les protocoles
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "todo-alb-sg" }
}

# ── Security Group : ECS Backend ─────────────────────────────

resource "aws_security_group" "ecs_backend" {
  name        = "todo-ecs-backend-sg"
  description = "Security Group ECS Backend Spring Boot"
  vpc_id      = local.vpc_id

  # Entrant : seulement depuis l'ALB, sur le port 8080
  ingress {
    description     = "Port 8080 depuis ALB uniquement"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  # Sortant : internet (pour appeler SES, S3, ECR, etc.)
  egress {
    description = "Sortant vers internet (AWS APIs, SES, S3)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "todo-ecs-backend-sg" }
}

# ── Security Group : ECS Frontends ───────────────────────────

resource "aws_security_group" "ecs_frontend" {
  name        = "todo-ecs-frontend-sg"
  description = "Security Group ECS Frontends Nginx"
  vpc_id      = local.vpc_id

  ingress {
    description     = "Port 80 depuis ALB uniquement"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "todo-ecs-frontend-sg" }
}

# ── Security Group : RDS PostgreSQL ──────────────────────────

resource "aws_security_group" "rds" {
  name        = "todo-rds-sg"
  description = "Security Group RDS PostgreSQL — acces restreint au backend"
  vpc_id      = local.vpc_id

  # Entrant : UNIQUEMENT depuis le backend ECS
  ingress {
    description     = "PostgreSQL depuis ECS backend uniquement"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_backend.id]
  }

  # Pas d'egress pour RDS (la BDD ne contacte personne)
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["10.0.0.0/8"]    # Seulement dans le VPC
  }

  tags = { Name = "todo-rds-sg" }
}

# ── Security Group : ElastiCache Redis ───────────────────────

resource "aws_security_group" "redis" {
  name        = "todo-redis-sg"
  description = "Security Group ElastiCache Redis — acces restreint au backend"
  vpc_id      = local.vpc_id

  ingress {
    description     = "Redis depuis ECS backend uniquement"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_backend.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["10.0.0.0/8"]
  }

  tags = { Name = "todo-redis-sg" }
}
