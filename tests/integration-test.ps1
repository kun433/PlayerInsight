# PlayerInsight 插件联动层的离线测试（不需要服务器、不需要 PlaceholderAPI）。
# 编译并运行 tests/IntegrationTest.java，校验：
#   1) 内置 Paper's Delight 档案的占位符名与官方文档逐条一致
#   2) 内置 Slimefun 档案的占位符名与官方文档逐条一致（模板不带 {player}）
#   3) 解析规则（{player} 替换、在线限定、未解析/空值/null 丢弃、numeric 数值校验）
#   4) PluginData 的 Markdown / JSON / 指令行渲染
#   5) 报告章节只在其有数据时追加，未启用联动时报告与旧版逐行一致
#
# 用法：在 PowerShell 中执行  .\integration-test.ps1 [ -Module 26.3 ]
# 需要：与该构建匹配的 JDK（1.21.11 → JDK 21，26.3 → JDK 25）；先跑过对应目录的 build.ps1
param(
    [string]$Module = "26.3"
)
$ErrorActionPreference = "Stop"

$testsDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = Split-Path -Parent $testsDir
$moduleDir = Join-Path $root $Module

# 26.x 起需要 JDK 25，1.21.x 用 JDK 21
$jdkName = if ($Module -match '^1\.') { "jdk-21" } else { "jdk-25" }
$jdk = "C:\Program Files\Java\$jdkName"
$javac = "$jdk\bin\javac.exe"
$java = "$jdk\bin\java.exe"
if (-not (Test-Path $javac)) {
    $javac = "javac"
    $java = "java"
}

$classes = Join-Path $moduleDir "build\classes"
if (-not (Test-Path $classes)) {
    Write-Host "缺少 $classes，请先在 $Module 目录执行 .\build.ps1" -ForegroundColor Red
    exit 1
}

$libs = (Get-ChildItem (Join-Path $moduleDir "lib") -Filter *.jar | ForEach-Object { $_.FullName }) -join ";"
$classpath = "$classes;" + (Join-Path $moduleDir "shaded") + ";$libs"
$outDir = Join-Path $testsDir "out\it-$Module"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Write-Host "== 编译测试 =="
& $javac -encoding UTF-8 -classpath $classpath -d $outDir (Join-Path $testsDir "IntegrationTest.java")
if ($LASTEXITCODE -ne 0) {
    Write-Host "测试编译失败" -ForegroundColor Red
    exit 1
}

Write-Host "== 运行测试 =="
Push-Location $root
& $java -classpath "$outDir;$classpath" com.playerinsight.integration.IntegrationTest
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) {
    Write-Host "存在失败断言，详见 tests\out\integration-test-result.txt" -ForegroundColor Red
    exit $code
}
Write-Host "全部通过，详见 tests\out\integration-test-result.txt" -ForegroundColor Green
