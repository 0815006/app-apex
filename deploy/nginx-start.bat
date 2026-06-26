@echo off
chcp 65001 >nul 2>&1
title 🟢 Nginx 启动

cd /d "%~dp0"
echo ==========================================
echo   🟢 启动 Nginx
echo   工作目录: %cd%
echo ==========================================
echo.

:: 防重复启动：先检查 nginx 是否已在运行
tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% equ 0 (
    echo ⚠️  Nginx 已在运行中，无需重复启动。
    echo   如需重启请先执行 nginx-stop.bat 再运行本脚本。
    echo.
    pause
    exit /b 0
)

echo [1/2] 校验配置文件...
nginx -t
if %errorlevel% neq 0 (
    echo.
    echo ❌ 配置校验失败！请修正后重试。
    pause
    exit /b 1
)

echo.
echo [2/2] 以后台进程启动 Nginx...
start "" /B nginx
if %errorlevel% neq 0 (
    echo.
    echo ❌ Nginx 启动失败！可能是端口被占用。
    pause
    exit /b 1
)

:: 等待进程就绪
timeout /t 2 /nobreak >nul

:: 二次确认进程是否在运行
tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% neq 0 (
    echo.
    echo ❌ Nginx 进程未成功驻留！请检查端口是否被占用。
    nginx -t
    pause
    exit /b 1
)

echo.
echo ✅ Nginx 已启动
echo   配置文件位于 conf.d/ 目录，各 server 端口请查看对应 .conf
echo.
pause
exit /b 0
