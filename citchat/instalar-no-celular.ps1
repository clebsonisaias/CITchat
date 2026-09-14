# Baixa o APK compilado no GitHub Actions e instala no celular conectado por USB (depuracao USB ativa).
#
#   powershell -ExecutionPolicy Bypass -File citchat\instalar-no-celular.ps1              (ultima compilacao com sucesso)
#   powershell -ExecutionPolicy Bypass -File citchat\instalar-no-celular.ps1 -RunId 123   (uma execucao especifica)
#   powershell -ExecutionPolicy Bypass -File citchat\instalar-no-celular.ps1 -Apk C:\caminho\CITchat.apk
param(
  [string]$Repo = 'clebsonisaias/CITchat',
  [string]$RunId = '',
  [string]$Apk = '',
  [string]$Package = 'br.com.citmax.citchat'
)
$ErrorActionPreference = 'Continue'
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')

function Fail([string]$Message) {
  Write-Host "ERRO: $Message" -ForegroundColor Red
  exit 1
}

$outDir = Join-Path $env:USERPROFILE 'CITchat-apk'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $Apk) {
  if (-not $RunId) {
    $RunId = (gh run list -R $Repo --workflow citchat-apk.yml --status success --limit 1 --json databaseId --jq '.[0].databaseId' 2>$null)
    if (-not $RunId) { Fail "Nenhuma compilacao com sucesso encontrada em $Repo." }
  }
  $runDir = Join-Path $outDir $RunId
  if (-not (Get-ChildItem -Path $runDir -Recurse -Filter '*.apk' -ErrorAction SilentlyContinue)) {
    Write-Host "Baixando o APK da execucao $RunId..."
    gh run download $RunId -R $Repo -D $runDir 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { Fail "Nao foi possivel baixar os artefatos da execucao $RunId." }
  }
  $Apk = (Get-ChildItem -Path $runDir -Recurse -Filter '*.apk' | Select-Object -First 1).FullName
  if (-not $Apk) { Fail 'O artefato nao contem APK.' }
}
Write-Host "APK: $Apk"

$devices = @(adb devices 2>$null | Select-String -Pattern '^\S+\s+device$')
if ($devices.Count -eq 0) { Fail 'Nenhum celular autorizado. Conecte o cabo, ative a depuracao USB e aceite o aviso no celular.' }

Write-Host 'Instalando...'
$install = (adb install -r $Apk 2>&1) -join "`n"
Write-Host $install
if ($install -notmatch 'Success') { Fail 'A instalacao falhou.' }

Write-Host 'Abrindo o app...'
adb shell monkey -p $Package -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
Start-Sleep -Seconds 6

$shot = Join-Path $outDir 'tela.png'
adb shell screencap -p /sdcard/citchat-tela.png 2>&1 | Out-Null
adb pull /sdcard/citchat-tela.png $shot 2>&1 | Out-Null
adb shell rm /sdcard/citchat-tela.png 2>&1 | Out-Null

$version = ((adb shell dumpsys package $Package 2>$null) | Select-String -Pattern 'versionName=' | Select-Object -First 1).ToString().Trim()
Write-Host "Instalado: $Package $version" -ForegroundColor Green
Write-Host "Captura da tela: $shot"
