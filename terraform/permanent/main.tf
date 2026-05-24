# ══════════════════════════════════════════════════════════════
#  TERRAFORM PERMANENT — main.tf
#  Ressources qui ne sont JAMAIS détruites :
#  VPC · subnets · IGW · NAT · ECR · IAM
# ══════════════════════════════════════════════════════════════
#
#  POURQUOI DEUX STACKS (permanent / ephemeral) ?
#  → Le VPC et les repos ECR coûtent ~0€ : on les garde toujours.
#  → RDS + ElastiCache + ECS + ALB = ~60-80€/mois en eu-west-3.
#    On peut les détruire hors heures de bureau sans perdre les données
#    (les images Docker sont dans ECR, le code dans Git).
#  → terraform destroy sur ephemeral/ → aucun impact sur le réseau.
#
#  BACKEND S3 :
#  → L'état Terraform est stocké dans S3 (créé manuellement en bootstrap).
#  → DynamoDB assure le verrouillage : deux `terraform apply` simultanés
#    ne peuvent pas corrompre le state.
#  → Clé distincte pour permanent et ephemeral → states indépendants.
#
#  TERRAFORM CLOUD vs S3 BACKEND :
#  → Terraform Cloud : SaaS, UI web, free tier, collaboration
#  → S3 + DynamoDB : autogéré, gratuit, simple, pas de dépendance externe
#  → On choisit S3 pour rester dans l'écosystème AWS

terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
  }

  # Backend S3 — créé manuellement lors du bootstrap (étape 1-4 du README)
  backend "s3" {
    bucket         = "todo-enterprise-tfstate-583931058666"
    key            = "permanent/terraform.tfstate"
    region         = "eu-west-3"
    # DynamoDB pour le state locking (évite les apply concurrents)
    dynamodb_table = "todo-enterprise-tfstate-lock"
    encrypt        = true    # Chiffrement côté serveur (SSE-S3)
  }
}

# ── Provider AWS ──────────────────────────────────────────────

provider "aws" {
  region = var.aws_region

  # Tags par défaut appliqués à TOUTES les ressources
  # Utile pour le billing, l'audit, et l'identification des ressources
  default_tags {
    tags = {
      Project     = "todo-enterprise"
      Environment = var.environment
      ManagedBy   = "terraform"
      Stack       = "permanent"
    }
  }
}
