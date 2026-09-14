# Gera a chave de assinatura (keystore PKCS12) do CITchat com o OpenSSL do Git para Windows.
#
#   powershell -ExecutionPolicy Bypass -File citchat\gerar-keystore.ps1
#
# Os arquivos ficam FORA do repositorio (padrao: %USERPROFILE%\CITchat-segredos) e nunca
# devem ir para o GitHub. Faca backup da pasta: sem essa chave nao da para publicar
# atualizacoes do app com a mesma assinatura.
param(
  [string]$OutDir = (Join-Path $env:USERPROFILE 'CITchat-segredos'),
  [string]$Alias = 'citchat'
)
$ErrorActionPreference = 'Stop'

$openssl = 'C:\Program Files\Git\mingw64\bin\openssl.exe'
if (-not (Test-Path $openssl)) { throw "OpenSSL do Git para Windows nao encontrado em $openssl" }

$ks = Join-Path $OutDir 'citchat-release.p12'
if (Test-Path $ks) { throw "Ja existe uma keystore em $ks. Nao vou sobrescrever: perder a chave impede atualizar o app." }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# Senha aleatoria de 32 caracteres. Em PKCS12 a senha da keystore e a da chave sao a mesma.
$chars = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$password = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })

$tmp = Join-Path $OutDir ('tmp-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
$key = Join-Path $tmp 'key.pem'
$crt = Join-Path $tmp 'cert.pem'
try {
  $env:CITCHAT_KS_PASS = $password
  # openssl escreve progresso no stderr; no Windows PowerShell 5.1 isso vira erro fatal com 'Stop'
  $ErrorActionPreference = 'Continue'
  & $openssl req -x509 -newkey rsa:4096 -sha256 -days 10950 -nodes -keyout $key -out $crt -subj '/CN=CITchat/O=CITmax/C=BR' 2>$null
  if ($LASTEXITCODE -ne 0) { throw 'openssl req falhou' }
  & $openssl pkcs12 -export -inkey $key -in $crt -name $Alias -out $ks -passout env:CITCHAT_KS_PASS 2>$null
  if ($LASTEXITCODE -ne 0) { throw 'openssl pkcs12 falhou' }
  $sha256 = (& $openssl x509 -in $crt -noout -fingerprint -sha256 2>$null) -replace '^.*=', ''
  $sha1 = (& $openssl x509 -in $crt -noout -fingerprint -sha1 2>$null) -replace '^.*=', ''
  if (-not $sha256) { throw 'nao foi possivel ler o certificado' }
  $ErrorActionPreference = 'Stop'
} finally {
  Remove-Item Env:CITCHAT_KS_PASS -ErrorAction SilentlyContinue
  Remove-Item -Recurse -Force $tmp
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
$ksUnix = $ks -replace '\\', '/'
[IO.File]::WriteAllText((Join-Path $OutDir 'KEYSTORE_PASSWORD.txt'), $password, $utf8)
[IO.File]::WriteAllText((Join-Path $OutDir 'KEYSTORE_BASE64.txt'), [Convert]::ToBase64String([IO.File]::ReadAllBytes($ks)), $utf8)
[IO.File]::WriteAllText((Join-Path $OutDir 'keystore.properties'), @"
keystore.file=$ksUnix
keystore.password=$password
key.alias=$Alias
key.password=$password
"@, $utf8)
[IO.File]::WriteAllText((Join-Path $OutDir 'LEIA-ME.txt'), @"
CITchat - chave de assinatura (NAO compartilhe, NAO envie para o GitHub, FACA BACKUP)

citchat-release.p12   keystore PKCS12, alias "$Alias"
keystore.properties   para compilar localmente, se um dia precisar
KEYSTORE_PASSWORD.txt senha da keystore e da chave

Secrets do GitHub (repositorio > Settings > Secrets and variables > Actions):
  KEYSTORE_BASE64    = conteudo de KEYSTORE_BASE64.txt
  KEYSTORE_PASSWORD  = conteudo de KEYSTORE_PASSWORD.txt

Impressao digital do certificado (use no Firebase e no Google Play):
  SHA-256: $sha256
  SHA-1:   $sha1
"@, $utf8)

"Keystore criada em: $OutDir"
"Alias: $Alias"
"SHA-256: $sha256"
"SHA-1:   $sha1"
