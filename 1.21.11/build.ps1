# 编译 + 打包 PlayerInsight（Paper 1.21.11，Java 21 字节码）
# 用法：在 PowerShell 中执行  .\build.ps1
$ErrorActionPreference = "Stop"

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
    "$base\shaded"
) -join ";"

$classes = "$base\build\classes"
$stage = "$base\build\stage"

Write-Host "== 清理 =="
Remove-Item -Recurse -Force "$base\build" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes, $stage | Out-Null

Write-Host "== 编译 =="
$src = Get-ChildItem -Recurse "$base\src" -Filter *.java | ForEach-Object { $_.FullName }
& $javac --release 21 -encoding UTF-8 -Xlint:-options -classpath $classpath -d $classes $src

Write-Host "== 打包 =="
Copy-Item -Recurse "$classes\*" $stage -Force
Copy-Item -Recurse "$base\shaded\com" $stage -Force
Copy-Item "$base\resources\plugin.yml" $stage -Force
Copy-Item "$base\resources\config.yml" $stage -Force
New-Item -ItemType Directory -Force -Path "$base\dist" | Out-Null
$outJar = "$base\dist\PlayerInsight-1.21.11-4.jar"
Remove-Item -Force $outJar -ErrorAction SilentlyContinue
& $jar cf $outJar -C $stage .

Write-Host "== 完成 =="
Get-ChildItem "$base\dist" | Select-Object Name, Length
