# ============================================================
#  Todo Enterprise — Makefile
#  Usage : make <target>
#  Prérequis : Docker Desktop, Minikube, kubectl, Helm, Terraform, AWS CLI
# ============================================================

.PHONY: help \
        prereqs \
        cluster-up cluster-down cluster-status \
        data-up data-down data-status \
        keycloak-up \
        monitoring-up \
        app-up app-down \
        build-api build-angular build-react \
        deploy-api deploy-angular deploy-react \
        docker-up docker-down docker-status \
        session-start session-end \
        infra-init \
        test test-unit test-integration \
        perf \
        grafana kibana kafka-ui mongo-ui \
        ecr-status \
        hosts-info \
        clean

# ─────────────────────────────────────────────────
#  Aide
# ─────────────────────────────────────────────────
help:
	@echo ""
	@echo "╔══════════════════════════════════════════════════════╗"
	@echo "║         Todo Enterprise — Commandes Make             ║"
	@echo "╠══════════════════════════════════════════════════════╣"
	@echo "║  ENVIRONNEMENT LOCAL                                  ║"
	@echo "║  prereqs          Vérifier les outils installés       ║"
	@echo "║  cluster-up       Démarrer Minikube                   ║"
	@echo "║  cluster-down     Arrêter Minikube                    ║"
	@echo "║  cluster-status   État du cluster                     ║"
	@echo "║  data-up          Déployer PostgreSQL/Mongo/Redis/Kafka║"
	@echo "║  data-down        Supprimer le data layer             ║"
	@echo "║  keycloak-up      Déployer Keycloak                   ║"
	@echo "║  monitoring-up    Déployer Prometheus+Grafana+Loki     ║"
	@echo "╠══════════════════════════════════════════════════════╣"
	@echo "║  DOCKER COMPOSE (alternative Minikube)                ║"
	@echo "║  docker-up        Démarrer le data layer (compose)    ║"
	@echo "║  docker-down      Arrêter le data layer               ║"
	@echo "║  docker-status    Vérifier les services               ║"
	@echo "╠══════════════════════════════════════════════════════╣"
	@echo "║  BUILD & DEPLOY                                       ║"
	@echo "║  build-api        Build image Spring Boot (Minikube)  ║"
	@echo "║  build-angular    Build image Angular (Minikube)      ║"
	@echo "║  build-react      Build image React (Minikube)        ║"
	@echo "║  deploy-api       Helm upgrade todo-api               ║"
	@echo "║  deploy-angular   Helm upgrade todo-angular           ║"
	@echo "║  deploy-react     Helm upgrade todo-react             ║"
	@echo "╠══════════════════════════════════════════════════════╣"
	@echo "║  PROXIES UI                                           ║"
	@echo "║  grafana          Port-forward Grafana → :3000        ║"
	@echo "║  kibana           Port-forward Kibana → :5601         ║"
	@echo "║  kafka-ui         Port-forward Kafka UI → :8090       ║"
	@echo "║  mongo-ui         Port-forward Mongo Express → :8091  ║"
	@echo "╠══════════════════════════════════════════════════════╣"
	@echo "║  AWS SESSION                                          ║"
	@echo "║  session-start    Terraform apply RDS + NAT Gateway   ║"
	@echo "║  session-end      Terraform destroy éphémère          ║"
	@echo "║  infra-init       Terraform apply permanent (1 fois)  ║"
	@echo "║  ecr-status       Taille images ECR                   ║"
	@echo "╠══════════════════════════════════════════════════════╣"
	@echo "║  TESTS                                                ║"
	@echo "║  test             Tous les tests (Maven)              ║"
	@echo "║  test-unit        Tests unitaires uniquement          ║"
	@echo "║  perf             Gatling load test                   ║"
	@echo "╠══════════════════════════════════════════════════════╣"
	@echo "║  UTILITAIRES                                          ║"
	@echo "║  hosts-info       Afficher les entrées hosts à ajouter║"
	@echo "║  clean            Nettoyer les targets Maven/Node     ║"
	@echo "╚══════════════════════════════════════════════════════╝"
	@echo ""

