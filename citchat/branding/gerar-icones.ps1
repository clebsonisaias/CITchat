# Gera os PNGs de marca do CITchat a partir do simbolo oficial da CITmax (hexagono com circuito),
# direto nas pastas de recursos do app (Windows PowerShell 5.1 + GDI+).
#
#   powershell -ExecutionPolicy Bypass -File citchat\branding\gerar-icones.ps1
#   powershell -ExecutionPolicy Bypass -File citchat\branding\gerar-icones.ps1 -PreviewDir $env:TEMP\citchat-preview
#
# O simbolo fica em citchat/branding/simbolo-citmax.png (branco com transparencia). Ele foi extraido
# da versao negativa do Manual da marca CITmax (pagina 26: simbolo branco sobre #00C896). Para
# recriar a partir de uma renderizacao dessa pagina: -FromRender caminho\da\renderizacao.png
param(
  [string]$PreviewDir = '',
  [switch]$PreviewOnly,
  [string]$FromRender = '',
  # Manual da marca: Inovacao (cor 1)
  [string]$BackgroundColor = '#00C896'
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class CitSymbol {
  // Render of the negative logo: white symbol over a green (#00C896) area, possibly with a white page
  // margin on the left. Returns the symbol cropped to its bounds, white with alpha taken from the red channel.
  public static Bitmap Extract (Bitmap src) {
    int w = src.Width, h = src.Height;
    Bitmap argb = src.Clone(new Rectangle(0, 0, w, h), PixelFormat.Format32bppArgb);
    BitmapData data = argb.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
    byte[] px = new byte[w * h * 4];
    Marshal.Copy(data.Scan0, px, 0, px.Length);
    argb.UnlockBits(data);
    argb.Dispose();
    Func<int, int, int> red = (x, y) => px[(y * w + x) * 4 + 2];

    // Limits of the green area (the render may include white page margins around it)
    int x0 = 0;
    while (x0 < w - 1 && red(x0, h / 2) > 60) x0++;
    int y0 = 0;
    while (y0 < h - 1 && red(x0 + 5, y0) > 60) y0++;
    int x1 = w - 1;
    while (x1 > x0 && red(x1, y0 + 5) > 60) x1--;
    int y1 = h - 1;
    while (y1 > y0 && red(x0 + 5, y1) > 60) y1--;
    int minX = w, minY = h, maxX = -1, maxY = -1;
    for (int y = y0; y <= y1; y++) {
      for (int x = x0; x <= x1; x++) {
        if (red(x, y) > 128) {
          if (x < minX) minX = x;
          if (x > maxX) maxX = x;
          if (y < minY) minY = y;
          if (y > maxY) maxY = y;
        }
      }
    }
    if (maxX < 0) throw new InvalidOperationException("Symbol not found");
    minX = Math.Max(x0, minX - 2); minY = Math.Max(y0, minY - 2);
    maxX = Math.Min(w - 1, maxX + 2); maxY = Math.Min(h - 1, maxY + 2);
    int cw = maxX - minX + 1, ch = maxY - minY + 1;

    Bitmap result = new Bitmap(cw, ch, PixelFormat.Format32bppArgb);
    BitmapData outData = result.LockBits(new Rectangle(0, 0, cw, ch), ImageLockMode.WriteOnly, PixelFormat.Format32bppArgb);
    byte[] outPx = new byte[cw * ch * 4];
    for (int y = 0; y < ch; y++) {
      for (int x = 0; x < cw; x++) {
        int o = (y * cw + x) * 4;
        outPx[o] = 255; outPx[o + 1] = 255; outPx[o + 2] = 255;
        outPx[o + 3] = (byte) red(minX + x, minY + y);
      }
    }
    Marshal.Copy(outPx, 0, outData.Scan0, outPx.Length);
    result.UnlockBits(outData);
    return result;
  }
}
'@

$Repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Res = Join-Path $Repo 'app\src\main\res'
$SymbolPath = Join-Path $PSScriptRoot 'simbolo-citmax.png'
$Brand = [System.Drawing.ColorTranslator]::FromHtml($BackgroundColor)

if ($FromRender) {
  $render = New-Object System.Drawing.Bitmap($FromRender)
  $extracted = [CitSymbol]::Extract($render)
  $render.Dispose()
  $extracted.Save($SymbolPath, [System.Drawing.Imaging.ImageFormat]::Png)
  "Simbolo salvo em $SymbolPath ($($extracted.Width)x$($extracted.Height))"
  $extracted.Dispose()
}
$Symbol = New-Object System.Drawing.Bitmap($SymbolPath)

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

# Draws the symbol with the given height, centred on (cx, cy)
function Draw-Symbol($g, [double]$Height, [double]$CenterX, [double]$CenterY) {
  $width = $Height * $Symbol.Width / $Symbol.Height
  $rect = New-Object System.Drawing.RectangleF([single]($CenterX - $width / 2), [single]($CenterY - $Height / 2), [single]$width, [single]$Height)
  $g.DrawImage($Symbol, $rect)
}

# Launcher icon for API < 26: brand circle (44dp in 48dp) with the white symbol
function New-LauncherIcon([int]$Size, [bool]$FullBleed, [string]$Matte = '') {
  $bmp, $g = New-Canvas $Size $Size $Matte
  $brush = New-Object System.Drawing.SolidBrush($Brand)
  if ($FullBleed) {
    $g.FillRectangle($brush, 0, 0, $Size, $Size)
    Draw-Symbol $g ($Size * 0.58) ($Size / 2) ($Size / 2)
  } else {
    $d = $Size * 44.0 / 48
    $g.FillEllipse($brush, [single](($Size - $d) / 2), [single](($Size - $d) / 2), [single]$d, [single]$d)
    Draw-Symbol $g ($d * 0.64) ($Size / 2) ($Size / 2)
  }
  $g.Dispose()
  return $bmp
}

# Adaptive icon foreground and monochrome layer: 108dp canvas, symbol 46dp tall (64% of the visible 72dp)
function New-AdaptiveForeground([double]$Density) {
  $size = [int][math]::Round(108 * $Density)
  $bmp, $g = New-Canvas $size $size ''
  Draw-Symbol $g (46 * $Density) ($size / 2) ($size / 2)
  $g.Dispose()
  return $bmp
}

# Status bar icon: white symbol 22dp tall inside 24dp
function New-NotificationIcon([int]$Size, [string]$Matte = '') {
  $bmp, $g = New-Canvas $Size $Size $Matte
  Draw-Symbol $g ($Size * 22.0 / 24) ($Size / 2) ($Size / 2)
  $g.Dispose()
  return $bmp
}

# Logos drawn by the app itself (passcode, call screen, source code menu), white, tinted by the app
function New-Logo([int]$CanvasPx, [double]$SymbolPx) {
  $bmp, $g = New-Canvas $CanvasPx $CanvasPx ''
  Draw-Symbol $g $SymbolPx ($CanvasPx / 2) ($CanvasPx / 2)
  $g.Dispose()
  return $bmp
}

# Intro texture drawn over the brand sphere (IntroRenderer.c: sphere 148x148, texture 82x74 units,
# anchor (6, -5) moves the texture 6 units left and 5 down, so the symbol is drawn 6 right and 5 up).
function New-IntroTexture([double]$Density, [string]$Matte = '') {
  $w = [int][math]::Round(82 * $Density)
  $h = [int][math]::Round(74 * $Density)
  $bmp, $g = New-Canvas $w $h $Matte
  Draw-Symbol $g (60 * $Density) ((41 + 6) * $Density) ((37 - 5) * $Density)
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
    Save-Png (New-AdaptiveForeground $f) (Join-Path $Res "mipmap-$d\citchat_adaptive_fg.png")
    if ($d -ne 'xxxhdpi') {
      Save-Png (New-IntroTexture $f) (Join-Path $Res "drawable-$d\intro_tg_plane.png")
    }
  }
  # xxhdpi (3x) versions; Android scales them for other densities
  Save-Png (New-Logo 168 155) (Join-Path $Res 'drawable-xxhdpi\deproko_logo_telegram_passcode_56.png')
  Save-Png (New-Logo 54 51) (Join-Path $Res 'drawable-xxhdpi\deproko_logo_telegram_18.png')
  Save-Png (New-Logo 72 60) (Join-Path $Res 'drawable-xxhdpi\baseline_logo_telegram_24.png')
  Save-Png (New-LauncherIcon 512 $true) (Join-Path $PSScriptRoot 'play-store-512.png')
  "Icones gerados em $Res"
}

if ($PreviewDir) {
  New-Item -ItemType Directory -Force -Path $PreviewDir | Out-Null
  Save-Png (New-LauncherIcon 192 $false '#FFFFFF') (Join-Path $PreviewDir 'launcher-192.png')
  Save-Png (New-NotificationIcon 96 '#37474F') (Join-Path $PreviewDir 'notification-96.png')
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
$Symbol.Dispose()
