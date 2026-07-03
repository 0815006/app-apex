@echo off
chcp 65001 >nul 2>&1
title ExporterAgent Installer

set AGENT_DIR=D:\app\exporter-agent

echo ============================================
echo   ExporterAgent Installer
echo ============================================
echo.
echo Target: %AGENT_DIR%
echo.

REM ---- Check admin rights (WinSW requires Administrator) ----
net session >nul 2>&1
if %errorlevel% neq 0 goto :err_no_admin

REM ---- Check prerequisites ----
if not exist "%AGENT_DIR%" goto :err_no_dir
if not exist "%AGENT_DIR%\windows_exporter-0.31.7-amd64.exe" goto :err_no_exporter
if not exist "%AGENT_DIR%\ExporterAgent.exe" goto :err_no_winsw

REM ---- Create textfile directory ----
if exist "%AGENT_DIR%\textfile" goto :skip_textfile
echo [1/3] Creating textfile directory ...
mkdir "%AGENT_DIR%\textfile"
if %errorlevel% neq 0 goto :err_mkdir
goto :after_textfile

:skip_textfile
echo [1/3] textfile directory already exists, skip.

:after_textfile
REM ---- Install and start WinSW service ----
echo [2/3] Installing ExporterAgent service ...
"%AGENT_DIR%\ExporterAgent.exe" install
if %errorlevel% neq 0 echo [WARN] Service may already exist, trying to start ...

echo [3/3] Starting service ...
"%AGENT_DIR%\ExporterAgent.exe" start

echo.
echo ============================================
echo   Installation complete
echo ============================================
echo Exporter metrics : http://localhost:9182/metrics
echo Config file      : %AGENT_DIR%\config.yaml
echo Textfile dir     : %AGENT_DIR%\textfile
echo Service name     : ExporterAgent (check services.msc)
echo.
echo To verify: curl http://localhost:9182/metrics ^| findstr windows_listening_port
echo.
echo Next step: run install-port-collector.bat to enable port listening collection.
goto :end

:err_no_dir
echo [ERROR] Directory not found: %AGENT_DIR%
echo.
echo Please copy all files from deploy\exporter-agent\ to %AGENT_DIR%\
echo Then run this script again.
goto :end

:err_no_exporter
echo [ERROR] File not found: %AGENT_DIR%\windows_exporter-0.31.7-amd64.exe
goto :end

:err_no_winsw
echo [ERROR] File not found: %AGENT_DIR%\ExporterAgent.exe (WinSW wrapper)
goto :end

:err_no_admin
echo [ERROR] WinSW requires Administrator privileges.
echo        Please right-click and select "Run as administrator".
goto :end

:err_mkdir
echo [ERROR] Failed to create textfile directory.
goto :end

:end
echo.
pause
