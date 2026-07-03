@echo off
title Port Collector Uninstaller

set AGENT_DIR=D:\app\exporter-agent

echo ============================================
echo   Port Collector Uninstaller
echo ============================================
echo.

REM 停止后台 daemon 进程
echo [1/3] Stopping daemon process ...
powershell.exe -Command "Get-WmiObject Win32_Process -Filter \"CommandLine like '%%port-collector-daemon.ps1%%'\" | ForEach-Object { $_.Terminate() }" 2>nul
taskkill /f /fi "WINDOWTITLE eq Port Collector*" 2>nul

REM 删除旧计划任务（如果存在）
echo [2/3] Removing scheduled task ...
schtasks /delete /tn "Port Collector" /f 2>nul

REM 清理 .prom 输出
echo [3/3] Cleaning up ...
if exist "%AGENT_DIR%\textfile\listening_ports.prom" del /q "%AGENT_DIR%\textfile\listening_ports.prom"
if exist "%AGENT_DIR%\textfile\listening_ports.prom.tmp" del /q "%AGENT_DIR%\textfile\listening_ports.prom.tmp"

echo.
echo ============================================
echo   Port collector uninstalled
echo ============================================

:end
echo.
pause
