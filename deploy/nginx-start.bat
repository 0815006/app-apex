@echo off
chcp 65001 >nul 2>&1
title 🟢 Nginx 启动

cd /d "%~dp0"
echo ==========================================
echo   🟢 启动 Nginx
echo   工作目录: %cd%
echo ==========================================
echo.

nginx
if %errorlevel% neq 0 (
    echo.
    echo ❌ Nginx 启动失败！可能是端口被占用或配置有误。
    echo   请先执行: nginx -t 检查配置
    pause
    exit /b 1
)

echo.
echo ✅ Nginx 已启动 (端口 8083)
echo   访问: http://localhost:8083
echo.
nginx -t
echo.
pause
exit /b 0
