@echo off
chcp 65001 >nul 2>&1

set MYSQL_PORT=9105

echo ============================================
echo   Stop mysqld_exporter
echo ============================================
echo.

REM 先从端口查找 PID 精确干掉
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":%MYSQL_PORT%" ^| findstr "LISTENING"') do (
    echo Killing PID %%a on port %MYSQL_PORT% ...
    taskkill /F /PID %%a >nul 2>&1
)

echo.
echo mysqld_exporter on port %MYSQL_PORT% stopped.
echo.

pause
