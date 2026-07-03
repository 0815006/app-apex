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

# 写入 HELP / TYPE 头部 (纯英文 + ASCII 编码，兼容所有 PowerShell 版本)
@"
# HELP windows_listening_port Listening port status (1=Listening)
# TYPE windows_listening_port gauge
"@ | Out-File -FilePath $TmpFile -Encoding ascii

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
    $line = "windows_listening_port{port=`"$port`",local_address=`"$localAddr`",protocol=`"tcp`",process=`"$escapedProc`",pid=`"$procId`"} 1"
    Add-Content -Path $TmpFile -Value $line
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
    $line = "windows_listening_port{port=`"$port`",local_address=`"$localAddr`",protocol=`"udp`",process=`"$escapedProc`",pid=`"$procId`"} 1"
    Add-Content -Path $TmpFile -Value $line
}

# 原子替换
Move-Item -Force $TmpFile $OutputFile
