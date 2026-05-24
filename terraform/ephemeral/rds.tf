# ══════════════════════════════════════════════════════════════
#  RDS POSTGRESQL — terraform/ephemeral/rds.tf
# ══════════════════════════════════════════════════════════════
#
#  Amazon RDS PostgreSQL 16 en mode Single-AZ (dev).
#
#  RDS vs PostgreSQL autogéré (EC2) :
#  → RDS : pas de maintenance OS, backups automatiques, Multi-AZ facile
#          patch automatique, monitoring CloudWatch intégré
#  → EC2 : moins cher, plus de contrôle, mais gestion complexe
#  → On choisit RDS : on est sur un projet DevSecOps, pas DBA
#
#  COÛT db.t3.micro :
#  → ~0.018$/heure = ~13€/mois en continu
#  → Free Tier : 750h/mois pendant 12 mois (pour les nouveaux comptes)
#  → Strategy ephemeral : destroy le soir → ne paye que les heures d'utilisation
#
#  FLYWAY :
#  → Spring Boot lance Flyway au démarrage de l'application
#  → Flyway lit les scripts SQL dans /resources/db/migration/
#  → V1__init_schema.sql crée les tables lors du premier démarrage
#  → On n'utilise pas la fonctionnalité RDS de création automatique du schéma

# ── Subnet Group RDS (subnets privés) ────────────────────────

resource "aws_db_subnet_group" "main" {
  name        = "todo-enterprise-db-subnet-group"
  description = "Subnet group RDS — subnets prives uniquement"
  # RDS doit être dans au moins 2 AZs (même en Single-AZ pour le failover)
  subnet_ids  = local.private_subnet_ids

  tags = { Name = "todo-db-subnet-group" }
}

# ── Instance RDS PostgreSQL ───────────────────────────────────

resource "aws_db_instance" "main" {
  identifier = "todo-enterprise-db"

  # ── Moteur ──────────────────────────────────────────────────
  engine               = "postgres"
  engine_version       = "16.4"
  instance_class       = var.db_instance_class

  # ── Base de données ─────────────────────────────────────────
  db_name  = var.db_name
  username = var.db_username
  password = random_password.db.result    # Généré dans secrets.tf

  # ── Stockage ─────────────────────────────────────────────────
  allocated_storage     = 20      # 20 GB (minimum, Free Tier)
  max_allocated_storage = 100     # Auto-scaling jusqu'à 100 GB
  storage_type          = "gp3"   # gp3 = plus rapide et moins cher que gp2
  storage_encrypted     = true    # Chiffrement au repos (obligatoire bonne pratique)

  # ── Réseau ───────────────────────────────────────────────────
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  publicly_accessible    = false    # JAMAIS exposé à internet

  # ── Haute disponibilité ──────────────────────────────────────
  # multi_az = false en dev (pas de réplica → économies)
  # multi_az = true en prod → failover automatique en ~60s
  multi_az = false

  # ── Backups ──────────────────────────────────────────────────
  backup_retention_period = 7           # 7 jours de rétention des backups
  backup_window           = "02:00-03:00"   # Backup à 2h du matin UTC
  maintenance_window      = "Mon:03:00-Mon:04:00"

  # ── Mise à jour ───────────────────────────────────────────────
  auto_minor_version_upgrade  = true    # Patches automatiques (ex: 16.4 → 16.5)
  apply_immediately           = true    # Appliquer les changements immédiatement (dev)

  # ── Protection ───────────────────────────────────────────────
  # deletion_protection = true en prod (empêche `terraform destroy` accidentel)
  deletion_protection       = false
  # skip_final_snapshot = true : pas de snapshot avant suppression (dev)
  # En prod : skip_final_snapshot = false + final_snapshot_identifier
  skip_final_snapshot       = true
  delete_automated_backups  = true

  # ── Monitoring ───────────────────────────────────────────────
  performance_insights_enabled = false    # Payant ($) → désactivé en dev
  monitoring_interval          = 0        # 0 = monitoring de base (gratuit)

  # ── Parameter group ──────────────────────────────────────────
  # default.postgres16 = paramètres AWS par défaut pour PostgreSQL 16
  # Créer un custom parameter group pour personnaliser : shared_buffers, etc.

  tags = { Name = "todo-enterprise-db" }
}
