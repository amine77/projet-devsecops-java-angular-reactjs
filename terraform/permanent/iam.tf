# ══════════════════════════════════════════════════════════════
#  IAM — Rôles et politiques
#  terraform/permanent/iam.tf
# ══════════════════════════════════════════════════════════════
#
#  3 rôles IAM créés ici :
#
#  1. ecs_task_execution_role :
#     → Utilisé par ECS pour LANCER les tâches (pull ECR, écrire CloudWatch)
#     → Attaché à la task definition (executionRoleArn)
#     → Géré par AWS ECS, pas par l'application
#
#  2. ecs_task_role :
#     → Utilisé PAR l'application Java tournant dans le container
#     → Permet à l'app d'appeler SES, S3, DynamoDB, Secrets Manager
#     → Attaché à la task definition (taskRoleArn)
#     → Principe du moindre privilège : seules les permissions nécessaires
#
#  3. github_actions_role :
#     → Utilisé par GitHub Actions pour pusher vers ECR et déployer sur ECS
#     → Authentification via OIDC (pas de clés statiques AWS !)
#     → OIDC = OpenID Connect : GitHub génère un JWT, AWS le valide
#
#  OIDC vs CLÉS STATIQUES :
#  → Clés statiques : AWS_ACCESS_KEY_ID + AWS_SECRET_ACCESS_KEY dans GitHub Secrets
#    Risque : fuite des clés = accès permanent au compte AWS
#  → OIDC : token JWT éphémère (~15 min de validité), généré par GitHub
#    Avantage : pas de secret à stocker, rotation automatique
#
#  PRINCIPE DU MOINDRE PRIVILÈGE :
#  → Chaque rôle n'a que les permissions dont il a besoin
#  → Pas de AdministratorAccess → réduit la surface d'attaque

# ── Rôle 1 : ECS Task Execution Role ─────────────────────────
# Ce rôle est assumé par le service ECS (pas par l'application)

data "aws_iam_policy_document" "ecs_task_execution_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "ecs_task_execution" {
  name               = "todo-enterprise-ecs-execution-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_execution_assume.json

  tags = { Name = "todo-ecs-execution-role" }
}

# Politique AWS gérée : permet à ECS de puller les images ECR et écrire les logs
resource "aws_iam_role_policy_attachment" "ecs_task_execution_managed" {
  role       = aws_iam_role.ecs_task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Politique custom : lecture des secrets Secrets Manager pour injecter les env vars
resource "aws_iam_role_policy" "ecs_task_execution_secrets" {
  name = "todo-ecs-execution-secrets"
  role = aws_iam_role.ecs_task_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue",
          "ssm:GetParameters",
          "kms:Decrypt"
        ]
        # Seuls les secrets du projet todo-enterprise
        Resource = [
          "arn:aws:secretsmanager:${var.aws_region}:${var.aws_account_id}:secret:todo-enterprise/*"
        ]
      }
    ]
  })
}

# ── Rôle 2 : ECS Task Role ────────────────────────────────────
# Ce rôle est assumé PAR l'application Java (dans le container)

resource "aws_iam_role" "ecs_task" {
  name               = "todo-enterprise-ecs-task-role"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_execution_assume.json

  tags = { Name = "todo-ecs-task-role" }
}

# Permissions de l'application Java
resource "aws_iam_role_policy" "ecs_task_permissions" {
  name = "todo-ecs-task-permissions"
  role = aws_iam_role.ecs_task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # SES : envoi d'emails (notifications de validation/rejet)
      {
        Effect = "Allow"
        Action = [
          "ses:SendEmail",
          "ses:SendRawEmail"
        ]
        Resource = "*"
        Condition = {
          StringEquals = {
            "ses:FromAddress" = "noreply@todo-enterprise.com"
          }
        }
      },
      # S3 : stockage des pièces jointes (profil @aws)
      {
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject",
          "s3:GetPresignedUrl"
        ]
        Resource = "arn:aws:s3:::todo-enterprise-uploads-${var.aws_account_id}/*"
      },
      # DynamoDB : préférences utilisateurs (profil @aws)
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Query"
        ]
        Resource = "arn:aws:dynamodb:${var.aws_region}:${var.aws_account_id}:table/todo-user-preferences"
      },
      # CloudWatch : métriques custom (optionnel)
      {
        Effect = "Allow"
        Action = ["cloudwatch:PutMetricData"]
        Resource = "*"
        Condition = {
          StringEquals = {
            "cloudwatch:namespace" = "TodoEnterprise"
          }
        }
      }
    ]
  })
}

# ── Rôle 3 : GitHub Actions OIDC ─────────────────────────────
# Authentification sans clés statiques via OpenID Connect

# Provider OIDC GitHub (créé une seule fois par compte AWS)
resource "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"

  # Client ID que GitHub utilise pour demander des tokens
  client_id_list = ["sts.amazonaws.com"]

  # Thumbprint du certificat TLS de GitHub Actions OIDC
  # Vérifiable avec : openssl s_client -connect token.actions.githubusercontent.com:443
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

# Trust policy : conditions très précises sur qui peut assumer ce rôle
data "aws_iam_policy_document" "github_actions_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # SÉCURITÉ CRITIQUE : limite au repo exact + branche master
    # Sans cette condition → n'importe quel repo GitHub pourrait accéder
    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${var.github_org}/${var.github_repo}:ref:refs/heads/master",
        "repo:${var.github_org}/${var.github_repo}:ref:refs/heads/main",
        "repo:${var.github_org}/${var.github_repo}:environment:production"
      ]
    }
  }
}

resource "aws_iam_role" "github_actions" {
  name               = "todo-enterprise-github-actions-role"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume.json
  max_session_duration = 3600    # 1 heure max

  tags = { Name = "todo-github-actions-role" }
}

# Permissions GitHub Actions : push ECR + update ECS
resource "aws_iam_role_policy" "github_actions_permissions" {
  name = "todo-github-actions-permissions"
  role = aws_iam_role.github_actions.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # ECR : se connecter et pousser des images
      {
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken"
        ]
        Resource = "*"
      },
      {
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload",
          "ecr:DescribeRepositories",
          "ecr:ListImages"
        ]
        Resource = [
          aws_ecr_repository.backend.arn,
          aws_ecr_repository.frontend_angular.arn,
          aws_ecr_repository.frontend_react.arn
        ]
      },
      # ECS : mettre à jour les services (déploiement)
      {
        Effect = "Allow"
        Action = [
          "ecs:DescribeServices",
          "ecs:DescribeTaskDefinition",
          "ecs:DescribeTasks",
          "ecs:ListTasks",
          "ecs:RegisterTaskDefinition",
          "ecs:UpdateService"
        ]
        Resource = "*"
        Condition = {
          StringEquals = {
            "ecs:cluster" = "arn:aws:ecs:${var.aws_region}:${var.aws_account_id}:cluster/todo-enterprise-cluster"
          }
        }
      },
      # IAM : passer les rôles aux task definitions ECS
      {
        Effect = "Allow"
        Action = ["iam:PassRole"]
        Resource = [
          aws_iam_role.ecs_task_execution.arn,
          aws_iam_role.ecs_task.arn
        ]
      }
    ]
  })
}
