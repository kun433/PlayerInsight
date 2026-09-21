# 在隔离目录里启动真实的 Paper 服务器，验证插件能否正常加载/注册事件/执行指令。
# 通过 cmd 管道按时间顺序喂入控制台指令，输出写入 console-output.txt。
# 用法: .\smoke-test.ps1 -ServerDir <服务器目录> -JavaExe <java路径> [-StartupWait 75] [-FinalWait 15]
param(
    [Parameter(Mandatory=$true)][string]$ServerDir,
    [Parameter(Mandatory=$true)][string]$JavaExe,
    [int]$StartupWait = 75,
    [int]$FinalWait = 15,
    [string[]]$Commands = @("pi stats", "pi gen monthly 2026-09", "pi blocks MC_Yeko month"),
    [int]$CommandGap = 12,
    [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = "Stop"
$log = Join-Path $ServerDir "console-output.txt"
Remove-Item -Force $log -ErrorAction SilentlyContinue

$parts = @("ping -n $StartupWait 127.0.0.1 >nul")
foreach ($c in $Commands) {
    $parts += "echo $c"
    $parts += "ping -n $CommandGap 127.0.0.1 >nul"
}
$parts += "echo stop"
$feed = $parts -join " & "

$jarPath = Join-Path $ServerDir "server.jar"
$cmd = "( $feed ) | `"$JavaExe`" -Xmx1200M -jar `"$jarPath`" nogui > `"$log`" 2>&1"

Write-Host "启动: $ServerDir"
$proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $cmd -WorkingDirectory $ServerDir -PassThru -WindowStyle Hidden

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 5
    if ($proc.HasExited) { break }
}
if (-not $proc.HasExited) {
    Write-Host "!! 超时，强制结束 java 进程"
    Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object { $_.StartTime -gt (Get-Date).AddMinutes(-10) } | Stop-Process -Force
    Start-Sleep -Seconds 3
}

$text = if (Test-Path $log) { Get-Content -LiteralPath $log -Raw -Encoding UTF8 } else { "" }
Write-Output "===== 关键字统计 ====="
foreach ($k in @("PlayerInsight 已启用", "已生成", "报告已生成", "挖掘榜", "本月摘要", "Exception", "ERROR", "错误", "Could not load")) {
    $count = ([regex]::Matches($text, [regex]::Escape($k))).Count
    Write-Output ("{0,-22} {1}" -f $k, $count)
}
Write-Output ("日志行数: " + (($text -split "`n").Count))
