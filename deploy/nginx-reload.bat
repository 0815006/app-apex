@echo off
title Nginx Reload

cd /d "%~dp0"
echo ==========================================
echo   Nginx Config Reload
echo   Working Dir: %cd%
echo ==========================================
echo.

:: Check if running
tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% neq 0 (
    echo [FAIL] Nginx is not running. Run nginx-start.bat first.
    echo.
    pause
    exit /b 1
)

echo Listening ports:
setlocal enabledelayedexpansion
for /f "tokens=2" %%p in ('tasklist /fi "imagename eq nginx.exe" /fo table /nh 2^>nul') do (
    set "pid=%%p"
    for /f "tokens=2" %%a in ('netstat -ano ^| findstr "LISTENING" ^| findstr /r /c:"[^^0-9]!pid!$"') do echo     %%a
)
endlocal
echo.

echo [1/2] Checking config ...
"%~dp0nginx.exe" -t
if %errorlevel% neq 0 (
    echo.
    echo [FAIL] Config check failed! Fix and retry.
    pause
    exit /b 1
)

echo.
echo [2/2] Sending reload signal ...
"%~dp0nginx.exe" -s reload 2>nul
if %errorlevel% neq 0 (
    echo.
    echo [WARN] Reload failed (PID file may be corrupted)!
    echo        Try restart instead:
    echo          1. Run nginx-stop.bat
    echo          2. Run nginx-start.bat
    echo.
    pause
    exit /b 1
)

echo.
echo [ OK ] Nginx Config Reloaded
echo.
pause
exit /b 0
