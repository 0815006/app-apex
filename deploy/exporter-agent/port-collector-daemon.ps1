# Port Collector 后台进程（无弹窗）
# 在后台持续运行，每60秒采集一次监听端口，输出 Prometheus 指标文件
param(
    [int]$IntervalSeconds = 60
)

$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$Ps1Path    = Join-Path $ScriptDir "collect-ports.ps1"
$TextDir    = Join-Path $ScriptDir "textfile"

# 确保 textfile 目录存在
if (-not (Test-Path $TextDir)) {
    New-Item -ItemType Directory -Force -Path $TextDir | Out-Null
}

# 启动时立即采集一次
Write-Host "[Port Collector] Started (interval: ${IntervalSeconds}s)"
Write-Host "[Port Collector] Script : $Ps1Path"
Write-Host "[Port Collector] Output : $TextDir\listening_ports.prom"
Write-Host ""

while ($true) {
    try {
        & powershell.exe -ExecutionPolicy Bypass -WindowStyle Hidden -File $Ps1Path
        Write-Host "$(Get-Date -Format 'HH:mm:ss') - Collected OK"
    } catch {
        Write-Host "$(Get-Date -Format 'HH:mm:ss') - ERROR: $_"
    }
    Start-Sleep -Seconds $IntervalSeconds
}
