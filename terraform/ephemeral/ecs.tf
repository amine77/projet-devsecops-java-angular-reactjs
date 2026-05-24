# ══════════════════════════════════════════════════════════════
#  ECS FARGATE — terraform/ephemeral/ecs.tf
# ══════════════════════════════════════════════════════════════
#
#  ECS Fargate : containers managés sans gestion de serveurs EC2.
#
#  CONCEPTS ECS :
#
#  Cluster :
#  → Regroupement logique de services ECS
#  → En Fargate : pas d'EC2 à gérer (AWS gère l'infra sous-jacente)
#
#  Task Definition :
#  → Blueprint d'un container : image, CPU, mémoire, ports, env vars, logs
#  → Versionné : chaque update crée une nouvelle révision (1, 2, 3...)
#  → Équivalent du docker-compose.yml mais pour ECS
#
#  Service :
#  → Maintient N replicas (desired_count) de la task definition en vie
#  → Relance automatiquement si un container crash
#  → Intégré avec l'ALB pour le load balancing
#  → Rolling deployment : démarre les nouveaux avant d'arrêter les anciens
#
#  COÛT FARGATE (eu-west-3) :
#  → 0.04856 $/vCPU/heure + 0.00532 $/GB RAM/heure
#  → Backend (0.25 vCPU, 0.5 GB) : ~9€/mois en continu
#  → Chaque frontend (0.25 vCPU, 0.25 GB) : ~6€/mois chacun
#  → Total : ~21€/mois si actif 24/7
#  → Strategy ephemeral : destroy la nuit → ~7€/mois (8h/j × 5j/semaine)
#
#  MODE ÉCONOMIQUE — assign_public_ip = true :
#  → Les tâches ECS sont dans les subnets PUBLICS avec une IP publique
#  → Elles atteignent ECR, CloudWatch, Secrets Manager directement via internet
#  → Plus besoin de NAT Gateway → économie de ~32€/mois
#  → Sécurité : les Security Groups n'autorisent que le trafic depuis l'ALB
#    (port 8080 pour le backend, port 80 pour les frontends)
#    L'IP publique ne change rien : aucun trafic extérieur ne passe les SGs

# ── Cluster ECS ───────────────────────────────────────────────

resource "aws_ecs_cluster" "main" {
  name = "todo-enterprise-cluster"

  # Container Insights : métriques CloudWatch avancées (CPU, mémoire, réseau)
  # Coût supplémentaire (~15$/mois) → désactivé en dev
  setting {
    name  = "containerInsights"
    value = "disabled"
  }

  tags = { Name = "todo-enterprise-cluster" }
}

# Capacity providers : FARGATE et FARGATE_SPOT
# FARGATE_SPOT = containers interruptibles, 70% moins cher
resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name = aws_ecs_cluster.main.name

  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    weight            = 1
    base              = 1    # Au moins 1 task sur FARGATE (pas SPOT) pour la stabilité
  }
}

# ── Task Definition : Backend Spring Boot ────────────────────

resource "aws_ecs_task_definition" "backend" {
  family                   = "todo-backend"
  cpu                      = var.backend_cpu
  memory                   = var.backend_memory
  network_mode             = "awsvpc"     # Requis pour Fargate
  requires_compatibilities = ["FARGATE"]

  # Rôles IAM
  execution_role_arn = local.ecs_execution_role_arn    # Pour puller ECR + écrire logs
  task_role_arn      = local.ecs_task_role_arn         # Pour SES, S3, DynamoDB

  container_definitions = jsonencode([
    {
      name  = "todo-backend"
      image = "${local.ecr_backend_url}:${var.backend_image_tag}"

      # Ports exposés
      portMappings = [
        {
          containerPort = 8080
          hostPort      = 8080
          protocol      = "tcp"
        }
      ]

      # Variables d'environnement NON-sensibles (visibles dans les logs)
      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
        { name = "SERVER_PORT",            value = "8080" },
        # JVM : UseContainerSupport = adapte le heap au CPU/RAM du container
        { name = "JAVA_OPTS",              value = "-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0" }
      ]

      # Variables sensibles depuis Secrets Manager (chiffrées)
      # ECS les injecte comme variables d'env au démarrage
      secrets = [
        {
          name      = "SPRING_DATASOURCE_URL"
          valueFrom = "${aws_secretsmanager_secret.db.arn}:url::"
        },
        {
          name      = "SPRING_DATASOURCE_USERNAME"
          valueFrom = "${aws_secretsmanager_secret.db.arn}:username::"
        },
        {
          name      = "SPRING_DATASOURCE_PASSWORD"
          valueFrom = "${aws_secretsmanager_secret.db.arn}:password::"
        },
        {
          name      = "SPRING_DATA_REDIS_HOST"
          valueFrom = "${aws_secretsmanager_secret.app_config.arn}:redis_host::"
        }
      ]

      # Logging → CloudWatch
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.backend.name
          "awslogs-region"        = local.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }

      # Health check dans le container (en plus du health check ALB)
      healthCheck = {
        command     = ["CMD-SHELL", "curl -sf http://localhost:8080/actuator/health || exit 1"]
        interval    = 30
        timeout     = 10
        retries     = 3
        startPeriod = 60    # 60s de grace : Spring Boot prend ~30s à démarrer
      }

      essential = true    # Si ce container crash → la task entière est arrêtée
    }
  ])

  tags = { Name = "todo-backend-task" }

  depends_on = [
    aws_secretsmanager_secret_version.db,
    aws_secretsmanager_secret_version.app_config
  ]
}

