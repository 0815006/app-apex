@echo off
chcp 65001 >nul 2>&1
title 🔄 Nginx 热重载

cd /d "%~dp0"
echo ==========================================
echo   🔄 Nginx 配置热重载
echo   工作目录: %cd%
echo ==========================================
echo.

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
nginx -s reload
if %errorlevel% neq 0 (
    echo.
    echo ❌ 重载失败！请检查 Nginx 是否已启动。
    pause
    exit /b 1
)

echo.
echo ✅ Nginx 配置已热重载，无需重启服务
echo.
pause
exit /b 0
