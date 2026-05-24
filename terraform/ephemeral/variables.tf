# ══════════════════════════════════════════════════════════════
#  VARIABLES — terraform/ephemeral/variables.tf
# ══════════════════════════════════════════════════════════════

variable "environment" {
  description = "Environnement de deploiement"
  type        = string
  default     = "dev"
}

# ── Base de données ───────────────────────────────────────────

variable "db_name" {
  description = "Nom de la base de donnees PostgreSQL"
  type        = string
  default     = "tododb"    # Même valeur que docker-compose.yml
}

variable "db_username" {
  description = "Utilisateur PostgreSQL"
  type        = string
  default     = "todouser"
}

variable "db_instance_class" {
  description = "Type d'instance RDS"
  type        = string
  default     = "db.t3.micro"
  # db.t3.micro = Free Tier eligible (750h/mois la première année)
  # En prod : db.t3.small ou db.t3.medium
}

# ── Cache ─────────────────────────────────────────────────────

variable "redis_node_type" {
  description = "Type de noeud ElastiCache"
  type        = string
  default     = "cache.t3.micro"
}

# ── ECS / Containers ─────────────────────────────────────────

variable "backend_image_tag" {
  description = "Tag de l'image Docker backend (SHA commit)"
  type        = string
  default     = "latest"
}

variable "backend_cpu" {
  description = "CPU alloue au container backend (1 vCPU = 1024)"
  type        = number
  default     = 256    # 0.25 vCPU — suffisant pour une app Spring Boot dev
}

variable "backend_memory" {
  description = "Memoire allouee au container backend (MB)"
  type        = number
  default     = 512    # 512 MB — Spring Boot JVM needs at least 256MB
}

variable "backend_desired_count" {
  description = "Nombre de replicas ECS backend"
  type        = number
  default     = 1    # 1 en dev, 2+ en prod pour la HA
}

variable "frontend_cpu" {
  description = "CPU alloue aux containers frontend Nginx"
  type        = number
  default     = 256
}

variable "frontend_memory" {
  description = "Memoire allouee aux containers frontend Nginx (MB)"
  type        = number
  default     = 256    # Nginx est très léger
}

variable "frontend_desired_count" {
  description = "Nombre de replicas ECS frontend"
  type        = number
  default     = 1
}

# ── DNS / ACM ────────────────────────────────────────────────

variable "domain_name" {
  description = "Nom de domaine principal (ex: todo-enterprise.com)"
  type        = string
  default     = ""    # Vide = pas de HTTPS en dev (HTTP seulement)
}
