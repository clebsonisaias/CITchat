# Prepara o GitHub para compilar o CITchat. Rode no SEU terminal, porque ele pede login e as
# credenciais do Telegram (nada e mostrado na tela nem salvo no repositorio):
#
#   powershell -ExecutionPolicy Bypass -File citchat\configurar-github.ps1
#
# 1. Login no GitHub pelo navegador (com permissao "workflow")
# 2. Fork de TGX-Android/Telegram-X com o nome CITchat (se ainda nao existir) e Actions ligado
# 3. Secrets do repositorio: TELEGRAM_API_ID, TELEGRAM_API_HASH, KEYSTORE_BASE64, KEYSTORE_PASSWORD
$ErrorActionPreference = 'Continue'
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')

$RepoName = 'CITchat'
$Upstream = 'TGX-Android/Telegram-X'
$SecretsDir = Join-Path $env:USERPROFILE 'CITchat-segredos'

function Fail([string]$Message) {
  Write-Host "ERRO: $Message" -ForegroundColor Red
  exit 1
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
  Fail 'GitHub CLI nao encontrado. Instale com: winget install --id GitHub.cli'
}

Write-Host '== 1/3 Login no GitHub ==' -ForegroundColor Cyan
$status = (gh auth status 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0) {
  gh auth login --hostname github.com --git-protocol https --web --scopes workflow
  if ($LASTEXITCODE -ne 0) { Fail 'Login no GitHub nao concluido.' }
} elseif ($status -notmatch 'workflow') {
  gh auth refresh --hostname github.com --scopes workflow
  if ($LASTEXITCODE -ne 0) { Fail 'Nao foi possivel adicionar a permissao "workflow".' }
}
gh auth setup-git
$login = (gh api user --jq .login).Trim()
if (-not $login) { Fail 'Nao foi possivel ler o usuario do GitHub.' }
Write-Host "Conectado como $login"

Write-Host "== 2/3 Repositorio $login/$RepoName ==" -ForegroundColor Cyan
$repo = "$login/$RepoName"
gh repo view $repo --json name 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
  gh repo view "$login/Telegram-X" --json name 2>&1 | Out-Null
  if ($LASTEXITCODE -eq 0) {
    Write-Host "Renomeando o fork $login/Telegram-X para $RepoName"
    gh repo rename $RepoName -R "$login/Telegram-X" --yes
  } else {
    gh repo fork $Upstream --fork-name $RepoName --default-branch-only --clone=false
  }
  if ($LASTEXITCODE -ne 0) { Fail "Nao foi possivel criar o repositorio $repo." }
  Start-Sleep -Seconds 5
}
# Em forks o GitHub Actions vem desligado
gh api -X PUT "repos/$repo/actions/permissions" -F enabled=true -f allowed_actions=all 2>&1 | Out-Null
Write-Host "Repositorio: https://github.com/$repo"

Write-Host '== 3/3 Secrets ==' -ForegroundColor Cyan
if (-not (Test-Path (Join-Path $SecretsDir 'citchat-release.p12'))) {
  powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'gerar-keystore.ps1')
  if ($LASTEXITCODE -ne 0) { Fail 'Nao foi possivel gerar a keystore.' }
}

Write-Host 'Abra https://my.telegram.org > API development tools e copie os valores do app CITchat.'
do {
  $apiId = (Read-Host 'api_id (so numeros)').Trim()
} until ($apiId -match '^\d+$')
do {
  $secure = Read-Host 'api_hash (32 caracteres, nao aparece na tela)' -AsSecureString
  $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
  $apiHash = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr).Trim().ToLowerInvariant()
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
  if ($apiHash -notmatch '^[0-9a-f]{32}$') { Write-Host 'O api_hash deve ter 32 caracteres (0-9, a-f). Tente de novo.' -ForegroundColor Yellow }
} until ($apiHash -match '^[0-9a-f]{32}$')

$secrets = [ordered]@{
  TELEGRAM_API_ID   = $apiId
  TELEGRAM_API_HASH = $apiHash
  KEYSTORE_BASE64   = (Get-Content -Raw (Join-Path $SecretsDir 'KEYSTORE_BASE64.txt')).Trim()
  KEYSTORE_PASSWORD = (Get-Content -Raw (Join-Path $SecretsDir 'KEYSTORE_PASSWORD.txt')).Trim()
}
foreach ($name in $secrets.Keys) {
  gh secret set $name -R $repo --body $secrets[$name] 2>&1 | Out-Null
  if ($LASTEXITCODE -ne 0) { Fail "Nao foi possivel gravar o secret $name." }
  Write-Host "  $name ok"
}
$apiHash = $null
$secrets = $null

Write-Host ''
Write-Host "Pronto! Repositorio $repo configurado. Volte ao chat e avise." -ForegroundColor Green