# ─────────────────────────────────────────────────
#  Vérification des prérequis
# ─────────────────────────────────────────────────
prereqs:
	@echo ">>> Vérification des prérequis..."
	@echo ""
	@minikube version 2>/dev/null && echo "  ✅ Minikube" || echo "  ❌ Minikube manquant (winget install Kubernetes.minikube)"
	@kubectl version --client 2>/dev/null | head -1 && echo "  ✅ kubectl" || echo "  ❌ kubectl manquant"
	@helm version --short 2>/dev/null && echo "  ✅ Helm" || echo "  ❌ Helm manquant (winget install Helm.Helm)"
	@terraform version 2>/dev/null | head -1 && echo "  ✅ Terraform" || echo "  ❌ Terraform manquant (winget install Hashicorp.Terraform)"
	@aws --version 2>/dev/null && echo "  ✅ AWS CLI" || echo "  ❌ AWS CLI manquant (winget install Amazon.AWSCLI)"
	@docker version --format '{{.Server.Version}}' 2>/dev/null && echo "  ✅ Docker" || echo "  ❌ Docker manquant (Docker Desktop)"
	@mvn --version 2>/dev/null | head -1 && echo "  ✅ Maven" || echo "  ❌ Maven manquant"
	@node --version 2>/dev/null && echo "  ✅ Node.js" || echo "  ❌ Node.js manquant"

# ─────────────────────────────────────────────────
#  Cluster Minikube
# ─────────────────────────────────────────────────
MINIKUBE_PROFILE := todo-enterprise
MINIKUBE_IP := $(shell minikube ip --profile=$(MINIKUBE_PROFILE) 2>/dev/null)

cluster-up:
	@echo ">>> Démarrage Minikube (profil: $(MINIKUBE_PROFILE))..."
	minikube start \
	  --driver=docker \
	  --cpus=6 \
	  --memory=10240 \
	  --disk-size=40g \
	  --kubernetes-version=v1.30.0 \
	  --profile=$(MINIKUBE_PROFILE)
	minikube addons enable ingress --profile=$(MINIKUBE_PROFILE)
	minikube addons enable metrics-server --profile=$(MINIKUBE_PROFILE)
	minikube addons enable dashboard --profile=$(MINIKUBE_PROFILE)
	minikube addons enable storage-provisioner --profile=$(MINIKUBE_PROFILE)
	@echo ">>> Création des namespaces..."
	kubectl create namespace todo-app --dry-run=client -o yaml | kubectl apply -f -
	kubectl create namespace data --dry-run=client -o yaml | kubectl apply -f -
	kubectl create namespace monitoring --dry-run=client -o yaml | kubectl apply -f -
	@echo ""
	@echo "  ✅ Cluster démarré. IP Minikube : $$(minikube ip --profile=$(MINIKUBE_PROFILE))"
	@echo "  👉 Ajoutez l'IP dans votre fichier hosts : make hosts-info"

cluster-down:
	@echo ">>> Arrêt Minikube..."
	minikube stop --profile=$(MINIKUBE_PROFILE)

cluster-status:
	@echo ">>> État du cluster $(MINIKUBE_PROFILE)..."
	minikube status --profile=$(MINIKUBE_PROFILE)
	@echo ""
	kubectl get nodes
	@echo ""
	kubectl get all -n todo-app 2>/dev/null || true
	kubectl get all -n data 2>/dev/null || true

# ─────────────────────────────────────────────────
#  Data Layer (Helm)
# ─────────────────────────────────────────────────
data-up:
	@echo ">>> Ajout des repos Helm..."
	helm repo add bitnami https://charts.bitnami.com/bitnami
	helm repo update
	@echo ">>> Déploiement PostgreSQL..."
	helm upgrade --install postgres bitnami/postgresql \
	  -n data -f helm/values/postgres-values.yaml \
	  --wait --timeout=5m
	@echo ">>> Déploiement Redis..."
	helm upgrade --install redis bitnami/redis \
	  -n data -f helm/values/redis-values.yaml \
	  --wait --timeout=3m
	@echo ">>> Déploiement MongoDB..."
	helm upgrade --install mongo bitnami/mongodb \
	  -n data -f helm/values/mongo-values.yaml \
	  --wait --timeout=5m
	@echo ">>> Déploiement Kafka (KRaft)..."
	helm upgrade --install kafka bitnami/kafka \
	  -n data -f helm/values/kafka-values.yaml \
	  --wait --timeout=5m
	@echo ""
	@echo "  ✅ Data layer déployé !"
	make data-status

data-status:
	@echo ">>> État du Data Layer..."
	kubectl get pods,pvc -n data

data-down:
	@echo ">>> Suppression du data layer..."
	helm uninstall postgres redis mongo kafka -n data --ignore-not-found
	kubectl delete pvc --all -n data

