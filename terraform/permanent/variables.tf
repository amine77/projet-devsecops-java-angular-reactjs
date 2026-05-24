# ══════════════════════════════════════════════════════════════
#  VARIABLES — terraform/permanent/variables.tf
# ══════════════════════════════════════════════════════════════
#
#  Les valeurs par défaut correspondent à l'environnement réel.
#  Surcharger via terraform.tfvars ou -var en CLI.

variable "aws_region" {
  description = "Region AWS de deploiement"
  type        = string
  default     = "eu-west-3"    # Paris
}

variable "aws_account_id" {
  description = "ID du compte AWS (12 chiffres)"
  type        = string
  default     = "583931058666"
}

variable "environment" {
  description = "Environnement : dev, staging, production"
  type        = string
  default     = "dev"

  validation {
    condition     = contains(["dev", "staging", "production"], var.environment)
    error_message = "L'environnement doit etre dev, staging ou production."
  }
}

# ── Réseau ────────────────────────────────────────────────────

variable "vpc_cidr" {
  description = "CIDR block du VPC principal"
  type        = string
  default     = "10.0.0.0/16"
  # /16 = 65 534 adresses IP disponibles
  # Largement suffisant pour tous les sous-réseaux
}

variable "availability_zones" {
  description = "Zones de disponibilite a utiliser (eu-west-3 en a 3)"
  type        = list(string)
  default     = ["eu-west-3a", "eu-west-3b"]
  # On utilise 2 AZ sur 3 pour l'HA sans coût excessif
  # (chaque NAT Gateway = ~32€/mois → on en met 1 seul en dev)
}

# ── GitHub OIDC ───────────────────────────────────────────────

variable "github_org" {
  description = "Organisation ou utilisateur GitHub"
  type        = string
  default     = "amine77"
}

variable "github_repo" {
  description = "Nom du repository GitHub"
  type        = string
  default     = "projet-devsecops-java-angular-reactjs"
}
