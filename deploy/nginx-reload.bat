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

:: Get all nginx PID lines from netstat in one shot
netstat -ano | find "LISTENING" > "%TEMP%\_nx_ns.tmp"

:: Filter by each nginx PID using plain find (no regex, no delayed expansion)
if exist "%TEMP%\_nx_r.tmp" del "%TEMP%\_nx_r.tmp"
for /f "tokens=2" %%p in ('tasklist /fi "imagename eq nginx.exe" /fo table /nh') do (
    find " %%~p" "%TEMP%\_nx_ns.tmp" >> "%TEMP%\_nx_r.tmp" 2>nul
)

:: Print ports
if exist "%TEMP%\_nx_r.tmp" for /f "tokens=2" %%a in (%TEMP%\_nx_r.tmp) do echo     %%a
del "%TEMP%\_nx_ns.tmp" "%TEMP%\_nx_r.tmp" 2>nul
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
echo [ OK ] Config syntax is valid.
echo.
echo [INFO] Nginx on Windows does not support hot-reload reliably.
echo        After changing config, restart to apply:
echo          1. Run nginx-stop.bat
echo          2. Run nginx-start.bat
echo.
pause
exit /b 0
