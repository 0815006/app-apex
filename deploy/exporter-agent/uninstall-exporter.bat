@echo off
echo === ExporterAgent Uninstaller ===
echo.

set AGENT_DIR=D:\app\exporter-agent

if not exist "%AGENT_DIR%\ExporterAgent.exe" (
    echo [ERROR] ExporterAgent.exe (WinSW) not found. Is the exporter installed?
    exit /b 1
)

echo [1/2] Stopping ExporterAgent service ...
"%AGENT_DIR%\ExporterAgent.exe" stop

echo [2/2] Uninstalling ExporterAgent service ...
"%AGENT_DIR%\ExporterAgent.exe" uninstall

echo.
echo === Uninstallation complete ===
echo Note: config.yaml / textfile directory / exe files preserved.
echo       Delete %AGENT_DIR% manually if you want a full cleanup.
