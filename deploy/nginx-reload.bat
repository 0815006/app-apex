@echo off
chcp 65001 >nul 2>&1
title 🔄 Nginx 热重载

cd /d "%~dp0"
echo ==========================================
echo   🔄 Nginx 配置热重载
echo   工作目录: %cd%
echo ==========================================
echo.

:: 检查 Nginx 是否在运行
tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% neq 0 (
    echo ❌ Nginx 未在运行，请先执行 nginx-start.bat。
    echo.
    pause
    exit /b 1
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
echo [2/2] 发送重载信号...
nginx -s reload 2>nul
if %errorlevel% neq 0 (
    echo.
    echo ⚠️  热重载失败（PID 文件可能异常）！
    echo   建议改用重启方式：
    echo     1. 执行 nginx-stop.bat 停止
    echo     2. 执行 nginx-start.bat 启动
    echo.
    pause
    exit /b 1
)

echo.
echo ✅ Nginx 配置已热重载，无需重启服务
echo.
pause
exit /b 0
