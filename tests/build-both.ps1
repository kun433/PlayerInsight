# 一键重建两个版本并做一致性回放（共享代码同步 + 完整性守卫）
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)   # tests/ 的上一级 = 项目根
$v12 = "$root\1.12.2"; $v21 = "$root\1.21.11"
$javac11 = "C:\Program Files\Java\jdk-11\bin\javac.exe"
$javac21 = "C:\Program Files\Java\jdk-21\bin\javac.exe"
$jar11 = "C:\Program Files\Java\jdk-11\bin\jar.exe"
$jar21 = "C:\Program Files\Java\jdk-21\bin\jar.exe"
$java11 = "C:\Program Files\Java\jdk-11\bin\java.exe"
$java21 = "C:\Program Files\Java\jdk-21\bin\java.exe"

$cp12 = "$v12\build\classes;$v12\lib\paper-api-1.12.2.jar;$v12\lib\bungeecord-chat.jar;$v12\shaded"
$cp21 = "$v21\build\classes;$v21\lib\paper-api-1.21.11.jar;$v21\lib\adventure-api-4.26.1.jar;$v21\lib\adventure-key-4.26.1.jar;$v21\lib\adventure-text-serializer-plain-4.26.1.jar;$v21\lib\examination-api-1.3.0.jar;$v21\lib\bungeecord-chat-1.21.jar;$v21\shaded"

function Build($base, $javac, $jar, $cp, $jarName) {
    Remove-Item -Recurse -Force "$base\build" -ErrorAction SilentlyContinue
    New-Item -ItemType Directory -Force -Path "$base\build\classes","$base\build\stage" | Out-Null
    $src = Get-ChildItem -Recurse "$base\src" -Filter *.java | ForEach-Object { $_.FullName }
    $log = "$base\build\javac.log"
    $javacOut = & $javac --release $(if ($base -like "*1.21.11") { 21 } else { 8 }) -encoding UTF-8 -Xlint:-options -classpath $cp -d "$base\build\classes" $src 2>&1 | Out-String; Set-Content -Path $log -Value $javacOut -Encoding UTF8
    if ($LASTEXITCODE -ne 0) {
        Write-Host "!! 编译失败 $base"
        [IO.File]::ReadAllText($log, [System.Text.Encoding]::GetEncoding(936)) -split "`n" | Select-Object -First 40 | ForEach-Object { Write-Host $_ }
        throw "编译失败"
    }
    Copy-Item -Recurse "$base\build\classes\*" "$base\build\stage" -Force
    Copy-Item -Recurse "$base\shaded\com" "$base\build\stage" -Force
    Copy-Item "$base\resources\plugin.yml" "$base\build\stage" -Force
    Copy-Item "$base\resources\config.yml" "$base\build\stage" -Force
    New-Item -ItemType Directory -Force -Path "$base\dist" | Out-Null
    Remove-Item -Force "$base\dist\$jarName" -ErrorAction SilentlyContinue
    & $jar cf "$base\dist\$jarName" -C "$base\build\stage" .
    Write-Host ("OK " + $jarName + " " + (Get-Item "$base\dist\$jarName").Length)
}

# 1) 共享文件同步：1.12.2 是共享代码的来源，但只同步两版应当逐字相同的文件。
#
#    下面三类文件两版**故意不同**，绝不能覆盖，否则会把 1.21.11 的插件联动层弄丢：
#      - src/com/playerinsight/PlayerInsight.java         （1.21.11 多整套插件联动层与 /pi pd）
#      - src/com/playerinsight/report/ReportGenerator.java（1.21.11 多 extraSection 重载）
#      - resources/config.yml                            （1.21.11 多 integrations 段落）
#    另外各版本的 Compat.java 本来就是版本相关，也不参与同步。
Copy-Item -Force "$v12\src\com\playerinsight\model\PlayerStats.java" "$v21\src\com\playerinsight\model\"
Copy-Item -Force "$v12\src\com\playerinsight\report\DailyReportGenerator.java" "$v21\src\com\playerinsight\report\"
Copy-Item -Force "$v12\src\com\playerinsight\report\DataExport.java" "$v21\src\com\playerinsight\report\"
Copy-Item -Force "$v12\src\com\playerinsight\report\RecordGenerator.java" "$v21\src\com\playerinsight\report\"
Copy-Item -Recurse -Force "$v12\src\com\playerinsight\store\*" "$v21\src\com\playerinsight\store\"
Copy-Item -Recurse -Force "$v12\src\com\playerinsight\util\*" "$v21\src\com\playerinsight\util\"

# 2) 完整性守卫：1.21.11 独有的联动层必须还在，否则这次打包会把功能悄悄弄丢（宁可直接失败）
$guard = @{
    "$v21\src\com\playerinsight\integration\PluginIntegrations.java" = "slimefunEntries"
    "$v21\src\com\playerinsight\integration\PlaceholderApi.java"     = "setPlaceholders"
    "$v21\src\com\playerinsight\integration\PluginData.java"         = "markdown"
    "$v21\src\com\playerinsight\PlayerInsight.java"                  = "PluginIntegrations"
    "$v21\src\com\playerinsight\report\ReportGenerator.java"         = "extraSection"
    "$v21\resources\config.yml"                                      = "integrations:"
}
foreach ($f in $guard.Keys) {
    if (-not (Test-Path $f)) { throw "缺少 1.21.11 独有文件: $f" }
    if (-not ([IO.File]::ReadAllText($f).Contains($guard[$f]))) { throw "1.21.11 独有文件被覆盖或损坏: $f" }
}

# 3) 编译打包
Build $v12 $javac11 $jar11 $cp12 "PlayerInsight-2.1.2-1.12.2.jar"
Build $v21 $javac21 $jar21 $cp21 "PlayerInsight-2.1.2-1.21.11.jar"

# 4) 一致性回放
& $javac11 -encoding UTF-8 -classpath $cp12 -d "$root\tests" "$root\tests\ReplayHarness.java"
$sample = "$root\tests\sample-v5-trail.jsonl"
& $java11 -classpath "$root\tests;$cp12" ReplayHarness $sample "$root\tests\out\v4-12" --by-name "2026-09" "2026-09-17" | Out-Null
& $java21 -classpath "$root\tests;$cp21" ReplayHarness $sample "$root\tests\out\v4-21" --by-name "2026-09" "2026-09-17" | Out-Null

function Norm($p) { (Get-Content -LiteralPath $p -Encoding UTF8) | Where-Object { $_ -notmatch '生成时间|快照时间' } }
$ok = $true
foreach ($f in @("daily.md", "monthly-all-players.md", "Alex_CN-2026-09-report.md", "MC_Yeko-2026-09-report.md")) {
    $d = Compare-Object (Norm "$root\tests\out\v4-12\$f") (Norm "$root\tests\out\v4-21\$f")
    if ($d) { Write-Host ("!! " + $f + " 两版不一致"); $ok = $false } else { Write-Host ("✔ " + $f + " 两版一致") }
}
if (-not $ok) { throw "两版报告不一致" }
Write-Host "全部通过"