keycloak-up:
	@echo ">>> Création ConfigMap realm Keycloak..."
	kubectl create configmap keycloak-realm \
	  --from-file=todo-realm.json=keycloak/todo-realm.json \
	  -n todo-app --dry-run=client -o yaml | kubectl apply -f -
	@echo ">>> Déploiement Keycloak..."
	helm upgrade --install keycloak bitnami/keycloak \
	  -n todo-app -f helm/values/keycloak-values.yaml \
	  --wait --timeout=5m
	@echo "  ✅ Keycloak disponible : http://keycloak.todo.local"
	@echo "  👤 Admin : admin / admin"
	@echo "  🔑 Realm todo importé automatiquement"

monitoring-up:
	@echo ">>> Ajout des repos Helm monitoring..."
	helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
	helm repo add grafana https://grafana.github.io/helm-charts
	helm repo update
	@echo ">>> Déploiement kube-prometheus-stack..."
	helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
	  -n monitoring -f helm/values/monitoring-values.yaml \
	  --wait --timeout=10m
	@echo ">>> Déploiement Loki..."
	helm upgrade --install loki grafana/loki-stack \
	  -n monitoring \
	  --set grafana.enabled=false \
	  --set promtail.enabled=true
	@echo "  ✅ Monitoring déployé ! Lancer : make grafana"

# ─────────────────────────────────────────────────
#  Docker Compose (alternative Minikube, Phase 1 rapide)
# ─────────────────────────────────────────────────
docker-up:
	@echo ">>> Démarrage du data layer via Docker Compose..."
	docker compose up -d
	@echo ""
	@echo "  ✅ Services démarrés :"
	@echo "  🐘 PostgreSQL   → localhost:5432   (todouser/todopass)"
	@echo "  🍃 MongoDB      → localhost:27017  (mongouser/mongopass)"
	@echo "  🔴 Redis        → localhost:6379   (pas d'auth)"
	@echo "  📨 Kafka        → localhost:19092"
	@echo "  🐼 Kafka UI     → http://localhost:8090"
	@echo "  🍃 Mongo UI     → http://localhost:8091"
	@echo "  🔑 Keycloak     → http://localhost:8180  (admin/admin)"

docker-down:
	@echo ">>> Arrêt Docker Compose..."
	docker compose down

docker-status:
	@echo ">>> État des containers..."
	docker compose ps

docker-logs:
	docker compose logs -f --tail=50

# ─────────────────────────────────────────────────
#  Build des images (dans le daemon Minikube)
# ─────────────────────────────────────────────────
build-api:
	@echo ">>> Build image todo-api dans Minikube..."
	eval $$(minikube -p $(MINIKUBE_PROFILE) docker-env) && \
	  mvn -B package -DskipTests --file backend/pom.xml && \
	  docker build -t todo-api:latest ./backend
	@echo "  ✅ Image todo-api:latest prête dans Minikube"

build-angular:
	@echo ">>> Build image todo-angular dans Minikube..."
	eval $$(minikube -p $(MINIKUBE_PROFILE) docker-env) && \
	  docker build -t todo-angular:latest ./frontend-angular
	@echo "  ✅ Image todo-angular:latest prête"

build-react:
	@echo ">>> Build image todo-react dans Minikube..."
	eval $$(minikube -p $(MINIKUBE_PROFILE) docker-env) && \
	  docker build -t todo-react:latest ./frontend-react
	@echo "  ✅ Image todo-react:latest prête"

# ─────────────────────────────────────────────────
#  Deploy applications (Helm)
# ─────────────────────────────────────────────────
deploy-api:
	@echo ">>> Création du secret todo-api-secrets..."
	kubectl create secret generic todo-api-secrets \
	  --from-literal=SPRING_DATASOURCE_PASSWORD=todopass \
	  --from-literal=SPRING_DATA_MONGODB_PASSWORD=mongopass \
	  -n todo-app --dry-run=client -o yaml | kubectl apply -f -
	@echo ">>> Déploiement todo-api..."
	helm upgrade --install todo-api ./helm/todo-api -n todo-app
	kubectl rollout status deployment/todo-api -n todo-app

deploy-angular:
	helm upgrade --install todo-angular ./helm/todo-angular -n todo-app

deploy-react:
	helm upgrade --install todo-react ./helm/todo-react -n todo-app

app-up: deploy-api deploy-angular deploy-react
	@echo "  ✅ Applications déployées → http://todo.local"

app-down:
	helm uninstall todo-api todo-angular todo-react -n todo-app --ignore-not-found

