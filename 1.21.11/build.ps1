# 编译 + 打包 PlayerInsight（Paper 1.21.11，Java 21 字节码）
# 用法：在 PowerShell 中执行  .\build.ps1
# 需要：JDK 21；依赖 jar 放在 lib/，Gson 放在 shaded/
$ErrorActionPreference = "Continue"

$base = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = "C:\Program Files\Java\jdk-21"
$javac = "$jdk\bin\javac.exe"
$jar = "$jdk\bin\jar.exe"
if (-not (Test-Path $javac)) {
    $javac = "javac"
    $jar = "jar"
}

$classpath = @(
    "$base\lib\paper-api-1.21.11.jar",
    "$base\lib\adventure-api-4.26.1.jar",
    "$base\lib\adventure-key-4.26.1.jar",
    "$base\lib\adventure-text-serializer-plain-4.26.1.jar",
    "$base\lib\examination-api-1.3.0.jar",
    "$base\lib\bungeecord-chat-1.21.jar",
    "$base\shaded"
) -join ";"

foreach ($need in @("$base\lib\paper-api-1.21.11.jar", "$base\lib\adventure-api-4.26.1.jar", "$base\shaded")) {
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

Write-Host "== 编译 =="
$src = Get-ChildItem -Recurse "$base\src" -Filter *.java | ForEach-Object { $_.FullName }
$javacOut = & $javac --release 21 -encoding UTF-8 -Xlint:-options -classpath $classpath -d $classes $src 2>&1 | Out-String
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
$outJar = "$base\dist\PlayerInsight-2.1.0-1.21.11.jar"
Remove-Item -Force $outJar -ErrorAction SilentlyContinue
& $jar cf $outJar -C $stage .
if ($LASTEXITCODE -ne 0) {
    Write-Host "打包失败（exit=$LASTEXITCODE）" -ForegroundColor Red
    exit 1
}

Write-Host "== 完成 =="
Get-ChildItem "$base\dist" | Select-Object Name, Length
