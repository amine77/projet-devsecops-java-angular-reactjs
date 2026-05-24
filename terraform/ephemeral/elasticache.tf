# ══════════════════════════════════════════════════════════════
#  ELASTICACHE REDIS — terraform/ephemeral/elasticache.tf
# ══════════════════════════════════════════════════════════════
#
#  Amazon ElastiCache Redis 7 — cache applicatif.
#
#  RÔLE DANS L'ARCHITECTURE :
#  → Cache-Aside Pattern : Spring Backend vérifie Redis avant d'aller en BDD
#  → Clés stockées : task:{id}, tasks:owner:{userId}, tasks:team:{teamId}
#  → TTL : 5 minutes (configuré dans RedisTaskCacheAdapter.java)
#
#  ELASTICACHE vs Redis autogéré (EC2) :
#  → ElastiCache : managed, patching automatique, monitoring CloudWatch
#  → Plus cher mais moins de maintenance
#
#  MODES ELASTICACHE :
#  → Cluster Mode Disabled (mode simple) : 1 nœud primaire + replicas
#  → Cluster Mode Enabled : sharding sur plusieurs nœuds
#  → On utilise le mode simple (Cluster Mode Disabled) en dev
#
#  COÛT cache.t3.micro :
#  → ~0.017$/heure = ~12€/mois en continu
#  → Strategy ephemeral : destroy le soir → ne paye que l'utilisation

# ── Subnet Group (subnets privés) ────────────────────────────

resource "aws_elasticache_subnet_group" "main" {
  name        = "todo-enterprise-redis-subnet-group"
  description = "Subnet group ElastiCache Redis — subnets prives"
  subnet_ids  = local.private_subnet_ids

  tags = { Name = "todo-redis-subnet-group" }
}

# ── Cluster Redis ─────────────────────────────────────────────

resource "aws_elasticache_cluster" "main" {
  cluster_id = "todo-enterprise-redis"

  # ── Moteur ──────────────────────────────────────────────────
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.redis_node_type    # cache.t3.micro en dev

  # ── Topologie ────────────────────────────────────────────────
  num_cache_nodes = 1    # Nœud unique en dev (pas de replica)
  # En prod : utiliser aws_elasticache_replication_group pour le Multi-AZ

  # ── Port ─────────────────────────────────────────────────────
  port = 6379    # Port Redis standard

  # ── Réseau ───────────────────────────────────────────────────
  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.redis.id]

  # ── Maintenance ──────────────────────────────────────────────
  maintenance_window = "tue:03:00-tue:04:00"
  # Pas de snapshot en dev (Redis = cache éphémère par nature)
  snapshot_retention_limit = 0

  # ── Paramètres Redis ─────────────────────────────────────────
  # notify-keyspace-events : désactivé pour économiser de la mémoire
  # maxmemory-policy : allkeys-lru = supprime les clés les moins utilisées quand plein

  # apply_immediately en dev
  apply_immediately = true

  tags = { Name = "todo-enterprise-redis" }
}
