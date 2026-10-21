# SDC 开发工具一键安装（Windows 10/11）
# 用法：在本文件所在目录打开 PowerShell，执行
#   powershell -ExecutionPolicy Bypass -File .\install-dev-tools.ps1
# 安装：Git、OpenJDK 17、Maven、IntelliJ IDEA、Claude Code。已装的会跳过。

# 不用 Stop：Windows PowerShell 5.1 下原生命令写 stderr 会被当成终止错误
$ErrorActionPreference = "Continue"
$MavenVersion = "3.9.9"
$ToolsDir = Join-Path $env:USERPROFILE "tools"

function Refresh-Path {
    $env:Path = [Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
                [Environment]::GetEnvironmentVariable("Path", "User")
}

function Add-UserPath($dir) {
    $p = [Environment]::GetEnvironmentVariable("Path", "User")
    if (-not $p) { $p = "" }
    if (($p -split ";") -notcontains $dir) {
        [Environment]::SetEnvironmentVariable("Path", ($p.TrimEnd(";") + ";" + $dir).TrimStart(";"), "User")
    }
}

function Install-Winget($id, $name) {
    Write-Host "`n==> $name" -ForegroundColor Cyan
    winget list -e --id $id --accept-source-agreements *> $null
    if ($LASTEXITCODE -eq 0) { Write-Host "已安装，跳过"; return }
    winget install -e --id $id --accept-source-agreements --accept-package-agreements
    if ($LASTEXITCODE -ne 0) { throw "$name 安装失败（winget 退出码 $LASTEXITCODE）" }
}

if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    throw "未找到 winget。请在 Microsoft Store 安装「应用安装程序」(App Installer) 后重试。"
}

# 1. Git（Claude Code 在 Windows 上依赖 Git Bash，必须先装）
Install-Winget "Git.Git" "Git"

# 2. OpenJDK 17
Install-Winget "Microsoft.OpenJDK.17" "OpenJDK 17"
$jdk = Get-ChildItem "$env:ProgramFiles\Microsoft" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue |
       Sort-Object Name -Descending | Select-Object -First 1
if ($jdk) {
    [Environment]::SetEnvironmentVariable("JAVA_HOME", $jdk.FullName, "User")
    $env:JAVA_HOME = $jdk.FullName
    Add-UserPath (Join-Path $jdk.FullName "bin")
    Write-Host "JAVA_HOME = $($jdk.FullName)"
} else {
    Write-Warning "没找到 JDK 17 安装目录，请手动设置 JAVA_HOME"
}

# 3. Maven（winget 无 Apache 官方包，从 Apache 归档下载）
Write-Host "`n==> Maven $MavenVersion" -ForegroundColor Cyan
$mvnHome = Join-Path $ToolsDir "apache-maven-$MavenVersion"
if (Test-Path (Join-Path $mvnHome "bin\mvn.cmd")) {
    Write-Host "已安装，跳过"
} else {
    New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
    $zip = Join-Path $env:TEMP "apache-maven-$MavenVersion-bin.zip"
    $url = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing -ErrorAction Stop
    Expand-Archive -Path $zip -DestinationPath $ToolsDir -Force -ErrorAction Stop
    Remove-Item $zip
}
[Environment]::SetEnvironmentVariable("MAVEN_HOME", $mvnHome, "User")
Add-UserPath (Join-Path $mvnHome "bin")

# 4. IntelliJ IDEA（2025.3 起 Ultimate/Community 合并为一个包）
Install-Winget "JetBrains.IntelliJIDEA" "IntelliJ IDEA"

# 5. Claude Code
Write-Host "`n==> Claude Code" -ForegroundColor Cyan
Refresh-Path
if (Get-Command claude -ErrorAction SilentlyContinue) {
    Write-Host "已安装，跳过"
} else {
    # 先走官方安装器；它从 Google 存储下载，国内网络常报 ECONNREFUSED。
    try { Invoke-RestMethod https://claude.ai/install.ps1 | Invoke-Expression } catch { Write-Warning "官方安装器失败：$_" }
    Refresh-Path
    $env:Path += ";$env:USERPROFILE\.local\bin"
}
if (-not (Get-Command claude -ErrorAction SilentlyContinue)) {
    # 回退：npm 国内镜像，Windows 二进制由 npmmirror 提供。需要 Node.js 22+。
    Write-Host "改用 npm 国内镜像安装 Claude Code"
    Install-Winget "OpenJS.NodeJS.LTS" "Node.js LTS"
    Refresh-Path
    npm install -g @anthropic-ai/claude-code --registry=https://registry.npmmirror.com
    if ($LASTEXITCODE -ne 0) { Write-Warning "Claude Code 安装失败，见《开发工具安装说明.md》常见问题" }
}

# 自检
Refresh-Path
Write-Host "`n==> 版本自检" -ForegroundColor Cyan
foreach ($cmd in "git --version", "java -version", "mvn -version", "claude --version") {
    Write-Host "> $cmd"
    try { cmd /c "$cmd 2>&1" } catch { Write-Warning "$cmd 失败：$_" }
}

Write-Host "`n完成。请关闭所有终端和 IDEA 后重新打开，环境变量才会生效。" -ForegroundColor Green
Write-Host "下一步：按《DeepSeek配置说明.md》配置 Claude Code。"