# ─────────────────────────────────────────────────
#  Proxies UI (port-forward)
# ─────────────────────────────────────────────────
grafana:
	@echo ">>> Grafana disponible sur http://localhost:3000 (admin/admin)"
	kubectl port-forward svc/monitoring-grafana 3000:80 -n monitoring

kibana:
	@echo ">>> Kibana disponible sur http://localhost:5601"
	kubectl port-forward svc/logging-kibana 5601:5601 -n logging

kafka-ui:
	@echo ">>> Kafka UI disponible sur http://localhost:8090"
	kubectl port-forward svc/kafka-ui 8090:80 -n data

mongo-ui:
	@echo ">>> Mongo Express disponible sur http://localhost:8091"
	kubectl port-forward svc/mongo-mongodb 8091:8081 -n data

dashboard:
	minikube dashboard --profile=$(MINIKUBE_PROFILE)

# ─────────────────────────────────────────────────
#  Tests
# ─────────────────────────────────────────────────
test:
	mvn -B verify --file backend/pom.xml

test-unit:
	mvn -B test --file backend/pom.xml

test-integration:
	mvn -B verify -Pintegration-tests --file backend/pom.xml

perf:
	@echo ">>> Lancement simulation Gatling..."
	cd performance-tests && mvn gatling:test \
	  -Dgatling.simulationClass=$${SIMULATION:-CreateTaskSimulation}

# ─────────────────────────────────────────────────
#  AWS Session
# ─────────────────────────────────────────────────
session-start:
	@echo ">>> Provisionnement RDS PostgreSQL + NAT Gateway..."
	cd terraform/ephemeral && terraform apply -auto-approve
	@RDS_URL=$$(cd terraform/ephemeral && terraform output -raw rds_endpoint); \
	kubectl create secret generic rds-secret \
	  --from-literal=url="jdbc:postgresql://$$RDS_URL:5432/tododb" \
	  --from-literal=username="todouser" \
	  --from-literal=password="todopass" \
	  -n todo-app --dry-run=client -o yaml | kubectl apply -f -
	kubectl rollout restart deployment/todo-api -n todo-app
	@echo "  ✅ Session AWS démarrée. RDS prêt."
	@echo "  ⏱️  N'oubliez pas : make session-end en fin de session !"

session-end:
	@echo ">>> Destruction des ressources éphémères AWS..."
	cd terraform/ephemeral && terraform destroy -auto-approve
	kubectl delete secret rds-secret -n todo-app --ignore-not-found
	@echo "  ✅ Ressources détruites. Coût session : ~$0.09-0.13"

infra-init:
	@echo ">>> Application Terraform permanent (S3, ECR, Lambda, etc.)..."
	cd terraform/permanent && terraform init && terraform apply -auto-approve

ecr-status:
	@echo ">>> Taille actuelle des repositories ECR :"
	@for repo in todo-api todo-angular todo-react; do \
	  echo "$$repo :"; \
	  aws ecr describe-images --repository-name $$repo \
	    --query 'sort_by(imageDetails, &imagePushedAt)[*].[imageTags[0],imageSizeInBytes,imagePushedAt]' \
	    --output table 2>/dev/null || echo "  (vide ou inexistant)"; \
	done

# ─────────────────────────────────────────────────
#  Utilitaires
# ─────────────────────────────────────────────────
hosts-info:
	@echo ""
	@echo "  Ajoutez ces lignes à C:\\Windows\\System32\\drivers\\etc\\hosts"
	@echo "  (PowerShell en tant qu'administrateur)"
	@echo ""
	@echo "  $$(minikube ip --profile=$(MINIKUBE_PROFILE) 2>/dev/null || echo '<MINIKUBE_IP>')  todo.local"
	@echo "  $$(minikube ip --profile=$(MINIKUBE_PROFILE) 2>/dev/null || echo '<MINIKUBE_IP>')  keycloak.todo.local"
	@echo "  $$(minikube ip --profile=$(MINIKUBE_PROFILE) 2>/dev/null || echo '<MINIKUBE_IP>')  grafana.todo.local"
	@echo ""
	@echo "  Commande PowerShell (en admin) :"
	@echo '  Add-Content "C:\Windows\System32\drivers\etc\hosts" "<MINIKUBE_IP> todo.local keycloak.todo.local grafana.todo.local"'

clean:
	mvn clean --file backend/pom.xml
	cd frontend-angular && rm -rf dist node_modules
	cd frontend-react && rm -rf dist node_modules
