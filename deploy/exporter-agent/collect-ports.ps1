<#
.SYNOPSIS
    Windows 端口监听采集脚本
    通过 Get-NetTCPConnection / Get-NetUDPEndpoint 获取监听端口，
    转为 Prometheus 格式供 windows_exporter Textfile Collector 读取。
#>

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$OutputDir  = Join-Path $ScriptDir "textfile"
$OutputFile = Join-Path $OutputDir "listening_ports.prom"
$TmpFile    = Join-Path $OutputDir "listening_ports.prom.tmp"

# 确保输出目录存在
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
}

# 先构建完整的 Prometheus 指标文本，再一次性写入（避免 Add-Content 编码不一致）
$sb = New-Object System.Text.StringBuilder

[void]$sb.AppendLine("# HELP windows_listening_port Listening port status (1=Listening)")
[void]$sb.AppendLine("# TYPE windows_listening_port gauge")

# --- TCP 监听端口 ---
$tcpListeners = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue
foreach ($conn in $tcpListeners) {
    $port      = $conn.LocalPort
    $localAddr = $conn.LocalAddress
    $procId    = $conn.OwningProcess

    if ($procId) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        $procName = if ($proc) { $proc.ProcessName } else { "unknown" }
    } else {
        $procName = "unknown"
    }

    # Prometheus label 中的特殊字符转义
    $escapedProc = $procName -replace '\\', '\\' -replace '"', '\"'
    $line = "windows_listening_port{port=`"$port`",local_address=`"$localAddr`",protocol=`"tcp`",process=`"$escapedProc`"} 1"
    [void]$sb.AppendLine($line)
}

# --- UDP 端点 ---
$udpListeners = Get-NetUDPEndpoint -ErrorAction SilentlyContinue
foreach ($conn in $udpListeners) {
    $port      = $conn.LocalPort
    $localAddr = $conn.LocalAddress
    $procId    = $conn.OwningProcess

    if ($procId) {
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        $procName = if ($proc) { $proc.ProcessName } else { "unknown" }
    } else {
        $procName = "unknown"
    }

    $escapedProc = $procName -replace '\\', '\\' -replace '"', '\"'
    $line = "windows_listening_port{port=`"$port`",local_address=`"$localAddr`",protocol=`"udp`",process=`"$escapedProc`"} 1"
    [void]$sb.AppendLine($line)
}

# 一次性写入，确保编码一致 (ASCII 兼容所有平台)
$sb.ToString() | Out-File -FilePath $TmpFile -Encoding ascii -NoNewline

# 原子替换
Move-Item -Force $TmpFile $OutputFile
