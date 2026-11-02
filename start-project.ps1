$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (Get-NetTCPConnection -LocalPort 9091 -State Listen -ErrorAction SilentlyContinue) {
    throw '9091 服务已经启动。请先停止旧服务，避免重复启动。'
}
$projectMaven = Get-Command mvn.cmd -ErrorAction SilentlyContinue
if ($projectMaven) {
    $projectMavenPath = $projectMaven.Source
} else {
    $projectMavenPath = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.m2\wrapper\dists') -Filter mvn.cmd -Recurse -ErrorAction SilentlyContinue |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $projectMavenPath) { throw '未找到 Maven，请先安装 Maven 或配置 PATH。' }
& $projectMavenPath clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw '编译失败，服务未启动。请修正编译错误后重试。' }
& java -jar (Join-Path $PSScriptRoot 'target\springboot-hello-1.0-SNAPSHOT.jar')
