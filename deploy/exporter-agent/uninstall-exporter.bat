@echo off
chcp 65001 >nul 2>&1
title ExporterAgent Uninstaller

set AGENT_DIR=D:\app\exporter-agent

echo ============================================
echo   ExporterAgent Uninstaller
echo ============================================
echo.

REM ---- Check admin rights (WinSW requires Administrator) ----
net session >nul 2>&1
if %errorlevel% neq 0 goto :err_no_admin

if not exist "%AGENT_DIR%\ExporterAgent.exe" goto :err_no_exe

echo [1/2] Stopping ExporterAgent service ...
"%AGENT_DIR%\ExporterAgent.exe" stop

echo [2/2] Uninstalling ExporterAgent service ...
"%AGENT_DIR%\ExporterAgent.exe" uninstall

echo.
echo ============================================
echo   Uninstallation complete
echo ============================================
echo Note: config.yaml / textfile directory / exe files preserved.
echo       Delete %AGENT_DIR% manually if you want a full cleanup.
goto :end

:err_no_admin
echo [ERROR] WinSW requires Administrator privileges.
echo        Please right-click and select "Run as administrator".
goto :end

:err_no_exe
echo [ERROR] ExporterAgent.exe (WinSW) not found in %AGENT_DIR%
echo        Is the exporter installed? Nothing to uninstall.

:end
echo.
pause
