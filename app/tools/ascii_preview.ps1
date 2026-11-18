# ASCII 判形：把图标降采样成字符画，用于无视觉环境下检查剪影
# 用法: pwsh -File ascii_preview.ps1 [-Path <png>] [-N 56]
param(
    [string]$Path = "$env:TEMP\skipper-icon\icon-1024.png",
    [int]$N = 56
)
Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$b = [System.Drawing.Bitmap]::new($Path)
$w = $b.Width; $h = $b.Height
$sb = New-Object System.Text.StringBuilder
for ($r = 0; $r -lt $N; $r++) {
    for ($c = 0; $c -lt $N; $c++) {
        $x = [int](($c + 0.5) * $w / $N); $y = [int](($r + 0.5) * $h / $N)
        $px = $b.GetPixel($x, $y)
        $ch = '.'
        if ($px.A -lt 40) { $ch = ' ' }                                              # 透明
        elseif ($px.R -lt 100 -and $px.G -lt 100 -and $px.B -lt 160) { $ch = '@' }    # 墨（眼）
        elseif ($px.A -gt 120 -and $px.B -gt 170 -and $px.R -lt 200) { $ch = '#' }    # 鲸身/鳍 蓝
        elseif ($px.R -gt 245 -and $px.G -gt 245 -and $px.B -gt 245) { $ch = '.' }    # 近白
        elseif ($px.R -gt 225 -and $px.G -gt 235) { $ch = ',' }                       # 浅蓝底
        else { $ch = '+' }
        [void]$sb.Append($ch)
    }
    [void]$sb.Append("`n")
}
$txt = $sb.ToString()
$txt
# bbox 统计
$minR = 999; $maxR = -1; $minC = 999; $maxC = -1
$lines = $txt -split "`n"
for ($r = 0; $r -lt $N; $r++) {
    $ln = $lines[$r]
    for ($c = 0; $c -lt $ln.Length; $c++) {
        $ch = $ln[$c]
        if ($ch -eq '#' -or $ch -eq '@') {
            if ($r -lt $minR) { $minR = $r }; if ($r -gt $maxR) { $maxR = $r }
            if ($c -lt $minC) { $minC = $c }; if ($c -gt $maxC) { $maxC = $c }
        }
    }
}
"---"
"鲸体 bbox: rows $minR..$maxR  cols $minC..$maxC"
"高度占比: {0:P0}  宽度占比: {1:P0}" -f (($maxR - $minR + 1) / $N), (($maxC - $minC + 1) / $N)
$b.Dispose()