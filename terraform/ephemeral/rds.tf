# ══════════════════════════════════════════════════════════════
#  FICHIER DÉSACTIVÉ — remplacé par ec2-minikube.tf
# ══════════════════════════════════════════════════════════════
#
#  L'infrastructure ECS Fargate + RDS + ElastiCache + ALB a été
#  remplacée par une instance EC2 t2.micro (Free Tier) faisant
#  tourner Minikube. PostgreSQL et Redis s'exécutent comme pods
#  Kubernetes via les charts Helm Bitnami.
#
#  Avantages :
#  → Coût : ~$0/mois (Free Tier 12 mois) vs ~$66/mois (ECS)
#  → Apprentissage Kubernetes et Helm directement dans le pipeline CD
#  → Même commandes kubectl/helm que EKS en production
