# ══════════════════════════════════════════════════════════════
#  VARIABLES — terraform/ephemeral/variables.tf
#  Stack éphémère : EC2 Minikube (remplace ECS + RDS + ALB)
# ══════════════════════════════════════════════════════════════

variable "environment" {
  description = "Environnement de déploiement"
  type        = string
  default     = "dev"
}

# ── EC2 Minikube ──────────────────────────────────────────────

variable "ec2_ssh_public_key" {
  description = <<-EOT
    Clé SSH publique pour accéder à l'EC2 Minikube.
    Générer avec : ssh-keygen -t ed25519 -C "todo-minikube" -f ~/.ssh/todo-minikube
    Puis copier le contenu de ~/.ssh/todo-minikube.pub ici.
  EOT
  type        = string
}

variable "ec2_instance_type" {
  description = "Type d'instance EC2. t2.micro = Free Tier (750h/mois gratuit)"
  type        = string
  default     = "t2.micro"
}

# ── Images Docker ─────────────────────────────────────────────

variable "backend_image_tag" {
  description = "Tag de l'image Docker backend (SHA commit Git)"
  type        = string
  default     = "latest"
}

variable "frontend_image_tag" {
  description = "Tag des images Docker frontend (SHA commit Git)"
  type        = string
  default     = "latest"
}
