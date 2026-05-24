# ============================================================
#  Dockerfile multi-stage — Spring Boot Java 21
#  Optimisé pour Minikube (Phase 1-5) et ECR (Phase 7+)
#
#  Stage 1 : builder — Maven + extraction des layers JAR
#  Stage 2 : runtime — JRE 21 minimal + layers optimisées
# ============================================================

# ─── Stage 1 : Build Maven ───────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copier les POM d'abord (cache des dépendances Maven)
# Si seul le code source change, les dépendances ne sont pas re-téléchargées
COPY pom.xml .
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY infrastructure/pom.xml infrastructure/

# Télécharger les dépendances (cachées si POM inchangé)
RUN mvn -B dependency:go-offline -f pom.xml

# Copier les sources
COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src

# Build (skip tests — ils tournent dans le CI)
RUN mvn -B package -DskipTests -f pom.xml

# Extraire les layers du JAR Spring Boot (layertools)
# Résultat : 4 layers (dependencies, spring-boot-loader, snapshot-dependencies, application)
RUN mkdir -p target/extracted && \
    java -Djarmode=layertools \
         -jar infrastructure/target/*.jar \
         extract --destination target/extracted

# ─── Stage 2 : Runtime JRE minimal ──────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Sécurité : pas de root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
USER appuser

# Copier les layers dans l'ordre (du plus stable au plus volatile)
# Docker cache les layers non-modifiées → rebuild rapide si seul l'app change
COPY --from=builder --chown=appuser:appgroup /workspace/target/extracted/dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=appuser:appgroup /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=appuser:appgroup /workspace/target/extracted/application/ ./

# Variables d'environnement par défaut
ENV SPRING_PROFILES_ACTIVE=local \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"

# Health check Docker (redondant avec K8s readinessProbe, mais utile en compose)
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

# JVM avec Virtual Threads (Java 21) — pas de flag supplémentaire nécessaire
# spring.threads.virtual.enabled=true dans application.yml active les VT
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
