# Gera os PNGs de marca do CITchat a partir do logo do app (hexagono CITmax com baloes de conversa),
# direto nas pastas de recursos do app (Windows PowerShell 5.1 + GDI+).
#
#   powershell -ExecutionPolicy Bypass -File citchat\branding\gerar-icones.ps1
#   powershell -ExecutionPolicy Bypass -File citchat\branding\gerar-icones.ps1 -FromSvg citchat\branding\logo-citchat.svg
#   powershell -ExecutionPolicy Bypass -File citchat\branding\gerar-icones.ps1 -PreviewDir $env:TEMP\citchat-preview -PreviewOnly
#
# O logo original e citchat/branding/logo-citchat.svg (exportado do Canva). Com -FromSvg ele e
# renderizado pelo Microsoft Edge (headless) em citchat/branding/logo-citchat.png, que e a base de
# todos os icones. Versoes de uma cor (notificacao, icone monocromatico, logos internos) usam a
# silhueta do hexagono com os baloes vazados.
param(
  [string]$PreviewDir = '',
  [switch]$PreviewOnly,
  [string]$FromSvg = '',
  # Fundo do icone: Pureza digital (branco), do Manual da marca
  [string]$BackgroundColor = '#FFFFFF'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class CitLogo {
  static byte[] Read (Bitmap src, out int w, out int h) {
    w = src.Width; h = src.Height;
    Bitmap argb = src.Clone(new Rectangle(0, 0, w, h), PixelFormat.Format32bppArgb);
    BitmapData data = argb.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
    byte[] px = new byte[w * h * 4];
    Marshal.Copy(data.Scan0, px, 0, px.Length);
    argb.UnlockBits(data);
    argb.Dispose();
    return px;
  }

  static Bitmap Write (byte[] px, int w, int h) {
    Bitmap result = new Bitmap(w, h, PixelFormat.Format32bppArgb);
    BitmapData data = result.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
    Marshal.Copy(px, 0, data.Scan0, px.Length);
    result.UnlockBits(data);
    return result;
  }

  // Crops a transparent render to the bounds of its visible pixels
  public static Bitmap Crop (Bitmap src) {
    int w, h;
    byte[] px = Read(src, out w, out h);
    int minX = w, minY = h, maxX = -1, maxY = -1;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        if (px[(y * w + x) * 4 + 3] > 8) {
          if (x < minX) minX = x;
          if (x > maxX) maxX = x;
          if (y < minY) minY = y;
          if (y > maxY) maxY = y;
        }
      }
    }
    if (maxX < 0) throw new InvalidOperationException("Empty render");
    return src.Clone(new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1), PixelFormat.Format32bppArgb);
  }

  // White silhouette: the coloured hexagon stays opaque, the light speech bubbles become holes
  public static Bitmap Silhouette (Bitmap src) {
    int w, h;
    byte[] px = Read(src, out w, out h);
    for (int i = 0; i < px.Length; i += 4) {
      int b = px[i], g = px[i + 1], r = px[i + 2], a = px[i + 3];
      int light = Math.Min(r, Math.Min(g, b));
      double hole = Math.Max(0.0, Math.Min(1.0, (light - 60) / 140.0));
      px[i] = 255; px[i + 1] = 255; px[i + 2] = 255;
      px[i + 3] = (byte) Math.Round(a * (1.0 - hole));
    }
    return Write(px, w, h);
  }
}
'@

$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Res = Join-Path $Repo 'app\src\main\res'
$LogoPath = Join-Path $PSScriptRoot 'logo-citchat.png'
$Background = [System.Drawing.ColorTranslator]::FromHtml($BackgroundColor)

if ($FromSvg) {
  $edge = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
  if (-not (Test-Path $edge)) { $edge = 'C:\Program Files\Microsoft\Edge\Application\msedge.exe' }
  $work = Join-Path $env:TEMP ('citchat-svg-' + [guid]::NewGuid().ToString('N'))
  New-Item -ItemType Directory -Force -Path $work | Out-Null
  Copy-Item -LiteralPath (Resolve-Path $FromSvg).Path -Destination (Join-Path $work 'logo.svg')
  $shot = Join-Path $work 'render.png'
  $url = 'file:///' + ((Join-Path $work 'logo.svg') -replace '\\', '/')
  $p = Start-Process -FilePath $edge -PassThru -WindowStyle Hidden -ArgumentList @('--headless=new', '--disable-gpu',
    '--hide-scrollbars', '--default-background-color=00000000', "--user-data-dir=$work\profile",
    "--screenshot=$shot", '--window-size=2000,2000', $url)
  if (-not $p.WaitForExit(120000)) { throw 'O Edge nao terminou de renderizar o SVG' }
  $render = New-Object System.Drawing.Bitmap($shot)
  $cropped = [CitLogo]::Crop($render)
  $render.Dispose()
  $cropped.Save($LogoPath, [System.Drawing.Imaging.ImageFormat]::Png)
  "Logo renderizado em $LogoPath ($($cropped.Width)x$($cropped.Height))"
  $cropped.Dispose()
}

$Logo = New-Object System.Drawing.Bitmap($LogoPath)
$Mono = [CitLogo]::Silhouette($Logo)

