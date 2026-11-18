# 生成「小霜跳过」桌面图标 —— 直接使用用户提供的原图（icon-src\source.jpg）
# 不做任何重绘：只做 1) 居中方形裁切 2) 等比缩放 3) 圆角遮罩（TextureBrush 填充）4) 输出 5 个密度
# 用法:
#   pwsh -File make_icon.ps1 -Preview      # 只写临时预览，不碰 res
#   pwsh -File make_icon.ps1               # 写入 app\res\mipmap-*\ic_launcher.png
#   pwsh -File make_icon.ps1 -CornerRadius 0 -Preview   # 输出直角方形（不做圆角）
param(
    [switch]$Preview,
    [string]$Source = (Join-Path $PSScriptRoot 'icon-src\source.jpg'),
    [int]$CropX = -1,      # -1 = 自动居中
    [int]$CropY = -1,      # -1 = 自动居中
    [int]$CropSide = 0,    # 0 = 取源图内可用的最大正方形
    [int]$CornerRadius = 232
)

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot      # app/
$res  = Join-Path $root 'res'
$S    = 1024                                   # 主画布边长

if (-not (Test-Path -LiteralPath $Source)) { throw "找不到源图: $Source" }
$src = [System.Drawing.Bitmap]::new($Source)
$W = $src.Width; $H = $src.Height

# ---------- 方形裁切窗口 ----------
$side = if ($CropSide -gt 0) { $CropSide } else { [Math]::Min($W, $H) }
if ($side -gt $W -or $side -gt $H) { throw "CropSide=$side 超出源图 ${W}x${H}" }
$cx = if ($CropX -ge 0) { $CropX } else { [int](($W - $side) / 2) }
$cy = if ($CropY -ge 0) { $CropY } else { [int](($H - $side) / 2) }
if ($cx -lt 0) { $cx = 0 }
if ($cy -lt 0) { $cy = 0 }
if ($cx + $side -gt $W) { $cx = $W - $side }
if ($cy + $side -gt $H) { $cy = $H - $side }
Write-Host ("源图 {0}x{1}  方形裁切 x={2} y={3} side={4}" -f $W, $H, $cx, $cy, $side)

# ---------- 1) 裁切 + 2) 等比缩放到主画布（无拉伸） ----------
$cropRect = New-Object System.Drawing.Rectangle($cx, $cy, $side, $side)
$flat = New-Object System.Drawing.Bitmap($S, $S, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$gf = [System.Drawing.Graphics]::FromImage($flat)
$gf.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$gf.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$gf.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
$gf.Clear([System.Drawing.Color]::White)
$gf.DrawImage($src, (New-Object System.Drawing.Rectangle(0, 0, $S, $S)),
              $cropRect.X, $cropRect.Y, $cropRect.Width, $cropRect.Height,
              [System.Drawing.GraphicsUnit]::Pixel)
$gf.Dispose()
$src.Dispose()

# ---------- 3) 圆角遮罩：用 TextureBrush + 圆角路径填充，路径外自动透明 ----------
$bmp = New-Object System.Drawing.Bitmap($S, $S, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
$g.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)

if ($CornerRadius -gt 0) {
    $d = $CornerRadius * 2
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $path.AddArc(0, 0, $d, $d, 180, 90)
    $path.AddArc($S - $d, 0, $d, $d, 270, 90)
    $path.AddArc($S - $d, $S - $d, $d, $d, 0, 90)
    $path.AddArc(0, $S - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    $tb = New-Object System.Drawing.TextureBrush($flat)
    $g.FillPath($tb, $path)
    $tb.Dispose(); $path.Dispose()
} else {
    $g.DrawImage($flat, 0, 0, $S, $S)
}
$g.Dispose()

# ---------- 4) 输出 ----------
function Save-Density([int]$px, [string]$path) {
    $dst = New-Object System.Drawing.Bitmap($px, $px, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gd = [System.Drawing.Graphics]::FromImage($dst)
    $gd.InterpolationMode  = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gd.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $gd.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
    $gd.Clear([System.Drawing.Color]::Transparent)
    $gd.DrawImage($bmp, 0, 0, $px, $px)
    $gd.Dispose()
    $dst.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $dst.Dispose()
}

if ($Preview) {
    $outDir = Join-Path $env:TEMP 'skipper-icon'
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    Save-Density $S (Join-Path $outDir 'icon-1024.png')
    foreach ($px in @(192, 96, 48)) { Save-Density $px (Join-Path $outDir "icon-$px.png") }
    foreach ($n in @('icon-1024', 'icon-192', 'icon-96', 'icon-48')) { Join-Path $outDir "$n.png" }
    $g.Dispose(); $bmp.Dispose(); $flat.Dispose()
    return
}

$densities = @{ 'mipmap-mdpi' = 48; 'mipmap-hdpi' = 72; 'mipmap-xhdpi' = 96;
                'mipmap-xxhdpi' = 144; 'mipmap-xxxhdpi' = 192 }
foreach ($k in ($densities.Keys | Sort-Object)) {
    $px  = $densities[$k]
    $dir = Join-Path $res $k
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Save-Density $px (Join-Path $dir 'ic_launcher.png')
    "写出 $k\ic_launcher.png  ${px}x${px}"
}
$bmp.Dispose(); $flat.Dispose()