# Janela para gravar o api_id/api_hash do Telegram (e a keystore) como Secrets do GitHub, sem usar o terminal.
#
#   powershell -STA -ExecutionPolicy Bypass -File citchat\credenciais-telegram.ps1 [-Repo usuario/CITchat]
#
# Os valores vao direto para o GitHub e nao sao mostrados nem salvos em arquivo.
param(
  [string]$Repo = ''
)
$ErrorActionPreference = 'Continue'
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$SecretsDir = Join-Path $env:USERPROFILE 'CITchat-segredos'
foreach ($f in 'KEYSTORE_BASE64.txt', 'KEYSTORE_PASSWORD.txt') {
  if (-not (Test-Path (Join-Path $SecretsDir $f))) { Write-Output "ERRO: $f nao encontrado. Rode citchat\gerar-keystore.ps1 antes."; exit 1 }
}
if (-not $Repo) {
  $login = (gh api user --jq .login 2>$null)
  if (-not $login) { Write-Output 'ERRO: GitHub CLI sem login (gh auth login).'; exit 1 }
  $Repo = "$($login.Trim())/CITchat"
}

$script:result = 'CANCELADO'

$form = New-Object System.Windows.Forms.Form
$form.Text = 'CITchat - credenciais do Telegram'
$form.StartPosition = 'CenterScreen'
$form.FormBorderStyle = 'FixedDialog'
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$form.TopMost = $true
$form.Font = New-Object System.Drawing.Font('Segoe UI', 10)
$form.ClientSize = New-Object System.Drawing.Size(470, 262)

$info = New-Object System.Windows.Forms.Label
$info.Text = "Em my.telegram.org > API development tools, copie os dados do app CITchat. Eles vao direto para os Secrets de $Repo."
$info.SetBounds(16, 12, 440, 44)

$link = New-Object System.Windows.Forms.LinkLabel
$link.Text = 'Abrir my.telegram.org'
$link.SetBounds(16, 60, 300, 22)
$link.Add_LinkClicked({ Start-Process 'https://my.telegram.org/apps' })

$lblId = New-Object System.Windows.Forms.Label
$lblId.Text = 'api_id'
$lblId.SetBounds(16, 97, 90, 22)
$txtId = New-Object System.Windows.Forms.TextBox
$txtId.SetBounds(110, 94, 344, 26)

$lblHash = New-Object System.Windows.Forms.Label
$lblHash.Text = 'api_hash'
$lblHash.SetBounds(16, 133, 90, 22)
$txtHash = New-Object System.Windows.Forms.TextBox
$txtHash.SetBounds(110, 130, 344, 26)
$txtHash.UseSystemPasswordChar = $true

$chkShow = New-Object System.Windows.Forms.CheckBox
$chkShow.Text = 'Mostrar api_hash'
$chkShow.SetBounds(110, 160, 200, 24)
$chkShow.Add_CheckedChanged({ $txtHash.UseSystemPasswordChar = -not $chkShow.Checked })

$status = New-Object System.Windows.Forms.Label
$status.ForeColor = [System.Drawing.Color]::Firebrick
$status.SetBounds(16, 190, 440, 22)

$btnSave = New-Object System.Windows.Forms.Button
$btnSave.Text = 'Salvar no GitHub'
$btnSave.SetBounds(214, 220, 150, 32)
$btnCancel = New-Object System.Windows.Forms.Button
$btnCancel.Text = 'Cancelar'
$btnCancel.SetBounds(370, 220, 84, 32)
$btnCancel.Add_Click({ $form.Close() })

$btnSave.Add_Click({
  $apiId = $txtId.Text.Trim()
  $apiHash = $txtHash.Text.Trim().ToLowerInvariant()
  if ($apiId -notmatch '^\d+$') { $status.Text = 'O api_id deve ter apenas numeros.'; return }
  if ($apiHash -notmatch '^[0-9a-f]{32}$') { $status.Text = 'O api_hash deve ter 32 caracteres (0-9 e a-f).'; return }
  $status.ForeColor = [System.Drawing.Color]::DimGray
  $status.Text = 'Gravando no GitHub...'
  $btnSave.Enabled = $false
  $form.Refresh()
  $values = [ordered]@{
    TELEGRAM_API_ID   = $apiId
    TELEGRAM_API_HASH = $apiHash
    KEYSTORE_BASE64   = (Get-Content -Raw (Join-Path $SecretsDir 'KEYSTORE_BASE64.txt')).Trim()
    KEYSTORE_PASSWORD = (Get-Content -Raw (Join-Path $SecretsDir 'KEYSTORE_PASSWORD.txt')).Trim()
  }
  foreach ($name in $values.Keys) {
    gh secret set $name -R $Repo --body $values[$name] 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
      $status.ForeColor = [System.Drawing.Color]::Firebrick
      $status.Text = "Falha ao gravar $name no GitHub. Tente de novo."
      $btnSave.Enabled = $true
      return
    }
  }
  $script:result = 'OK'
  [System.Windows.Forms.MessageBox]::Show($form, 'Credenciais salvas no GitHub. Pode voltar ao chat.', 'CITchat') | Out-Null
  $form.Close()
})

$form.AcceptButton = $btnSave
$form.CancelButton = $btnCancel
$form.Controls.AddRange(@($info, $link, $lblId, $txtId, $lblHash, $txtHash, $chkShow, $status, $btnSave, $btnCancel))
$form.Add_Shown({ $form.Activate(); $txtId.Focus() })
[void]$form.ShowDialog()

Write-Output $script:result
