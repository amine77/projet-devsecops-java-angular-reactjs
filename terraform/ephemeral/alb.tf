# ══════════════════════════════════════════════════════════════
#  APPLICATION LOAD BALANCER — terraform/ephemeral/alb.tf
# ══════════════════════════════════════════════════════════════
#
#  L'ALB reçoit le trafic internet et le route vers les bons services ECS.
#
#  ARCHITECTURE DE ROUTAGE :
#  Internet → ALB (port 80) → listener rules
#    /api/*              → Target Group backend (port 8080)
#    /actuator/*         → Target Group backend (port 8080)
#    Host: angular.*     → Target Group frontend Angular (port 80)
#    Host: react.*       → Target Group frontend React (port 80)
#    (défaut)            → Target Group frontend Angular
#
#  ALB vs NLB (Network Load Balancer) :
#  → ALB : Layer 7 (HTTP/HTTPS), routing par path/host/header
#  → NLB : Layer 4 (TCP/UDP), ultra-faible latence, IP fixe
#  → On choisit ALB : routing HTTP intelligent, pas besoin d'IP fixe
#
#  COÛT ALB :
#  → ~20€/mois fixe + coût variable selon le trafic
#  → Strategy ephemeral : destroy le soir → économies significatives
#
#  HEALTH CHECKS :
#  → ALB vérifie que le service répond (ex: GET /actuator/health → 200)
#  → Si le health check échoue → le container est déregistré
#  → ECS relance automatiquement le container défaillant

# ── Application Load Balancer ─────────────────────────────────

resource "aws_lb" "main" {
  name               = "todo-enterprise-alb"
  internal           = false    # false = public internet, true = interne au VPC
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = local.public_subnet_ids    # L'ALB est dans les subnets publics

  # Journalisation des accès ALB vers S3 (optionnel, coûte un peu)
  # access_logs { bucket = "..." enabled = false }

  # Protection contre la suppression accidentelle (en prod)
  enable_deletion_protection = false

  tags = { Name = "todo-enterprise-alb" }
}

# ── Target Group : Backend Spring Boot ───────────────────────

resource "aws_lb_target_group" "backend" {
  name        = "todo-backend-tg"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"          # "ip" pour Fargate (pas "instance")
  vpc_id      = local.vpc_id

  # Health check Spring Boot Actuator
  health_check {
    enabled             = true
    path                = "/actuator/health"
    port                = "traffic-port"
    protocol            = "HTTP"
    healthy_threshold   = 2    # 2 checks OK → healthy
    unhealthy_threshold = 3    # 3 checks KO → unhealthy → container restarté
    timeout             = 10
    interval            = 30
    matcher             = "200"    # Code HTTP attendu
  }

  # Deregistration delay : temps pour finir les requêtes en cours avant déregistrement
  deregistration_delay = 30    # 30s en dev (300s par défaut)

  tags = { Name = "todo-backend-tg" }
}

# ── Target Group : Frontend Angular ───────────────────────────

resource "aws_lb_target_group" "frontend_angular" {
  name        = "todo-angular-tg"
  port        = 80
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = local.vpc_id

  health_check {
    path                = "/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }

  deregistration_delay = 30
  tags = { Name = "todo-angular-tg" }
}

# ── Target Group : Frontend React ─────────────────────────────

resource "aws_lb_target_group" "frontend_react" {
  name        = "todo-react-tg"
  port        = 80
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = local.vpc_id

  health_check {
    path                = "/health"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    timeout             = 5
    interval            = 30
    matcher             = "200"
  }

  deregistration_delay = 30
  tags = { Name = "todo-react-tg" }
}

# ── Listener HTTP (port 80) ───────────────────────────────────
# En dev : HTTP direct (pas de HTTPS car pas de certificat ACM)
# En prod : redirect HTTP → HTTPS

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  # Action par défaut : vers le frontend Angular
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend_angular.arn
  }
}

# ── Règles de routage ─────────────────────────────────────────

# Règle 1 : /api/* → backend Spring Boot
resource "aws_lb_listener_rule" "api" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10    # Priorité la plus haute (plus petit = prioritaire)

  condition {
    path_pattern {
      values = ["/api/*"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}

# Règle 2 : /actuator/* → backend (santé, métriques)
resource "aws_lb_listener_rule" "actuator" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 20

  condition {
    path_pattern {
      values = ["/actuator/*"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.backend.arn
  }
}

# Règle 3 : Host react.* → frontend React
resource "aws_lb_listener_rule" "react" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 30

  condition {
    host_header {
      values = ["react.*", "app-react.*"]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend_react.arn
  }
}
