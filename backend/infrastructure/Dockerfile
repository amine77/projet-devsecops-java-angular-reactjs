# ══════════════════════════════════════════════════════════════
#  DOCKERFILE — Backend Spring Boot (Multi-stage)
# ══════════════════════════════════════════════════════════════
#
#  BUILD MULTI-STAGE :
#  → Sépare l'environnement de build (Maven + JDK) de l'image finale (JRE)
#  → L'image finale ne contient pas Maven, les sources, ni le JDK complet
#  → Résultat : image de ~200MB au lieu de ~600MB avec un JDK complet
#
#  ÉTAPES :
#  1. Stage "build"   : compilation Maven du projet multi-module
#  2. Stage "extract" : extraction du fat JAR en couches (Spring Boot Layers)
#  3. Stage "runtime" : image finale avec uniquement les couches nécessaires
#
#  SPRING BOOT LAYERS :
#  → spring-boot:repackage crée un fat JAR avec 4 couches :
#    - dependencies       : libs tierces (changent rarement → cache Docker long)
#    - spring-boot-loader : le loader Spring Boot
#    - snapshot-dependencies : libs SNAPSHOT (changent parfois)
#    - application        : ton code (change souvent → couche fine)
#  → Avantage : rebuild rapide en CI (seule la couche "application" change)
#
#  SÉCURITÉ :
#  → L'application tourne avec un user non-root "spring" (UID 1001)
#  → Pas de sudo, pas de shell dans l'image finale
#  → eclipse-temurin:21-jre-alpine = JRE minimal + Alpine Linux

# ── Étape 1 : Build Maven ─────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build

# Installation de Maven (Alpine n'a pas Maven par défaut)
RUN apk add --no-cache maven

WORKDIR /workspace

# Copier les POM en premier pour profiter du cache Docker
# Si les POM ne changent pas, Maven ne re-télécharge pas les dépendances
COPY pom.xml ./
COPY domain/pom.xml domain/
COPY application/pom.xml application/
COPY infrastructure/pom.xml infrastructure/

# Télécharger les dépendances (couche cachée séparément des sources)
# -B = batch mode (pas de couleurs, pour les logs CI)
# dependency:go-offline = précharge toutes les deps Maven
RUN mvn -B dependency:go-offline -pl domain,application,infrastructure

# Copier les sources
COPY domain/src domain/src
COPY application/src application/src
COPY infrastructure/src infrastructure/src

# Build : compilation + tests unitaires (pas les tests Testcontainers en CI Docker)
# -DskipTests=false : on exécute les tests unitaires
# -Dspring.profiles.active=test : profil de test
# Les tests d'intégration (Testcontainers) tournent dans un job CI séparé
RUN mvn -B package -DskipTests \
    -pl infrastructure \
    --also-make

# ── Étape 2 : Extraction des couches Spring Boot ───────────────
FROM eclipse-temurin:21-jre-alpine AS extract

WORKDIR /workspace

# Copier le fat JAR depuis l'étape de build
COPY --from=build /workspace/infrastructure/target/*.jar app.jar

# Extraire les couches du JAR (format Spring Boot Layertools)
RUN java -Djarmode=layertools -jar app.jar extract

# ── Étape 3 : Image finale ─────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Métadonnées OCI (bonnes pratiques)
LABEL org.opencontainers.image.title="Todo Enterprise Backend"
LABEL org.opencontainers.image.description="API REST Spring Boot 3 — Architecture Hexagonale"
LABEL org.opencontainers.image.version="0.1.0"

# Sécurité : créer un utilisateur non-root
# -S = system user (pas de home dir)
# -G = groupe
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copier les couches dans l'ordre croissant de fréquence de changement
# (les couches les moins changeantes en premier → meilleur cache)
COPY --from=extract --chown=spring:spring /workspace/dependencies/ ./
COPY --from=extract --chown=spring:spring /workspace/spring-boot-loader/ ./
COPY --from=extract --chown=spring:spring /workspace/snapshot-dependencies/ ./
COPY --from=extract --chown=spring:spring /workspace/application/ ./

# Changer d'utilisateur (ne plus être root)
USER spring

# Port exposé par Spring Boot
EXPOSE 8080

# Variables d'environnement par défaut
ENV SPRING_PROFILES_ACTIVE=local
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Point d'entrée : démarrage Spring Boot via le loader de couches
# org.springframework.boot.loader.launch.JarLauncher est le launcher layered
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
