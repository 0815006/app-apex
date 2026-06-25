@echo off
chcp 65001 >nul 2>&1
echo ==========================================
echo   停止 Apex Server 服务
echo   工作目录: D:\app\apex-server
echo ==========================================
echo.

:: 切到部署目录
cd /d "D:\app\apex-server"

net session >nul 2>&1
if %errorlevel% neq 0 (
    echo ❌ 请以管理员身份运行此脚本！
    pause
    exit /b 1
)

echo 正在停止服务...
ApexServer.exe stop
echo.
echo 正在卸载服务...
ApexServer.exe uninstall
echo.
echo ✅ 服务已停止并卸载
pause
