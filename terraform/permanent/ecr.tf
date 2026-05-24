# ══════════════════════════════════════════════════════════════
#  ECR — Amazon Elastic Container Registry
#  terraform/permanent/ecr.tf
# ══════════════════════════════════════════════════════════════
#
#  ECR = Registry Docker privé géré par AWS
#
#  AVANTAGES ECR vs Docker Hub :
#  → Authentification via IAM (pas de mot de passe supplémentaire)
#  → Colocalisation avec ECS (pas de frais de transfert de données)
#  → Image scanning intégré (vulnérabilités CVE)
#  → Lifecycle policies : suppression automatique des vieilles images
#
#  COÛT ECR :
#  → 0.10$/GB/mois pour le stockage
#  → 0.09$/GB pour les transferts hors région
#  → Les pulls depuis ECS dans la même région = GRATUITS
#  → En pratique : quelques euros/mois pour 3 apps
#
#  IMAGE IMMUTABILITY :
#  → image_tag_mutability = IMMUTABLE : on ne peut pas écraser un tag existant
#  → Garantit la traçabilité : le tag "abc123" correspond toujours au même image
#  → En dev, on met MUTABLE pour pouvoir re-pousser "latest"

# ── Repo Backend Spring Boot ──────────────────────────────────

resource "aws_ecr_repository" "backend" {
  name                 = "todo-backend"
  image_tag_mutability = "MUTABLE"    # MUTABLE pour le tag "latest" en dev

  # Scan automatique à chaque push — détecte les CVEs dans l'OS et les libs
  image_scanning_configuration {
    scan_on_push = true
  }

  # Chiffrement des images au repos (KMS géré par AWS)
  encryption_configuration {
    encryption_type = "AES256"    # Gratuit (vs KMS = ~1$/mois)
  }

  tags = { Name = "todo-backend" }
}

resource "aws_ecr_repository" "frontend_angular" {
  name                 = "todo-frontend-angular"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = { Name = "todo-frontend-angular" }
}

resource "aws_ecr_repository" "frontend_react" {
  name                 = "todo-frontend-react"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = { Name = "todo-frontend-react" }
}

# ── Lifecycle Policies ────────────────────────────────────────
# Supprime automatiquement les vieilles images → économies de stockage
# Garde seulement les 10 dernières images par repo

resource "aws_ecr_lifecycle_policy" "backend" {
  repository = aws_ecr_repository.backend.name

  policy = jsonencode({
    rules = [
      {
        # Règle 1 : garder les 10 dernières images tagguées
        rulePriority = 1
        description  = "Garder les 10 dernieres images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = { type = "expire" }
      }
    ]
  })
}

resource "aws_ecr_lifecycle_policy" "frontend_angular" {
  repository = aws_ecr_repository.frontend_angular.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Garder les 10 dernieres images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = { type = "expire" }
      }
    ]
  })
}

resource "aws_ecr_lifecycle_policy" "frontend_react" {
  repository = aws_ecr_repository.frontend_react.name

  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Garder les 10 dernieres images"
        selection = {
          tagStatus   = "any"
          countType   = "imageCountMoreThan"
          countNumber = 10
        }
        action = { type = "expire" }
      }
    ]
  })
}
