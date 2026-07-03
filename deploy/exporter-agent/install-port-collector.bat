@echo off
title Port Collector Installer

set AGENT_DIR=D:\app\exporter-agent

echo ============================================
echo   Port Collector Installer
echo ============================================
echo.

net session >nul 2>&1
if %errorlevel% neq 0 goto :err_no_admin

REM 清理残留的旧计划任务
schtasks /delete /tn "Port Collector" /f 2>nul

REM 启动后台采集进程（无窗口，每分钟自动采集）
echo Starting Port Collector daemon (hidden, every 60s) ...
start "" /MIN powershell.exe -WindowStyle Hidden -ExecutionPolicy Bypass -File "%AGENT_DIR%\port-collector-daemon.ps1"

echo.
echo ============================================
echo   Port collector started
echo ============================================
echo Script : %AGENT_DIR%\collect-ports.ps1
echo Output : %AGENT_DIR%\textfile\listening_ports.prom
echo.
echo Runs silently in background, no popup.
echo Stops when you log out or reboot.
echo.
echo To verify: type %AGENT_DIR%\textfile\listening_ports.prom
goto :end

:err_no_admin
echo [ERROR] Administrator privileges required.
echo        Right-click - "Run as administrator".

:end
echo.
pause
