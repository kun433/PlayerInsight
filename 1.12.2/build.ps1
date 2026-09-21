# 编译 + 打包 PlayerInsight（Paper/Spigot 1.12.2，Java 8 字节码）
# 用法：在 PowerShell 中执行  .\build.ps1
$ErrorActionPreference = "Stop"

$base = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = "C:\Program Files\Java\jdk-11"
$javac = "$jdk\bin\javac.exe"
$jar = "$jdk\bin\jar.exe"
if (-not (Test-Path $javac)) {
    # 退回到 PATH 里的 javac（需要能编译 --release 8）
    $javac = "javac"
    $jar = "jar"
}

$api = "$base\lib\paper-api-1.12.2.jar"
$chat = "$base\lib\bungeecord-chat.jar"
$shaded = "$base\shaded"
$classes = "$base\build\classes"
$stage = "$base\build\stage"

Write-Host "== 清理 =="
Remove-Item -Recurse -Force "$base\build" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $classes, $stage | Out-Null

Write-Host "== 编译 =="
$src = Get-ChildItem -Recurse "$base\src" -Filter *.java | ForEach-Object { $_.FullName }
& $javac --release 8 -encoding UTF-8 -Xlint:-options -classpath "$api;$chat;$shaded" -d $classes $src

Write-Host "== 打包 =="
Copy-Item -Recurse "$classes\*" $stage -Force
Copy-Item -Recurse "$shaded\com" $stage -Force
Copy-Item "$base\resources\plugin.yml" $stage -Force
Copy-Item "$base\resources\config.yml" $stage -Force
New-Item -ItemType Directory -Force -Path "$base\dist" | Out-Null
$outJar = "$base\dist\PlayerInsight-1.12.2-4.jar"
Remove-Item -Force $outJar -ErrorAction SilentlyContinue
& $jar cf $outJar -C $stage .

Write-Host "== 完成 =="
Get-ChildItem "$base\dist" | Select-Object Name, Length
