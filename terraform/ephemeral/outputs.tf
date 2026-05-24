# ══════════════════════════════════════════════════════════════
#  OUTPUTS — terraform/ephemeral/outputs.tf
# ══════════════════════════════════════════════════════════════

output "alb_dns_name" {
  description = "DNS de l'ALB — acceder a l'application via cette URL"
  value       = "http://${aws_lb.main.dns_name}"
}

output "alb_dns_api" {
  description = "URL de l'API backend"
  value       = "http://${aws_lb.main.dns_name}/api"
}

output "rds_endpoint" {
  description = "Endpoint RDS PostgreSQL"
  value       = aws_db_instance.main.endpoint
  sensitive   = true    # Ne pas afficher dans les logs CI
}

output "redis_endpoint" {
  description = "Endpoint ElastiCache Redis"
  value       = aws_elasticache_cluster.main.cache_nodes[0].address
  sensitive   = true
}

output "ecs_cluster_name" {
  description = "Nom du cluster ECS"
  value       = aws_ecs_cluster.main.name
}

output "deploy_command" {
  description = "Commande AWS CLI pour forcer un nouveau deploiement"
  value       = <<-EOT
    # Pour redéployer manuellement :
    aws ecs update-service --cluster ${aws_ecs_cluster.main.name} --service todo-backend-service --force-new-deployment
    aws ecs update-service --cluster ${aws_ecs_cluster.main.name} --service todo-frontend-angular-service --force-new-deployment
    aws ecs update-service --cluster ${aws_ecs_cluster.main.name} --service todo-frontend-react-service --force-new-deployment
  EOT
}
