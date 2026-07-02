@echo off
echo === Apex Port Collector Uninstaller ===
echo.

set AGENT_DIR=D:\app\exporter-agent

echo [1/2] Deleting scheduled task "Apex Port Collector" ...
schtasks /delete /tn "Apex Port Collector" /f

if %errorlevel% neq 0 (
    echo [WARN] Task may not exist or already removed.
)

echo [2/2] Cleaning up .prom output ...
if exist "%AGENT_DIR%\textfile\listening_ports.prom" (
    del /q "%AGENT_DIR%\textfile\listening_ports.prom"
)
if exist "%AGENT_DIR%\textfile\listening_ports.prom.tmp" (
    del /q "%AGENT_DIR%\textfile\listening_ports.prom.tmp"
)

echo.
echo === Port collector uninstalled ===
