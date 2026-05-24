# ══════════════════════════════════════════════════════════════
#  SECRETS MANAGER — terraform/ephemeral/secrets.tf
# ══════════════════════════════════════════════════════════════
#
#  AWS Secrets Manager stocke les credentials sensibles.
#
#  POURQUOI SECRETS MANAGER vs VARIABLES D'ENVIRONNEMENT ?
#  → Variables d'env : visibles dans les logs ECS, `docker inspect`, etc.
#  → Secrets Manager : chiffrées au repos (KMS), accès audité (CloudTrail)
#  → ECS injecte les secrets dans les variables d'env au démarrage du container
#    via l'ECS Task Definition (secrets = [{ name, valueFrom ARN }])
#
#  ROTATION AUTOMATIQUE :
#  → Secrets Manager peut roter automatiquement les mots de passe
#  → Lambda function dédiée → met à jour RDS + le secret en même temps
#  → En dev : pas de rotation (inutile)
#
#  COÛT :
#  → 0.40$/mois par secret + 0.05$/10 000 appels API
#  → En pratique : ~1-2€/mois pour ce projet

# ── Génération du mot de passe RDS ───────────────────────────
# random_password génère un mot de passe sécurisé
# Il est stocké dans le state Terraform (chiffré dans S3)

resource "random_password" "db" {
  length           = 24
  special          = true
  # PostgreSQL n'accepte pas @, ", ', \ dans les mots de passe des connection strings
  override_special = "!#$%^&*()-_=+[]{}|;:,.<>?"
}

# ── Secret RDS PostgreSQL ─────────────────────────────────────

resource "aws_secretsmanager_secret" "db" {
  name        = "todo-enterprise/database"
  description = "Credentials PostgreSQL pour l'application Todo Enterprise"

  # recovery_window_in_days = 0 : suppression immédiate (en dev)
  # En prod : 7-30 jours de rétention pour récupérer un secret supprimé par erreur
  recovery_window_in_days = 0

  tags = { Name = "todo-db-secret" }
}

resource "aws_secretsmanager_secret_version" "db" {
  secret_id = aws_secretsmanager_secret.db.id

  # JSON avec toutes les infos de connexion
  secret_string = jsonencode({
    username = var.db_username
    password = random_password.db.result
    dbname   = var.db_name
    # L'endpoint RDS sera mis à jour après la création de RDS
    # En pratique : utiliser une référence à aws_db_instance.main.endpoint
    host     = ""    # Rempli après le terraform apply de rds.tf
    port     = 5432
    # Connection string complète pour Spring Boot
    url      = "jdbc:postgresql://${aws_db_instance.main.endpoint}/${var.db_name}"
  })

  # Ce depends_on force la création de RDS avant de stocker l'endpoint
  depends_on = [aws_db_instance.main]
}

# ── Secret Configuration Application ─────────────────────────

resource "aws_secretsmanager_secret" "app_config" {
  name        = "todo-enterprise/app-config"
  description = "Configuration Spring Boot : JWT, Keycloak, etc."
  recovery_window_in_days = 0

  tags = { Name = "todo-app-config-secret" }
}

resource "random_password" "jwt_secret" {
  length  = 64
  special = false    # Pour les JWT secrets, pas de caractères spéciaux
}

resource "aws_secretsmanager_secret_version" "app_config" {
  secret_id = aws_secretsmanager_secret.app_config.id

  secret_string = jsonencode({
    # Ces valeurs sont des placeholders — à remplacer par les vraies valeurs
    jwt_issuer_uri   = "http://keycloak:8180/realms/todo"
    spring_profile   = "aws"
    redis_host       = aws_elasticache_cluster.main.cache_nodes[0].address
    redis_port       = "6379"
    # SES : identités à vérifier sur AWS SES avant utilisation
    ses_from_address = "noreply@todo-enterprise.com"
    ses_region       = "eu-west-3"
  })

  depends_on = [aws_elasticache_cluster.main]
}
