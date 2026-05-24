# ══════════════════════════════════════════════════════════════
#  DOCKERFILE — Frontend React 19 / Vite (Multi-stage)
# ══════════════════════════════════════════════════════════════
#
#  Même pattern qu'Angular : Node (build) → Nginx (serve)
#
#  VITE build :
#  → vite build génère le dossier dist/ avec les assets hashés
#  → tsc --noEmit : vérifie les types AVANT le build (erreurs TS = échec CI)
#  → L'output est dans dist/ (configurable via vite.config.ts build.outDir)
#
#  VARIABLES D'ENVIRONNEMENT VITE :
#  → Vite intègre les variables VITE_* au moment du build (pas au runtime)
#  → On les passe en --build-arg Docker → fichier .env temporaire
#  → En prod : les vraies URLs sont injectées par le pipeline CI

# ── Étape 1 : Build Vite + TypeScript check ────────────────────
FROM node:22-alpine AS build

ARG VITE_API_URL=/api
ARG VITE_OIDC_AUTHORITY=http://localhost:8180/realms/todo
ARG VITE_OIDC_CLIENT_ID=todo-spa
ARG VITE_OIDC_REDIRECT_URI=http://localhost:3000/auth/callback

WORKDIR /app

# Copier les manifests de dépendances
COPY package.json package-lock.json* ./

# Installation reproductible
RUN npm ci --frozen-lockfile

# Copier les sources
COPY . .

# Créer le fichier .env à partir des build args
# Vite lit ce fichier au moment du build pour injecter les variables
RUN printf "VITE_API_URL=%s\nVITE_OIDC_AUTHORITY=%s\nVITE_OIDC_CLIENT_ID=%s\nVITE_OIDC_REDIRECT_URI=%s\n" \
    "$VITE_API_URL" "$VITE_OIDC_AUTHORITY" "$VITE_OIDC_CLIENT_ID" "$VITE_OIDC_REDIRECT_URI" > .env

# Vérification TypeScript (tsc --noEmit)
# Si des erreurs de types → le build échoue → le CI échoue
RUN npm run build

# ── Étape 2 : Nginx ────────────────────────────────────────────
FROM nginx:1.27-alpine AS runtime

LABEL org.opencontainers.image.title="Todo Enterprise Frontend React"
LABEL org.opencontainers.image.description="SPA React 19/Vite servie par Nginx"

RUN rm -rf /etc/nginx/conf.d/*
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Vite génère les assets dans dist/
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --quiet --spider http://localhost/health || exit 1

CMD ["nginx", "-g", "daemon off;"]
