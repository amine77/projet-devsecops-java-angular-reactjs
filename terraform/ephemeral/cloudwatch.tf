# ══════════════════════════════════════════════════════════════
#  CLOUDWATCH LOGS — terraform/ephemeral/cloudwatch.tf
# ══════════════════════════════════════════════════════════════
#
#  Log groups CloudWatch pour chaque service ECS.
#
#  ECS FARGATE + CLOUDWATCH :
#  → ECS envoie automatiquement les logs stdout/stderr vers CloudWatch
#  → Configuré dans la task definition via awslogs driver
#  → Pas besoin d'agent de logging dans le container
#
#  RÉTENTION :
#  → 7 jours en dev (économies)
#  → 30-90 jours en prod (conformité, audit)
#  → Passé ce délai, les logs sont automatiquement supprimés
#
#  COÛT CLOUDWATCH :
#  → 0.57$/GB ingested + 0.03$/GB/mois stocké
#  → Application légère : quelques euros/mois max

resource "aws_cloudwatch_log_group" "backend" {
  name              = "/ecs/todo-enterprise/backend"
  retention_in_days = 7

  tags = { Name = "todo-backend-logs" }
}

resource "aws_cloudwatch_log_group" "frontend_angular" {
  name              = "/ecs/todo-enterprise/frontend-angular"
  retention_in_days = 7

  tags = { Name = "todo-frontend-angular-logs" }
}

resource "aws_cloudwatch_log_group" "frontend_react" {
  name              = "/ecs/todo-enterprise/frontend-react"
  retention_in_days = 7

  tags = { Name = "todo-frontend-react-logs" }
}
