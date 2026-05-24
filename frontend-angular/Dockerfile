# ══════════════════════════════════════════════════════════════
#  DOCKERFILE — Frontend Angular 20 (Multi-stage)
# ══════════════════════════════════════════════════════════════
#
#  ÉTAPES :
#  1. Stage "build" : npm ci + ng build → fichiers statiques dans /dist
#  2. Stage "runtime" : Nginx Alpine sert les fichiers statiques
#
#  AVANTAGES MULTI-STAGE :
#  → Image finale sans Node.js (économie ~500MB)
#  → Seul /dist est copié → pas de node_modules, src, ni tsconfig
#
#  npm ci vs npm install :
#  → npm ci : installe EXACTEMENT ce qui est dans package-lock.json
#    Plus reproductible, plus rapide en CI (pas de résolution de versions)
#  → npm install : peut mettre à jour package-lock.json
#    À éviter en CI pour garantir la reproductibilité

# ── Étape 1 : Build Angular ────────────────────────────────────
FROM node:22-alpine AS build

# Arguments de build (passés avec --build-arg en CI)
# Permet d'adapter la config selon l'environnement de déploiement
ARG API_URL=/api
ARG OIDC_AUTHORITY=http://localhost:8180/realms/todo
ARG OIDC_CLIENT_ID=todo-spa

WORKDIR /app

# Copier les fichiers de dépendances en premier (cache Docker)
# Si package.json ne change pas → npm ci utilise le cache
COPY package.json package-lock.json* ./

# Installation des dépendances
# --frozen-lockfile : échoue si package-lock.json est obsolète
RUN npm ci --frozen-lockfile

# Copier les sources Angular
COPY . .

# Build de production
# --configuration production : minification, tree-shaking, AOT compiler
# --output-path : dossier de sortie (personnalisable)
RUN npx ng build --configuration production --output-path /dist/angular

# ── Étape 2 : Nginx pour servir les assets ─────────────────────
FROM nginx:1.27-alpine AS runtime

LABEL org.opencontainers.image.title="Todo Enterprise Frontend Angular"
LABEL org.opencontainers.image.description="SPA Angular 20 servie par Nginx"

# Sécurité : supprimer la config Nginx par défaut
RUN rm -rf /etc/nginx/conf.d/*

# Copier notre config Nginx (SPA routing + security headers)
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copier les assets Angular buildés
COPY --from=build /dist/angular /usr/share/nginx/html

# Port 80 exposé
EXPOSE 80

# Vérification santé : teste que Nginx répond
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --spider http://localhost/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
