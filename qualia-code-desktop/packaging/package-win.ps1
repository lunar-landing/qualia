# Qualia Code Desktop - Windows 打包脚本（jpackage app-image）
#
# 前置要求：
#   1. 已安装完整 JDK 17（含 jpackage，JRE 不含），且 java/jpackage 在 PATH 中
#   2. Windows 10/11 系统自带 WebView2 运行时（内嵌浏览器内核）
#   3. 已安装 Node.js（>= 20.19 或 >= 22.12），用于构建 qualia-code-ui 前端
#
# 用法（在项目根目录 PowerShell 执行）：
#   .\qualia-code-desktop\packaging\package-win.ps1
#
# 产物：qualia-code-desktop\target\dist\<version>\Qualia Code\Qualia Code.exe（免安装目录，可整体 zip 分发）

$ErrorActionPreference = "Stop"

$ProjectRoot = Resolve-Path "$PSScriptRoot\..\.."
$ModuleDir = Join-Path $ProjectRoot "qualia-code-desktop"
$TargetDir = Join-Path $ModuleDir "target"

# 从父 pom.xml 读取 revision 属性作为版本号
$PomContent = Get-Content (Join-Path $ProjectRoot "pom.xml") -Raw
if ($PomContent -match '<revision>(.+?)</revision>') {
    $Version = $Matches[1]
} else {
    throw "未在 pom.xml 中找到 revision 属性"
}
Write-Host "==> 版本号: $Version"

$DistDir = Join-Path (Join-Path $TargetDir "dist") $Version

Write-Host "==> 构建可执行 jar（含全部依赖）..."
Set-Location $ProjectRoot
# 先安装 core/code 到本地仓库，再打包 desktop 模块的 shaded jar
& mvn -q -pl qualia-code-desktop -am clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Maven 构建失败" }

# 构建前端：npm run build 产物直写 qualia-code/target/classes/static（vite outDir 约定）。
# 必须在 clean 之后执行，否则产物会被 clean 删除。
$UiDir = Join-Path $ProjectRoot "qualia-code-ui"
if (-not (Test-Path (Join-Path $UiDir "node_modules"))) {
    Write-Host "==> 安装前端依赖 (npm install)..."
    Set-Location $UiDir
    & npm install --no-fund --no-audit
    if ($LASTEXITCODE -ne 0) { Set-Location $ProjectRoot; throw "npm install 失败" }
    Set-Location $ProjectRoot
}
Write-Host "==> 构建前端 (npm run build)..."
Set-Location $UiDir
& npm run build
if ($LASTEXITCODE -ne 0) { Set-Location $ProjectRoot; throw "前端构建失败" }
Set-Location $ProjectRoot

# 无 clean 增量重打：static 已在 target/classes 中，重打 qualia-code jar 并重新 shade 进 uber-jar
Write-Host "==> 重新打包（含前端静态资源，不执行 clean）..."
& mvn -q -pl qualia-code-desktop -am package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "重新打包失败" }

# 定位 shaded jar（shade 插件覆盖原 jar，同名输出）
$Jar = Get-ChildItem -Path $TargetDir -Filter "qualia-code-desktop-*.jar" |
    Where-Object { $_.Name -notlike "*original*" } |
    Select-Object -First 1
if (-not $Jar) { throw "未找到可执行 jar" }
Write-Host "==> 使用 jar: $($Jar.Name)"

# 准备 input 目录（jpackage 要求应用 jar 集中在一个目录）
$InputDir = Join-Path $TargetDir "jpackage-input"
if (Test-Path $InputDir) { Remove-Item $InputDir -Recurse -Force }
New-Item -ItemType Directory -Path $InputDir | Out-Null
Copy-Item $Jar.FullName -Destination $InputDir

if (Test-Path $DistDir) { Remove-Item $DistDir -Recurse -Force }
New-Item -ItemType Directory -Path $DistDir | Out-Null

$IconArg = @()
$IconPath = Join-Path $PSScriptRoot "app.ico"
if (Test-Path $IconPath) { $IconArg = @("--icon", $IconPath) }

# 定位 jpackage（仅完整 JDK 含）：优先 JAVA_HOME，其次 PATH，最后回退到已知 JDK 安装路径
function Resolve-JPackage {
    if ($env:JAVA_HOME) {
        $p = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
        if (Test-Path $p) { return $p }
    }
    $cmd = Get-Command jpackage -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $fallback = "D:\Environments\Java\jdk-21.0.11\bin\jpackage.exe"
    if (Test-Path $fallback) { return $fallback }
    throw "未找到 jpackage，请安装完整 JDK 或设置 JAVA_HOME 指向 JDK 根目录"
}
$JPackage = Resolve-JPackage
Write-Host "==> 使用 jpackage: $JPackage"

Write-Host "==> 运行 jpackage 生成 app-image..."
& $JPackage `
    --type app-image `
    --name "Qualia Code" `
    --app-version $Version `
    --input $InputDir `
    --main-jar $Jar.Name `
    --main-class cn.lunarlanding.qualia.code.desktop.DesktopLauncher `
    --dest $DistDir `
    --java-options "-Dfile.encoding=UTF-8" `
    @IconArg
if ($LASTEXITCODE -ne 0) { throw "jpackage 失败" }

Write-Host ""
Write-Host "==> 完成：$DistDir\Qualia Code\Qualia Code.exe"
Write-Host "   可将整个 'Qualia Code' 目录打包 zip 分发（免安装）。"
