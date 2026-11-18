# 手动构建 APK（无需 Gradle / Android Studio）
# 依赖：toolchain\jdk21, toolchain\sdk
param([switch]$Clean)

$ErrorActionPreference = 'Stop'
$Root = $PSScriptRoot
$Tool = Join-Path $Root 'toolchain'
$Jdk  = Join-Path $Tool 'jdk21'
$Sdk  = Join-Path $Tool 'sdk'

$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"

$BuildTools = Get-ChildItem (Join-Path $Sdk 'build-tools') -Directory | Sort-Object Name -Descending | Select-Object -First 1
$Platform   = Get-ChildItem (Join-Path $Sdk 'platforms') -Directory | Sort-Object Name -Descending | Select-Object -First 1
if (-not $BuildTools) { throw "build-tools 未安装" }
if (-not $Platform)   { throw "platform 未安装" }

$Aapt2     = Join-Path $BuildTools.FullName 'aapt2.exe'
$D8        = Join-Path $BuildTools.FullName 'd8.bat'
$Zipalign  = Join-Path $BuildTools.FullName 'zipalign.exe'
$Apksigner = Join-Path $BuildTools.FullName 'apksigner.bat'
$Javac     = Join-Path $Jdk 'bin\javac.exe'
$Keytool   = Join-Path $Jdk 'bin\keytool.exe'
$Jar       = Join-Path $Jdk 'bin\jar.exe'
$AndroidJar = Join-Path $Platform.FullName 'android.jar'
$LambdaStubs = Join-Path $BuildTools.FullName 'core-lambda-stubs.jar'

$App = Join-Path $Root 'app'
$Out = Join-Path $Root 'build'
$Gen = Join-Path $Out 'gen'
$Obj = Join-Path $Out 'obj'
$DexDir = Join-Path $Out 'dex'

if ($Clean -and (Test-Path $Out)) { Remove-Item $Out -Recurse -Force }
New-Item -ItemType Directory -Force -Path $Gen,$Obj,$DexDir | Out-Null

function Step($msg) { Write-Host "[STAGE] $msg" -ForegroundColor Cyan }

# ---------------------------------------------------------------- 1. 资源
Step "aapt2 compile 资源"
$flat = Join-Path $Out 'res.zip'
& $Aapt2 compile --dir (Join-Path $App 'res') -o $flat
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile 失败" }

Step "aapt2 link 生成 resources.arsc + R.java"
# 版本号只由 app\AndroidManifest.xml 的 versionCode / versionName 定义，这里不再重复传
& $Aapt2 link -o (Join-Path $Out 'base.apk') `
    -I $AndroidJar `
    --manifest (Join-Path $App 'AndroidManifest.xml') `
    --java $Gen `
    --min-sdk-version 24 --target-sdk-version 34 `
    $flat
if ($LASTEXITCODE -ne 0) { throw "aapt2 link 失败" }

# ---------------------------------------------------------------- 2. 编译
Step "javac 编译 Java 源码"
$srcs = @()
$srcs += (Get-ChildItem (Join-Path $App 'src') -Recurse -Filter *.java | Select-Object -ExpandProperty FullName)
$srcs += (Get-ChildItem $Gen -Recurse -Filter *.java | Select-Object -ExpandProperty FullName)
$argFile = Join-Path $Out 'javac.args'
($srcs | ForEach-Object { $_.Replace('\','/') }) -join "`n" | Set-Content $argFile -Encoding ASCII

& $Javac -source 8 -target 8 -encoding UTF-8 -bootclasspath "$AndroidJar;$LambdaStubs" `
    -classpath "$AndroidJar;$LambdaStubs" -d $Obj -nowarn "@$argFile" 2>&1 | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) { throw "javac 失败" }

# ---------------------------------------------------------------- 3. dex
Step "d8 转 dex"
$classes = Get-ChildItem $Obj -Recurse -Filter *.class | Select-Object -ExpandProperty FullName
& $D8 --release --min-api 24 --lib $AndroidJar --output $DexDir @classes 2>&1 | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) { throw "d8 失败" }

# ---------------------------------------------------------------- 4. 打包
Step "合并 dex 进 apk"
Copy-Item (Join-Path $Out 'base.apk') (Join-Path $Out 'unsigned.apk') -Force
Push-Location $DexDir
& $Jar uf (Join-Path $Out 'unsigned.apk') classes.dex
$rc = $LASTEXITCODE
Pop-Location
if ($rc -ne 0) { throw "jar 打包失败" }

Step "zipalign"
& $Zipalign -f -p 4 (Join-Path $Out 'unsigned.apk') (Join-Path $Out 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw "zipalign 失败" }

# ---------------------------------------------------------------- 5. 签名
$ks = Join-Path $Root 'keystore.jks'
if (-not (Test-Path $ks)) {
    Step "生成签名证书"
    & $Keytool -genkeypair -v -keystore $ks -alias skipper -keyalg RSA -keysize 2048 `
        -validity 10000 -storepass skipper123 -keypass skipper123 `
        -dname "CN=Xiaohan Skipper, O=Local, C=CN" 2>&1 | Out-Null
}

Step "apksigner 签名"
$final = Join-Path $Root 'XiaohanSkipper.apk'
& $Apksigner sign --ks $ks --ks-key-alias skipper `
    --ks-pass pass:skipper123 --key-pass pass:skipper123 `
    --out $final (Join-Path $Out 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw "apksigner 失败" }

Step "校验签名"
& $Apksigner verify --print-certs $final

$size = [math]::Round((Get-Item $final).Length / 1KB, 1)
Write-Host ""
Write-Host "OK -> $final  ($size KB)" -ForegroundColor Green