# ── Service ECS : Backend ─────────────────────────────────────

resource "aws_ecs_service" "backend" {
  name            = "todo-backend-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.backend.arn
  desired_count   = var.backend_desired_count

  # FARGATE : AWS gère l'infra
  launch_type = "FARGATE"

  # Réseau : subnets PUBLICS — pas de NAT Gateway, IP publique directe
  # Les SGs garantissent que seul l'ALB peut atteindre le port 8080
  network_configuration {
    subnets          = local.public_subnet_ids
    security_groups  = [aws_security_group.ecs_backend.id]
    assign_public_ip = true    # IP publique → accès direct ECR/CloudWatch/SecretsManager
  }

  # Intégration avec l'ALB
  load_balancer {
    target_group_arn = aws_lb_target_group.backend.arn
    container_name   = "todo-backend"
    container_port   = 8080
  }

  # Déploiement : rolling update (pas de downtime)
  deployment_minimum_healthy_percent = 50     # Accepte une instance en moins pendant le déploiement
  deployment_maximum_percent         = 200    # Lance les nouvelles avant d'arrêter les anciennes

  # Force un nouveau déploiement quand la task definition change
  force_new_deployment = true

  depends_on = [aws_lb_listener_rule.api]

  tags = { Name = "todo-backend-service" }

  lifecycle {
    # Ignore les changements de desired_count (géré par l'auto-scaling)
    ignore_changes = [desired_count]
  }
}

# ── Task Definition : Frontend Angular ───────────────────────

resource "aws_ecs_task_definition" "frontend_angular" {
  family                   = "todo-frontend-angular"
  cpu                      = var.frontend_cpu
  memory                   = var.frontend_memory
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]

  execution_role_arn = local.ecs_execution_role_arn

  container_definitions = jsonencode([
    {
      name  = "todo-frontend-angular"
      image = "${local.ecr_angular_url}:${var.backend_image_tag}"

      portMappings = [
        { containerPort = 80, hostPort = 80, protocol = "tcp" }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.frontend_angular.name
          "awslogs-region"        = local.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget -qO- http://localhost/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 10    # Nginx démarre en quelques secondes
      }

      essential = true
    }
  ])

  tags = { Name = "todo-frontend-angular-task" }
}

resource "aws_ecs_service" "frontend_angular" {
  name            = "todo-frontend-angular-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.frontend_angular.arn
  desired_count   = var.frontend_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = local.public_subnet_ids
    security_groups  = [aws_security_group.ecs_frontend.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.frontend_angular.arn
    container_name   = "todo-frontend-angular"
    container_port   = 80
  }

  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200
  force_new_deployment               = true

  depends_on = [aws_lb_listener.http]

  tags = { Name = "todo-frontend-angular-service" }
}

# ── Task Definition : Frontend React ─────────────────────────

resource "aws_ecs_task_definition" "frontend_react" {
  family                   = "todo-frontend-react"
  cpu                      = var.frontend_cpu
  memory                   = var.frontend_memory
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]

  execution_role_arn = local.ecs_execution_role_arn

  container_definitions = jsonencode([
    {
      name  = "todo-frontend-react"
      image = "${local.ecr_react_url}:${var.backend_image_tag}"

      portMappings = [
        { containerPort = 80, hostPort = 80, protocol = "tcp" }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.frontend_react.name
          "awslogs-region"        = local.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }

      healthCheck = {
        command     = ["CMD-SHELL", "wget -qO- http://localhost/health || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 10
      }

      essential = true
    }
  ])

  tags = { Name = "todo-frontend-react-task" }
}

resource "aws_ecs_service" "frontend_react" {
  name            = "todo-frontend-react-service"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.frontend_react.arn
  desired_count   = var.frontend_desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = local.public_subnet_ids
    security_groups  = [aws_security_group.ecs_frontend.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.frontend_react.arn
    container_name   = "todo-frontend-react"
    container_port   = 80
  }

  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200
  force_new_deployment               = true

  depends_on = [aws_lb_listener_rule.react]

  tags = { Name = "todo-frontend-react-service" }
}
