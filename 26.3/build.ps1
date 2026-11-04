# 编译 + 打包 PlayerInsight（Paper 26.3，Java 25 字节码）
# 用法：在 PowerShell 中执行  .\build.ps1
# 需要：JDK 25+（Paper 26.3 最低 Java 25）；依赖 jar 放在 lib/，重定位后的 Gson 放在 shaded/
$ErrorActionPreference = "Continue"

$base = Split-Path -Parent $MyInvocation.MyCommand.Path
# JDK 25 起都能编（Paper 26.3 要求运行时最低 Java 25），按顺序找第一个可用的
$jdkCandidates = @("C:\Program Files\Java\jdk-25")
if ($env:PI_JDK) { $jdkCandidates = @($env:PI_JDK) + $jdkCandidates }
$jdkCandidates += @(Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match '^jdk-(2[5-9]|[3-9]\d)' } | Sort-Object Name | ForEach-Object { $_.FullName })

$jdk = $null
foreach ($candidate in $jdkCandidates) {
    if ($candidate -and (Test-Path "$candidate\bin\javac.exe")) { $jdk = $candidate; break }
}
if ($jdk) {
    $javac = "$jdk\bin\javac.exe"
    $jar = "$jdk\bin\jar.exe"
} else {
    Write-Host "未找到 JDK 25+，尝试使用 PATH 中的 javac（需 25 或更高）" -ForegroundColor Yellow
    $javac = "javac"
    $jar = "jar"
}

$classpath = @(
    "$base\lib\paper-api-26.3.jar",
    "$base\lib\adventure-api-5.2.0.jar",
    "$base\lib\adventure-key-5.2.0.jar",
    "$base\lib\adventure-text-serializer-plain-5.2.0.jar",
    "$base\lib\annotations-26.0.2.jar",
    "$base\lib\guava-33.6.0-jre.jar",
    "$base\lib\bungeecord-chat-1.21.jar",
    "$base\shaded"
) -join ";"

foreach ($need in @("$base\lib\paper-api-26.3.jar", "$base\lib\adventure-api-5.2.0.jar", "$base\shaded")) {
    if (-not (Test-Path $need)) {
        Write-Host "缺少依赖: $need（请先按 README 准备 lib/ 与 shaded/）" -ForegroundColor Red
        exit 1
    }
}

$classes = "$base\build\classes"
$stage = "$base\build\stage"

Write-Host "== 清理 =="
Remove-Item -Recurse -Force "$base\build" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes, $stage | Out-Null

Write-Host "== 编译（$javac）=="
$src = Get-ChildItem -Recurse "$base\src" -Filter *.java | ForEach-Object { $_.FullName }
$javacOut = & $javac --release 25 -encoding UTF-8 -Xlint:-options -classpath $classpath -d $classes $src 2>&1 | Out-String
if ($LASTEXITCODE -ne 0) {
    Write-Host $javacOut
    Write-Host "编译失败（exit=$LASTEXITCODE）" -ForegroundColor Red
    exit 1
}

Write-Host "== 打包 =="
Copy-Item -Recurse "$classes\*" $stage -Force
Copy-Item -Recurse "$base\shaded\com" $stage -Force
Copy-Item "$base\resources\plugin.yml" $stage -Force
Copy-Item "$base\resources\config.yml" $stage -Force
New-Item -ItemType Directory -Force -Path "$base\dist" | Out-Null
$outJar = "$base\dist\PlayerInsight-2.3.0-26.3.jar"
Remove-Item -Force $outJar -ErrorAction SilentlyContinue
& $jar cf $outJar -C $stage .
if ($LASTEXITCODE -ne 0) {
    Write-Host "打包失败（exit=$LASTEXITCODE）" -ForegroundColor Red
    exit 1
}

Write-Host "== 完成 =="
Get-ChildItem "$base\dist" | Select-Object Name, Length
