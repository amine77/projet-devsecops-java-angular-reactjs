{{/*
══════════════════════════════════════════════════════════════
 HELPERS — helm/todo-enterprise/templates/_helpers.tpl
══════════════════════════════════════════════════════════════

 Fonctions réutilisables dans tous les templates du chart.
 Les fonctions helpers sont définies avec {{- define "name" -}}

 CONVENTION DE NOMMAGE Helm :
 → chart.name      : nom court du chart ("todo-enterprise")
 → chart.fullname  : nom complet release-chart ("prod-todo-enterprise")
 → chart.labels    : labels standards Kubernetes recommandés
*/}}

{{/*
Nom du chart (sans la release)
*/}}
{{- define "todo-enterprise.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Nom complet : release + chart
Ex: "prod-todo-enterprise" si release = "prod"
Tronqué à 63 caractères (limite Kubernetes)
*/}}
{{- define "todo-enterprise.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Labels standard Kubernetes (recommandés par Helm)
Ces labels permettent de filtrer/identifier les ressources
*/}}
{{- define "todo-enterprise.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{ include "todo-enterprise.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Labels sélecteurs (utilisés pour le routage service → pods)
IMPORTANT : Ces labels ne doivent pas changer une fois déployés
*/}}
{{- define "todo-enterprise.selectorLabels" -}}
app.kubernetes.io/name: {{ include "todo-enterprise.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
URL complète de l'image backend ECR
Ex: 583931058666.dkr.ecr.eu-west-3.amazonaws.com/todo-backend:abc1234
*/}}
{{- define "todo-enterprise.backendImage" -}}
{{- printf "%s/%s:%s" .Values.ecr.registry .Values.backend.image.repository .Values.backend.image.tag }}
{{- end }}

{{/*
URL complète de l'image Angular ECR
*/}}
{{- define "todo-enterprise.angularImage" -}}
{{- printf "%s/%s:%s" .Values.ecr.registry .Values.frontendAngular.image.repository .Values.frontendAngular.image.tag }}
{{- end }}

{{/*
URL complète de l'image React ECR
*/}}
{{- define "todo-enterprise.reactImage" -}}
{{- printf "%s/%s:%s" .Values.ecr.registry .Values.frontendReact.image.repository .Values.frontendReact.image.tag }}
{{- end }}
