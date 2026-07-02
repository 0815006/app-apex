@echo off
setlocal
echo === Apex Port Collector Installer ===
echo.

set AGENT_DIR=D:\app\exporter-agent
set PS1_PATH=%AGENT_DIR%\collect-ports.ps1

if not exist "%PS1_PATH%" (
    echo [ERROR] collect-ports.ps1 not found in %AGENT_DIR%
    exit /b 1
)

REM ---- Register Windows Scheduled Task ----
REM Schedule: triggers every 1 minute, repeats every 10 seconds for 1 minute
echo [1/2] Registering scheduled task "Apex Port Collector" ...
schtasks /create ^
    /sc minute /mo 1 ^
    /ri 10 /du "00:01" ^
    /tn "Apex Port Collector" ^
    /tr "powershell.exe -ExecutionPolicy Bypass -WindowStyle Hidden -File \"%PS1_PATH%\"" ^
    /f

if %errorlevel% neq 0 (
    echo [ERROR] Failed to create scheduled task. Run as Administrator?
    exit /b 1
)

echo [2/2] Running first collection ...
powershell.exe -ExecutionPolicy Bypass -WindowStyle Hidden -File "%PS1_PATH%"

echo.
echo === Port collector installed ===
echo Task   : Apex Port Collector (every 10s)
echo Script : %PS1_PATH%
echo Output : %AGENT_DIR%\textfile\listening_ports.prom
echo.
echo To verify: schtasks /query /tn "Apex Port Collector"
echo            type %AGENT_DIR%\textfile\listening_ports.prom
