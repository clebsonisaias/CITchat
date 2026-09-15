# Gera os PNGs de marca do CITchat direto nas pastas de recursos do app (Windows PowerShell 5.1 + GDI+).
#
#   powershell -ExecutionPolicy Bypass -File citchat\branding\gerar-icones.ps1
#   powershell -ExecutionPolicy Bypass -File citchat\branding\gerar-icones.ps1 -PreviewDir $env:TEMP\citchat-preview
#
# A arte e definida no canvas de 108x108 do icone adaptativo (area visivel 18..90), igual a
# citchat-icon.svg e aos vetores em app/src/main/res/drawable (app_adaptive_fg_monochrome.xml,
# citchat_launcher_foreground.xml). Se mudar a arte, mude nos tres lugares.
param(
  [string]$PreviewDir = '',
  [switch]$PreviewOnly,
  # Cores da marca CITmax (citmax.com.br): fundo --brand-primary, balao --ink, ponto --brand-accent
  [string]$BackgroundColor = '#00C896',
  [string]$GlyphColor = '#0D1F1C',
  [string]$DotColor = '#D8FF3E'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Res = Join-Path $Repo 'app\src\main\res'

$Brand  = [System.Drawing.ColorTranslator]::FromHtml($BackgroundColor)
$Accent = [System.Drawing.ColorTranslator]::FromHtml($DotColor)
$White  = [System.Drawing.ColorTranslator]::FromHtml($GlyphColor)

# Bubble with the "C" (and optionally the dot) cut out. The tail is filled separately,
# because with FillMode.Alternate its overlap with the bubble would become a hole.
function New-GlyphPath([bool]$DotHole) {
  $p = New-Object System.Drawing.Drawing2D.GraphicsPath([System.Drawing.Drawing2D.FillMode]::Alternate)
  $p.AddEllipse([single]31, [single]29, [single]46, [single]46)
  $p.StartFigure()
  $p.AddArc([single]39.5, [single]37.5, [single]29, [single]29, [single]45, [single]270)
  $p.AddArc([single]59.132, [single]40.868, [single]6, [single]6, [single]315, [single]180)
  $p.AddArc([single]45.5, [single]43.5, [single]17, [single]17, [single]315, [single]-270)
  $p.AddArc([single]59.132, [single]57.132, [single]6, [single]6, [single]225, [single]180)
  $p.CloseFigure()
  if ($DotHole) {
    $p.StartFigure()
    $p.AddEllipse([single]63.1, [single]48.6, [single]6.8, [single]6.8)
  }
  return $p
}

$TailPoints = [System.Drawing.PointF[]]@(
  (New-Object System.Drawing.PointF(35.5, 63.5)),
  (New-Object System.Drawing.PointF(32, 77)),
  (New-Object System.Drawing.PointF(46.5, 72))
)

function New-Canvas([int]$Width, [int]$Height, [string]$Matte) {
  $bmp = New-Object System.Drawing.Bitmap($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  if ($Matte) { $g.Clear([System.Drawing.ColorTranslator]::FromHtml($Matte)) } else { $g.Clear([System.Drawing.Color]::Transparent) }
  return @($bmp, $g)
}

function Save-Png($Bitmap, [string]$Path) {
  New-Item -ItemType Directory -Force -Path (Split-Path $Path) | Out-Null
  $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
  $Bitmap.Dispose()
}

# Maps a square canvas area (x0, y0, size) to a square image area (offset, pixels)
function Set-CanvasTransform($g, [double]$OffsetX, [double]$OffsetY, [double]$Pixels, [double]$X0, [double]$Y0, [double]$Size) {
  $g.ResetTransform()
  $g.TranslateTransform([single]$OffsetX, [single]$OffsetY)
  $g.ScaleTransform([single]($Pixels / $Size), [single]($Pixels / $Size))
  $g.TranslateTransform([single](-$X0), [single](-$Y0))
}

function Draw-Glyph($g, $Color, [bool]$DotHole, $DotColor) {
  $brush = New-Object System.Drawing.SolidBrush($Color)
  $path = New-GlyphPath $DotHole
  $g.FillPath($brush, $path)
  $g.FillPolygon($brush, $TailPoints)
  $path.Dispose()
  if ($DotColor) {
    $dot = New-Object System.Drawing.SolidBrush($DotColor)
    $g.FillEllipse($dot, [single]63.1, [single]48.6, [single]6.8, [single]6.8)
  }
}

# Launcher icon for API < 26 (upstream uses the same round image for app_launcher and app_launcher_round)
function New-LauncherIcon([int]$Size, [string]$Shape, [string]$Matte = '') {
  $bmp, $g = New-Canvas $Size $Size $Matte
  $pad = if ($Shape -eq 'full') { 0 } else { $Size * 2.0 / 48 }
  Set-CanvasTransform $g $pad $pad ($Size - 2 * $pad) 18 18 72
  $bg = New-Object System.Drawing.SolidBrush($Brand)
  if ($Shape -eq 'full') { $g.FillRectangle($bg, 0, 0, 108, 108) } else { $g.FillEllipse($bg, 18, 18, 72, 72) }
  Draw-Glyph $g $White $false $Accent
  $g.Dispose()
  return $bmp
}

# Status bar icon: white glyph, 20dp tall inside a 24dp square
function New-NotificationIcon([int]$Size, [string]$Matte = '') {
  $bmp, $g = New-Canvas $Size $Size $Matte
  $glyph = $Size * 20.0 / 24
  # glyph bounds on the canvas: x 31..77, y 29..77 -> centred square (30, 29, 48)
  Set-CanvasTransform $g (($Size - $glyph) / 2) (($Size - $glyph) / 2) $glyph 30 29 48
  # Android tints status bar icons: only the alpha channel matters, keep it white
  Draw-Glyph $g ([System.Drawing.Color]::White) $true $null
  $g.Dispose()
  return $bmp
}

# Intro texture drawn over the brand sphere (IntroRenderer.c: sphere 148x148, texture 82x74 units,
# anchor (6, -5) moves the texture 6 units left and 5 down, so the glyph is drawn 6 right and 5 up).
function New-IntroTexture([double]$Density, [string]$Matte = '') {
  $w = [int][math]::Round(82 * $Density)
  $h = [int][math]::Round(74 * $Density)
  $bmp, $g = New-Canvas $w $h $Matte
  $unitsPerCanvas = 1.3
  $glyph = 48 * $unitsPerCanvas * $Density
  $cx = (41 + 6) * $Density
  $cy = (37 - 5) * $Density
  Set-CanvasTransform $g ($cx - $glyph / 2) ($cy - $glyph / 2) $glyph 30 29 48
  Draw-Glyph $g $White $true $Accent
  $g.Dispose()
  return $bmp
}

$densities = [ordered]@{ 'mdpi' = 1.0; 'hdpi' = 1.5; 'xhdpi' = 2.0; 'xxhdpi' = 3.0; 'xxxhdpi' = 4.0 }
if (-not $PreviewOnly) {
foreach ($d in $densities.Keys) {
  $f = $densities[$d]
  Save-Png (New-LauncherIcon ([int](48 * $f)) 'circle') (Join-Path $Res "mipmap-$d\app_launcher.png")
  Save-Png (New-LauncherIcon ([int](48 * $f)) 'circle') (Join-Path $Res "mipmap-$d\app_launcher_round.png")
  Save-Png (New-NotificationIcon ([int](24 * $f))) (Join-Path $Res "mipmap-$d\app_notification.png")
  if ($d -ne 'xxxhdpi') {
    Save-Png (New-IntroTexture $f) (Join-Path $Res "drawable-$d\intro_tg_plane.png")
  }
}
Save-Png (New-LauncherIcon 512 'full') (Join-Path $PSScriptRoot 'play-store-512.png')
"Icones gerados em $Res"
}

if ($PreviewDir) {
  New-Item -ItemType Directory -Force -Path $PreviewDir | Out-Null
  Save-Png (New-LauncherIcon 192 'circle' '#FDFCF5') (Join-Path $PreviewDir 'launcher-192.png')
  Save-Png (New-NotificationIcon 96 '#37474F') (Join-Path $PreviewDir 'notification-96.png')
  # Intro: sphere + texture composed like IntroRenderer does at xxhdpi
  $s = 3.0
  $bmp, $g = New-Canvas 540 540 '#FFFFFF'
  $bg = New-Object System.Drawing.SolidBrush($Brand)
  $r = 148 * $s
  $g.FillEllipse($bg, [single](270 - $r / 2), [single](270 - $r / 2), [single]$r, [single]$r)
  $tex = New-IntroTexture $s
  $g.DrawImage($tex, [single](270 - $tex.Width / 2 - 6 * $s), [single](270 - $tex.Height / 2 + 5 * $s))
  $tex.Dispose()
  $g.Dispose()
  Save-Png $bmp (Join-Path $PreviewDir 'intro-sphere.png')
  "Previas em $PreviewDir"
}
