@echo off
setlocal enabledelayedexpansion
echo === ExporterAgent Installer ===
echo.

set AGENT_DIR=D:\app\exporter-agent

REM ---- Check prerequisites ----
if not exist "%AGENT_DIR%\windows_exporter-0.31.7-amd64.exe" (
    echo [ERROR] windows_exporter-0.31.7-amd64.exe not found in %AGENT_DIR%
    exit /b 1
)
if not exist "%AGENT_DIR%\ExporterAgent.exe" (
    echo [ERROR] ExporterAgent.exe (WinSW) not found in %AGENT_DIR%
    exit /b 1
)

REM ---- Create textfile directory ----
if not exist "%AGENT_DIR%\textfile" (
    echo [1/3] Creating textfile directory ...
    mkdir "%AGENT_DIR%\textfile"
    echo       Done.
) else (
    echo [1/3] textfile directory already exists, skip.
)

REM ---- Install and start WinSW service ----
echo [2/3] Installing ExporterAgent service ...
"%AGENT_DIR%\ExporterAgent.exe" install
if !errorlevel! neq 0 (
    echo [WARN] Service may already exist, trying to start ...
)
echo [3/3] Starting service ...
"%AGENT_DIR%\ExporterAgent.exe" start

echo.
echo === Installation complete ===
echo Exporter metrics : http://localhost:9182/metrics
echo Config file      : %AGENT_DIR%\config.yaml
echo Textfile dir     : %AGENT_DIR%\textfile
echo Service name     : ExporterAgent (check in services.msc)
echo.
echo To verify: curl http://localhost:9182/metrics ^| findstr windows_listening_port
echo.
echo Next step: run install-port-collector.bat to enable port listening collection.
