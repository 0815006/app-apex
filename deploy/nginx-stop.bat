@echo off
title Nginx Stop

cd /d "%~dp0"
echo ==========================================
echo   Nginx Stop
echo   Working Dir: %cd%
echo ==========================================
echo.

:: Check if running
tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% neq 0 (
    echo [WARN] Nginx is not running. Nothing to stop.
    echo.
    pause
    exit /b 0
)

echo Sending quit signal ...
"%~dp0nginx.exe" -s quit 2>nul
timeout /t 2 /nobreak >nul

tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% neq 0 goto :stopped

echo.
echo [WARN] Graceful quit failed, trying fast stop ...
"%~dp0nginx.exe" -s stop 2>nul
timeout /t 2 /nobreak >nul

tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% neq 0 goto :stopped

echo.
echo [WARN] Signal methods failed (PID file issue), force killing ...
taskkill /f /im nginx.exe >nul 2>&1
timeout /t 1 /nobreak >nul

tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% neq 0 goto :stopped

echo.
echo [FAIL] Cannot kill nginx.exe! Terminate it manually.
pause
exit /b 1

:stopped
echo.
echo [ OK ] Nginx Stopped
echo.
pause
exit /b 0
