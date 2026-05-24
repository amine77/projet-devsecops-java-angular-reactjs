# ══════════════════════════════════════════════════════════════
#  OUTPUTS — terraform/ephemeral/outputs.tf
#  Stack éphémère : EC2 Minikube
# ══════════════════════════════════════════════════════════════

output "ec2_public_ip" {
  description = "IP publique fixe de l'EC2 Minikube (Elastic IP)"
  value       = aws_eip.minikube.public_ip
}

output "ssh_command" {
  description = "Commande SSH pour se connecter à l'EC2"
  value       = "ssh -i ~/.ssh/todo-minikube ubuntu@${aws_eip.minikube.public_ip}"
}

output "kubernetes_api_url" {
  description = "URL de l'API server Kubernetes (pour kubeconfig)"
  value       = "https://${aws_eip.minikube.public_ip}:8443"
}

output "kubectl_config_instructions" {
  description = "Instructions pour récupérer le kubeconfig et l'ajouter aux secrets GitHub"
  value       = <<-EOT

    ════════════════════════════════════════════════════════
     ÉTAPES POST-APPLY (à faire une seule fois)
    ════════════════════════════════════════════════════════

    1. Attendre ~8 minutes que Minikube finisse de s'installer
       Vérifier les logs : ssh ubuntu@${aws_eip.minikube.public_ip}
                           cat /var/log/setup-minikube.log

    2. Se connecter en SSH :
       ssh -i ~/.ssh/todo-minikube ubuntu@${aws_eip.minikube.public_ip}

    3. Vérifier que Minikube tourne :
       minikube status
       kubectl get nodes

    4. Exporter le kubeconfig (base64) :
       cat ~/.kube/config | base64 -w0

    5. Copier la sortie dans GitHub :
       Repo Settings → Secrets and variables → Actions
       → New secret : KUBECONFIG_B64 = <valeur base64>

    6. Ajouter aussi le secret EC2_HOST :
       EC2_HOST = ${aws_eip.minikube.public_ip}

    ════════════════════════════════════════════════════════
  EOT
}
