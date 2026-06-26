@echo off
chcp 65001 >nul 2>&1
title 🔴 Nginx 停止

cd /d "%~dp0"
echo ==========================================
echo   🔴 停止 Nginx
echo   工作目录: %cd%
echo ==========================================
echo.

echo 正在优雅停止 Nginx...
nginx -s quit
if %errorlevel% neq 0 (
    echo.
    echo ⚠️  优雅停止失败，尝试快速停止...
    nginx -s stop
    if %errorlevel% neq 0 (
        echo ❌ Nginx 停止失败！
        pause
        exit /b 1
    )
)

echo.
echo ✅ Nginx 已停止
echo.
pause
exit /b 0
