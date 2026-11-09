$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (Get-NetTCPConnection -LocalPort 9091 -State Listen -ErrorAction SilentlyContinue) {
    throw '9091 服务已经启动。请先停止旧服务，避免重复启动。'
}
$projectJavaCandidates = @(
    $env:JAVA_HOME
    [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
    [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
)
$projectJavaHome = $projectJavaCandidates | Where-Object {
    $_ -and
    (Test-Path -LiteralPath (Join-Path $_ 'bin\java.exe') -PathType Leaf) -and
    (Test-Path -LiteralPath (Join-Path $_ 'bin\javac.exe') -PathType Leaf)
} | Select-Object -First 1
if (-not $projectJavaHome) {
    throw 'JAVA_HOME 未指向有效的 JDK。请将用户环境变量 JAVA_HOME 设置为 JDK 17 或更高版本的安装目录。'
}
$env:JAVA_HOME = $projectJavaHome
$projectJavaPath = Join-Path $projectJavaHome 'bin\java.exe'
Write-Host "使用 JDK：$projectJavaHome"
$projectMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($projectMaven) {
    $projectMavenPath = $projectMaven.Source
} else {
    $projectMavenPath = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.m2\wrapper\dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $projectMavenPath) { throw '未找到 Maven，请先安装 Maven 或配置 PATH。' }
& $projectMavenPath --global-settings (Join-Path $PSScriptRoot '.mvn\settings.xml') clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw '构建失败，服务未启动。请查看上方 Maven 的具体错误信息。' }
& $projectJavaPath -jar (Join-Path $PSScriptRoot 'target\springboot-hello-1.0-SNAPSHOT.jar')
if ($LASTEXITCODE -ne 0) { throw '服务启动或运行失败。请查看上方 Java 的具体错误信息。' }
