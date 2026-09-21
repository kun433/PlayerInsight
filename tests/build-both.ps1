# 一键重建两个版本并做一致性回放（-4）
$ErrorActionPreference = "Continue"
$root = "C:\Users\zpst1\.proma\agent-workspaces\default\workspace-files\PlayerInsight"
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

# 1) 共享文件同步（保留 1.21.11 的 Compat.java）
Copy-Item -Force "$v12\src\com\playerinsight\model\PlayerStats.java" "$v21\src\com\playerinsight\model\"
Copy-Item -Recurse -Force "$v12\src\com\playerinsight\report\*" "$v21\src\com\playerinsight\report\"
Copy-Item -Force "$v12\src\com\playerinsight\store\LogStore.java" "$v21\src\com\playerinsight\store\"
Copy-Item -Force "$v12\src\com\playerinsight\util\Text.java" "$v21\src\com\playerinsight\util\"
Copy-Item -Force "$v12\resources\config.yml" "$v21\resources\config.yml"
# 主类同步 + 重新套聊天补丁
$mf = "$v21\src\com\playerinsight\PlayerInsight.java"
$utf8 = New-Object System.Text.UTF8Encoding($false)
$m = ([IO.File]::ReadAllText("$v12\src\com\playerinsight\PlayerInsight.java")) -replace "`r`n","`n"
$m = $m.Replace("import org.bukkit.event.player.AsyncPlayerChatEvent;`n",
    "import io.papermc.paper.event.player.AsyncChatEvent;`nimport net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;`n")
$old = "    public void onChat(AsyncPlayerChatEvent e) {"
$new = "    public void onChat(AsyncChatEvent e) {"
if (-not $m.Contains($old)) { throw "聊天事件补丁未匹配" }
$m = $m.Replace($old, $new)
$m = $m.Replace("o.addProperty(`"msg`", e.getMessage());", "o.addProperty(`"msg`", PlainTextComponentSerializer.plainText().serialize(e.message()));")
[IO.File]::WriteAllText($mf, $m, $utf8)

# 2) 编译打包
Build $v12 $javac11 $jar11 $cp12 "PlayerInsight-1.12.2-4.jar"
Build $v21 $javac21 $jar21 $cp21 "PlayerInsight-1.21.11-4.jar"

# 3) 一致性回放
& $javac11 -encoding UTF-8 -classpath $cp12 -d "$root\tests" "$root\tests\ReplayHarness.java"
$sample = "$root\tests\sample-v4-rich.jsonl"
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
