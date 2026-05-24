# ============================================================
#  check-prereqs.ps1 — Vérification des prérequis Windows 11
#  Usage : .\scripts\check-prereqs.ps1
# ============================================================

$ErrorActionPreference = "Continue"

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║    Todo Enterprise — Vérification des prérequis      ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

$allOk = $true

function Check-Tool {
    param (
        [string]$Name,
        [string]$Command,
        [string]$InstallCmd,
        [string]$MinVersion = ""
    )

    try {
        $version = Invoke-Expression $Command 2>$null
        if ($LASTEXITCODE -eq 0 -or $version) {
            Write-Host "  ✅ $Name" -ForegroundColor Green -NoNewline
            if ($version) {
                $versionStr = ($version | Select-Object -First 1).ToString().Trim()
                Write-Host " ($versionStr)" -ForegroundColor DarkGray
            } else {
                Write-Host ""
            }
        } else {
            throw "Non trouvé"
        }
    } catch {
        Write-Host "  ❌ $Name — Non installé" -ForegroundColor Red
        Write-Host "     Installation : $InstallCmd" -ForegroundColor Yellow
        $script:allOk = $false
    }
}

Write-Host "── Outils CLI ──────────────────────────────────────────" -ForegroundColor DarkGray
Check-Tool "Minikube" "minikube version --short" "winget install Kubernetes.minikube"
Check-Tool "kubectl"  "kubectl version --client --short 2>$null" "winget install Kubernetes.kubectl"
Check-Tool "Helm"     "helm version --short" "winget install Helm.Helm"
Check-Tool "Terraform" "terraform version | Select-Object -First 1" "winget install Hashicorp.Terraform"
Check-Tool "AWS CLI"  "aws --version" "winget install Amazon.AWSCLI"
Check-Tool "Docker"   "docker version --format '{{.Server.Version}}'" "Installer Docker Desktop"

Write-Host ""
Write-Host "── Java & Build ────────────────────────────────────────" -ForegroundColor DarkGray
Check-Tool "Java 21"  "java -version 2>&1 | Select-Object -First 1" "winget install EclipseAdoptium.Temurin.21.JDK"
Check-Tool "Maven"    "mvn --version | Select-Object -First 1" "winget install Apache.Maven"
Check-Tool "Node.js"  "node --version" "winget install OpenJS.NodeJS.LTS"
Check-Tool "npm"      "npm --version" "Installé avec Node.js"

Write-Host ""
Write-Host "── Ressources machine ──────────────────────────────────" -ForegroundColor DarkGray

# RAM disponible
$ram = [math]::Round((Get-CimInstance Win32_PhysicalMemory | Measure-Object Capacity -Sum).Sum / 1GB)
if ($ram -ge 12) {
    Write-Host "  ✅ RAM : ${ram} GB (recommandé : 12 GB)" -ForegroundColor Green
} elseif ($ram -ge 8) {
    Write-Host "  ⚠️  RAM : ${ram} GB (minimum : 8 GB, recommandé : 12 GB)" -ForegroundColor Yellow
} else {
    Write-Host "  ❌ RAM : ${ram} GB (insuffisant — minimum 8 GB)" -ForegroundColor Red
    $allOk = $false
}

# CPU (logiques)
$cpu = (Get-CimInstance Win32_Processor | Measure-Object NumberOfLogicalProcessors -Sum).Sum
if ($cpu -ge 6) {
    Write-Host "  ✅ CPU : $cpu cores logiques (recommandé : 6+)" -ForegroundColor Green
} elseif ($cpu -ge 4) {
    Write-Host "  ⚠️  CPU : $cpu cores (minimum : 4, recommandé : 6)" -ForegroundColor Yellow
} else {
    Write-Host "  ❌ CPU : $cpu cores (insuffisant — minimum 4)" -ForegroundColor Red
    $allOk = $false
}

# Espace disque
$disk = [math]::Round((Get-PSDrive C).Free / 1GB)
if ($disk -ge 40) {
    Write-Host "  ✅ Disque libre : ${disk} GB (recommandé : 40 GB)" -ForegroundColor Green
} elseif ($disk -ge 20) {
    Write-Host "  ⚠️  Disque libre : ${disk} GB (minimum : 20 GB)" -ForegroundColor Yellow
} else {
    Write-Host "  ❌ Disque libre : ${disk} GB (insuffisant — minimum 20 GB)" -ForegroundColor Red
    $allOk = $false
}

Write-Host ""
Write-Host "── Réseau ──────────────────────────────────────────────" -ForegroundColor DarkGray

# Vérifier que Docker Desktop tourne
$dockerRunning = $false
try {
    $result = docker info 2>$null
    $dockerRunning = $LASTEXITCODE -eq 0
} catch {}

if ($dockerRunning) {
    Write-Host "  ✅ Docker Desktop est démarré" -ForegroundColor Green
} else {
    Write-Host "  ❌ Docker Desktop n'est pas démarré — démarrez-le avant Minikube" -ForegroundColor Red
    $allOk = $false
}

Write-Host ""
Write-Host "── Résumé ──────────────────────────────────────────────" -ForegroundColor DarkGray
if ($allOk) {
    Write-Host "  🎉 Tous les prérequis sont satisfaits !" -ForegroundColor Green
    Write-Host ""
    Write-Host "  Prochaine étape : make cluster-up" -ForegroundColor Cyan
    Write-Host "  Ou (plus rapide) : make docker-up (Docker Compose sans Minikube)" -ForegroundColor Cyan
} else {
    Write-Host "  ⚠️  Certains prérequis manquent — voir les messages ❌ ci-dessus" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "── Commandes d'installation rapide (PowerShell admin) ──" -ForegroundColor DarkGray
Write-Host "  winget install Kubernetes.minikube Kubernetes.kubectl Helm.Helm" -ForegroundColor DarkGray
Write-Host "  winget install Hashicorp.Terraform Amazon.AWSCLI" -ForegroundColor DarkGray
Write-Host "  winget install EclipseAdoptium.Temurin.21.JDK Apache.Maven" -ForegroundColor DarkGray
Write-Host "  winget install OpenJS.NodeJS.LTS" -ForegroundColor DarkGray
Write-Host ""
