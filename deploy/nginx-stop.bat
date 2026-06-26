@echo off
chcp 65001 >nul 2>&1
title 🔴 Nginx 停止

cd /d "%~dp0"
echo ==========================================
echo   🔴 停止 Nginx
echo   工作目录: %cd%
echo ==========================================
echo.

:: 先检查 nginx 进程是否在运行
tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% neq 0 (
    echo ⚠️  Nginx 当前未在运行，无需停止。
    echo.
    pause
    exit /b 0
)

echo 正在优雅停止 Nginx...
nginx -s quit 2>nul
timeout /t 2 /nobreak >nul

:: 检查优雅停止是否成功
tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% neq 0 goto :stopped

echo.
echo ⚠️  优雅停止未生效，尝试快速停止...
nginx -s stop 2>nul
timeout /t 2 /nobreak >nul

tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% neq 0 goto :stopped

echo.
echo ⚠️  信号模式均失败（PID 文件异常），强制终止进程...
taskkill /f /im nginx.exe >nul 2>&1
timeout /t 1 /nobreak >nul

tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% neq 0 goto :stopped

echo.
echo ❌ Nginx 进程无法终止！请手动结束 nginx.exe。
pause
exit /b 1

:stopped
echo.
echo ✅ Nginx 已停止
echo.
pause
exit /b 0
