# ══════════════════════════════════════════════════════════════
#  TERRAFORM EPHEMERAL — main.tf
#  Ressources qui peuvent être détruites pour économiser :
#  RDS · ElastiCache · ECS · ALB · Security Groups
# ══════════════════════════════════════════════════════════════
#
#  Ce stack lit les outputs du stack permanent via terraform_remote_state.
#
#  WORKFLOW DEV (économies) :
#  Matin  : terraform apply   → démarrer RDS + ECS + ALB (~60€/mois en continu)
#  Soir   : terraform destroy → tout supprimer (payer seulement ce qu'on utilise)
#  → Les images Docker restent dans ECR, le code dans Git → rien de perdu
#
#  terraform_remote_state :
#  → Lit le fichier .tfstate du stack permanent depuis S3
#  → Permet d'accéder aux outputs : vpc_id, subnet_ids, role_arns...
#  → Alternative : variables manuelles (moins pratique, risque d'erreur)

terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  backend "s3" {
    bucket         = "todo-enterprise-tfstate-583931058666"
    key            = "ephemeral/terraform.tfstate"
    region         = "eu-west-3"
    dynamodb_table = "todo-enterprise-tfstate-lock"
    encrypt        = true
  }
}

provider "aws" {
  region = local.aws_region

  default_tags {
    tags = {
      Project     = "todo-enterprise"
      Environment = var.environment
      ManagedBy   = "terraform"
      Stack       = "ephemeral"
    }
  }
}

provider "random" {}

# ── Référence au stack permanent ─────────────────────────────

data "terraform_remote_state" "permanent" {
  backend = "s3"

  config = {
    bucket = "todo-enterprise-tfstate-583931058666"
    key    = "permanent/terraform.tfstate"
    region = "eu-west-3"
  }
}

# ── Locals : raccourcis vers les outputs permanent ─────────────

locals {
  aws_region    = data.terraform_remote_state.permanent.outputs.aws_region
  aws_account   = data.terraform_remote_state.permanent.outputs.aws_account_id

  vpc_id             = data.terraform_remote_state.permanent.outputs.vpc_id
  public_subnet_ids  = data.terraform_remote_state.permanent.outputs.public_subnet_ids
  private_subnet_ids = data.terraform_remote_state.permanent.outputs.private_subnet_ids

  ecs_execution_role_arn = data.terraform_remote_state.permanent.outputs.ecs_task_execution_role_arn
  ecs_task_role_arn      = data.terraform_remote_state.permanent.outputs.ecs_task_role_arn

  ecr_backend_url  = data.terraform_remote_state.permanent.outputs.ecr_backend_url
  ecr_angular_url  = data.terraform_remote_state.permanent.outputs.ecr_frontend_angular_url
  ecr_react_url    = data.terraform_remote_state.permanent.outputs.ecr_frontend_react_url
}