function New-Canvas([int]$Width, [int]$Height, [string]$Matte) {
  $bmp = New-Object System.Drawing.Bitmap($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
  $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
  $g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
  if ($Matte) { $g.Clear([System.Drawing.ColorTranslator]::FromHtml($Matte)) } else { $g.Clear([System.Drawing.Color]::Transparent) }
  return @($bmp, $g)
}

function Save-Png($Bitmap, [string]$Path) {
  New-Item -ItemType Directory -Force -Path (Split-Path $Path) | Out-Null
  $Bitmap.Save($Path, [System.Drawing.Imaging.ImageFormat]::Png)
  $Bitmap.Dispose()
}

# Draws an image with the given height, centred on (cx, cy)
function Draw-Centered($g, $Image, [double]$Height, [double]$CenterX, [double]$CenterY) {
  $width = $Height * $Image.Width / $Image.Height
  $rect = New-Object System.Drawing.RectangleF([single]($CenterX - $width / 2), [single]($CenterY - $Height / 2), [single]$width, [single]$Height)
  $g.DrawImage($Image, $rect)
}

# Launcher icon for API < 26: white circle (44dp in 48dp) with the logo
function New-LauncherIcon([int]$Size, [bool]$FullBleed, [string]$Matte = '') {
  $bmp, $g = New-Canvas $Size $Size $Matte
  $brush = New-Object System.Drawing.SolidBrush($Background)
  if ($FullBleed) {
    $g.FillRectangle($brush, 0, 0, $Size, $Size)
    Draw-Centered $g $Logo ($Size * 0.78) ($Size / 2) ($Size / 2)
  } else {
    $d = $Size * 44.0 / 48
    $g.FillEllipse($brush, [single](($Size - $d) / 2), [single](($Size - $d) / 2), [single]$d, [single]$d)
    Draw-Centered $g $Logo ($d * 0.8) ($Size / 2) ($Size / 2)
  }
  $g.Dispose()
  return $bmp
}

# Adaptive icon layers: 108dp canvas, logo 60dp tall (inside the 66dp safe zone)
function New-AdaptiveLayer($Image, [double]$Density) {
  $size = [int][math]::Round(108 * $Density)
  $bmp, $g = New-Canvas $size $size ''
  Draw-Centered $g $Image (60 * $Density) ($size / 2) ($size / 2)
  $g.Dispose()
  return $bmp
}

# Status bar icon: white silhouette 22dp tall inside 24dp
function New-NotificationIcon([int]$Size, [string]$Matte = '') {
  $bmp, $g = New-Canvas $Size $Size $Matte
  Draw-Centered $g $Mono ($Size * 22.0 / 24) ($Size / 2) ($Size / 2)
  $g.Dispose()
  return $bmp
}

# Logos drawn by the app itself (passcode, call screen, source code menu): white, tinted by the app
function New-Logo([int]$CanvasPx, [double]$LogoPx) {
  $bmp, $g = New-Canvas $CanvasPx $CanvasPx ''
  Draw-Centered $g $Mono $LogoPx ($CanvasPx / 2) ($CanvasPx / 2)
  $g.Dispose()
  return $bmp
}

$densities = [ordered]@{ 'mdpi' = 1.0; 'hdpi' = 1.5; 'xhdpi' = 2.0; 'xxhdpi' = 3.0; 'xxxhdpi' = 4.0 }
if (-not $PreviewOnly) {
  foreach ($d in $densities.Keys) {
    $f = $densities[$d]
    Save-Png (New-LauncherIcon ([int](48 * $f)) $false) (Join-Path $Res "mipmap-$d\app_launcher.png")
    Save-Png (New-LauncherIcon ([int](48 * $f)) $false) (Join-Path $Res "mipmap-$d\app_launcher_round.png")
    Save-Png (New-NotificationIcon ([int](24 * $f))) (Join-Path $Res "mipmap-$d\app_notification.png")
    Save-Png (New-AdaptiveLayer $Logo $f) (Join-Path $Res "mipmap-$d\citchat_adaptive_fg.png")
    Save-Png (New-AdaptiveLayer $Mono $f) (Join-Path $Res "mipmap-$d\citchat_adaptive_mono.png")
    if ($d -ne 'xxxhdpi') {
      # The intro shows the logo in place of the sphere (IntroController.getSphereBitmap); nothing flies over it
      $w = [int][math]::Round(82 * $f); $h = [int][math]::Round(74 * $f)
      $empty, $g = New-Canvas $w $h ''
      $g.Dispose()
      Save-Png $empty (Join-Path $Res "drawable-$d\intro_tg_plane.png")
    }
  }
  $intro, $g = New-Canvas ([int][math]::Ceiling(900 * $Logo.Width / $Logo.Height)) 900 ''
  Draw-Centered $g $Logo 900 ($intro.Width / 2) 450
  $g.Dispose()
  Save-Png $intro (Join-Path $Res 'drawable-nodpi\citchat_intro_logo.png')
  # xxhdpi (3x) versions; Android scales them for other densities
  Save-Png (New-Logo 168 155) (Join-Path $Res 'drawable-xxhdpi\deproko_logo_telegram_passcode_56.png')
  Save-Png (New-Logo 54 51) (Join-Path $Res 'drawable-xxhdpi\deproko_logo_telegram_18.png')
  Save-Png (New-Logo 72 60) (Join-Path $Res 'drawable-xxhdpi\baseline_logo_telegram_24.png')
  Save-Png (New-LauncherIcon 512 $true) (Join-Path $PSScriptRoot 'play-store-512.png')
  "Icones gerados em $Res"
}

if ($PreviewDir) {
  New-Item -ItemType Directory -Force -Path $PreviewDir | Out-Null
  Save-Png (New-LauncherIcon 192 $false '#D9E3E3') (Join-Path $PreviewDir 'launcher-192.png')
  Save-Png (New-NotificationIcon 96 '#37474F') (Join-Path $PreviewDir 'notification-96.png')
  $monoPreview, $g = New-Canvas 216 216 '#37474F'
  Draw-Centered $g $Mono 120 108 108
  $g.Dispose()
  Save-Png $monoPreview (Join-Path $PreviewDir 'monochrome.png')
  "Previas em $PreviewDir"
}
$Mono.Dispose()
$Logo.Dispose()
