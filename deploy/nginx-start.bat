@echo off
title Nginx Start

cd /d "%~dp0"
echo ==========================================
echo   Nginx Start
echo   Working Dir: %cd%
echo ==========================================
echo.

:: Check if already running
tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% equ 0 (
    echo [WARN] Nginx is already running.
    echo        To restart: run nginx-stop.bat first, then nginx-start.bat
    echo.
    pause
    exit /b 0
)

echo [1/2] Checking config ...
"%~dp0nginx.exe" -t
if %errorlevel% neq 0 (
    echo.
    echo [FAIL] Config check failed! Fix and retry.
    pause
    exit /b 1
)

echo.
echo [2/2] Starting Nginx in background ...
start "" /B "%~dp0nginx.exe"
if %errorlevel% neq 0 (
    echo.
    echo [FAIL] Failed to start Nginx! Port may be in use.
    pause
    exit /b 1
)

:: Wait for process to settle
timeout /t 2 /nobreak >nul

:: Verify nginx.exe is running
tasklist /fi "imagename eq nginx.exe" 2>nul | find /i "nginx.exe" >nul
if %errorlevel% neq 0 (
    echo.
    echo [FAIL] Nginx process did not stay up! Check if port is in use.
    "%~dp0nginx.exe" -t
    pause
    exit /b 1
)

echo.
echo [ OK ] Nginx Started
echo        Config files: conf.d\*.conf
echo.
pause
exit /b 0
