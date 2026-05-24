# ══════════════════════════════════════════════════════════════
#  OUTPUTS — terraform/permanent/outputs.tf
# ══════════════════════════════════════════════════════════════
#
#  Les outputs du stack permanent sont lus par le stack ephemeral
#  via : data "terraform_remote_state" "permanent" { ... }
#
#  Cela évite de dupliquer les valeurs (IDs, ARNs) entre les deux stacks.

# ── Réseau ────────────────────────────────────────────────────

output "vpc_id" {
  description = "ID du VPC principal"
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "IDs des subnets publics (ALB)"
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "IDs des subnets prives (ECS, RDS, Redis)"
  value       = aws_subnet.private[*].id
}

output "availability_zones" {
  description = "Zones de disponibilite utilisees"
  value       = var.availability_zones
}

# ── ECR ───────────────────────────────────────────────────────

output "ecr_backend_url" {
  description = "URL du repository ECR backend"
  value       = aws_ecr_repository.backend.repository_url
}

output "ecr_frontend_angular_url" {
  description = "URL du repository ECR frontend Angular"
  value       = aws_ecr_repository.frontend_angular.repository_url
}

output "ecr_frontend_react_url" {
  description = "URL du repository ECR frontend React"
  value       = aws_ecr_repository.frontend_react.repository_url
}

output "ecr_registry" {
  description = "URL du registry ECR (sans le nom du repo)"
  value       = "${var.aws_account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
}

# ── IAM ───────────────────────────────────────────────────────

output "ecs_task_execution_role_arn" {
  description = "ARN du role ECS Task Execution"
  value       = aws_iam_role.ecs_task_execution.arn
}

output "ecs_task_role_arn" {
  description = "ARN du role ECS Task (pour l'application)"
  value       = aws_iam_role.ecs_task.arn
}

output "github_actions_role_arn" {
  description = "ARN du role GitHub Actions OIDC — a mettre dans le secret AWS_ROLE_TO_ASSUME"
  value       = aws_iam_role.github_actions.arn
}

# ── Config ────────────────────────────────────────────────────

output "aws_region" {
  value = var.aws_region
}

output "aws_account_id" {
  value = var.aws_account_id
}
