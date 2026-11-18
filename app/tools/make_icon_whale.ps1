# 生成「小霜跳过」桌面图标：DeepSeek 风格大肥鱼（蓝色胖鲸）
# 用法: pwsh -File make_icon.ps1 [-Preview]
param([switch]$Preview)

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot      # app/
$res  = Join-Path $root 'res'
$S    = 1024

function New-RoundRectPath([single]$x, [single]$y, [single]$w, [single]$h, [single]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

# AddBezier 没有「从当前点续画」的重载，用 $script:cx/cy 维护当前点
function Bez($path, [single]$c1x, [single]$c1y, [single]$c2x, [single]$c2y, [single]$ex, [single]$ey) {
    $path.AddBezier([System.Drawing.PointF]::new($script:cx, $script:cy),
                    [System.Drawing.PointF]::new($c1x, $c1y),
                    [System.Drawing.PointF]::new($c2x, $c2y),
                    [System.Drawing.PointF]::new($ex, $ey))
    $script:cx = $ex; $script:cy = $ey
}
function MoveTo($path, [single]$x, [single]$y) { $script:cx = $x; $script:cy = $y; $path.StartFigure() }

$bmp = New-Object System.Drawing.Bitmap($S, $S, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode      = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.PixelOffsetMode    = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)

# ---------- 底板：浅色圆角方 ----------
$bgPath = New-RoundRectPath 0 0 $S $S 232
$bgBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.RectangleF(0, 0, $S, $S)),
    [System.Drawing.Color]::FromArgb(255, 255, 255, 255),
    [System.Drawing.Color]::FromArgb(255, 206, 220, 255),
    90.0)
$g.FillPath($bgBrush, $bgPath)

# ---------- 配色：DeepSeek 蓝 ----------
$ink = [System.Drawing.Color]::FromArgb(255, 16, 28, 84)

$bodyBrush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.RectangleF(120, 280, 900, 600)),
    [System.Drawing.Color]::FromArgb(255, 120, 150, 255),
    [System.Drawing.Color]::FromArgb(255, 58, 86, 242),
    58.0)

# 整体左移，让「身体 + 尾鳍」在画布里居中
$stBase = $g.Save()
$g.TranslateTransform(-58, 0)

# ---------- 尾鳍：5 点人字形（先画尾，根部被身体盖住） ----------
$tail = New-Object System.Drawing.Drawing2D.GraphicsPath
MoveTo $tail 762 466
Bez $tail  862 428  940 350  1008 302       # 上叶外缘 → 上叶尖
Bez $tail  986 430  934 520  902 590        # 上叶内缘 → 尾叉
Bez $tail  934 664  986 754  1008 884       # 下叶内缘 → 下叶尖
Bez $tail  940 838  862 764  762 714        # 下叶外缘 → 尾根
$tail.CloseFigure()
$g.FillPath($bodyBrush, $tail)

# ---------- 身体 ----------
$body = New-Object System.Drawing.Drawing2D.GraphicsPath
MoveTo $body 340 288
Bez $body  230 324  178 452  194 574        # 头部前缘下探
Bez $body  208 738  320 864  472 868        # 下巴 → 肚底
Bez $body  600 872  700 846  762 764        # 肚底 → 尾根下
Bez $body  792 706  796 600  770 536        # 右缘（尾根）
Bez $body  736 440  640 386  520 364        # 背部
Bez $body  440 352  376 312  340 288        # 头顶回起点
$body.CloseFigure()
$g.FillPath($bodyBrush, $body)

# ---------- 肚皮高光 ----------
$belly = New-Object System.Drawing.Drawing2D.GraphicsPath
MoveTo $belly 760 770
Bez $belly  640 846  460 866  330 812
Bez $belly  470 820  640 806  760 770
$belly.CloseFigure()
$g.FillPath((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(58, 255, 255, 255))), $belly)

# ---------- 胸鳍 ----------
$fin = New-Object System.Drawing.Drawing2D.GraphicsPath
MoveTo $fin 466 672
Bez $fin  556 712  588 806  532 862
Bez $fin  468 818  440 736  466 672
$fin.CloseFigure()
$g.FillPath((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 48, 72, 214))), $fin)

# ---------- 眼睛 ----------
$eyeWhite = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$g.FillEllipse($eyeWhite, 264, 416, 122, 122)
$g.FillEllipse((New-Object System.Drawing.SolidBrush($ink)), 290, 442, 72, 72)
$g.FillEllipse($eyeWhite, 304, 458, 26, 26)

# ---------- 微笑 ----------
$smilePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(245, 255, 255, 255), 18)
$smilePen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$smilePen.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
$smile = New-Object System.Drawing.Drawing2D.GraphicsPath
MoveTo $smile 244 648
Bez $smile  308 750  436 756  494 662
$g.DrawPath($smilePen, $smile)

# ---------- 腮红 ----------
$g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(78, 255, 176, 204))), 222, 596, 124, 60)

$g.Restore($stBase)

# ---------- 预览 ----------
if ($Preview) {
    $outDir = Join-Path $env:TEMP 'skipper-icon'
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $bmp.Save((Join-Path $outDir 'icon-1024.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    foreach ($px in @(192, 96, 48)) {
        $small = New-Object System.Drawing.Bitmap($px, $px)
        $gs = [System.Drawing.Graphics]::FromImage($small)
        $gs.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $gs.DrawImage($bmp, 0, 0, $px, $px)
        $small.Save((Join-Path $outDir "icon-$px.png"), [System.Drawing.Imaging.ImageFormat]::Png)
        $gs.Dispose(); $small.Dispose()
    }
    "$outDir\icon-1024.png"
    "$outDir\icon-192.png"
    "$outDir\icon-96.png"
    "$outDir\icon-48.png"
    $g.Dispose(); $bmp.Dispose()
    return
}

# ---------- 输出 5 个密度 ----------
$sizes = @{ 'mipmap-mdpi' = 48; 'mipmap-hdpi' = 72; 'mipmap-xhdpi' = 96;
            'mipmap-xxhdpi' = 144; 'mipmap-xxxhdpi' = 192 }
foreach ($k in ($sizes.Keys | Sort-Object)) {
    $px = $sizes[$k]
    $dir = Join-Path $res $k
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $dst = New-Object System.Drawing.Bitmap($px, $px)
    $gd = [System.Drawing.Graphics]::FromImage($dst)
    $gd.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gd.PixelOffsetMode   = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $gd.DrawImage($bmp, 0, 0, $px, $px)
    $dst.Save((Join-Path $dir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $gd.Dispose(); $dst.Dispose()
    "写出 $k\ic_launcher.png  ${px}x${px}"
}

$g.Dispose(); $bmp.Dispose()