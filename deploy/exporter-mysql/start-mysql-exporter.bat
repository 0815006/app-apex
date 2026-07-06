@echo off
chcp 65001 >nul 2>&1
title mysqld_exporter

set MYSQL_PORT=9105

cd /d "%~dp0"

echo ============================================
echo   mysqld_exporter (Windows)
echo ============================================
echo.
echo Config: mysql_localhost.cnf
echo Listen: http://localhost:%MYSQL_PORT%/metrics
echo.
echo Starting in background (minimized window)...
echo Use stop-mysql-exporter.bat to stop.
echo ============================================

start "mysqld_exporter" /MIN mysqld_exporter.exe --config.my-cnf="mysql_localhost.cnf" --web.listen-address=":%MYSQL_PORT%"

echo.
echo mysqld_exporter started. Check http://localhost:%MYSQL_PORT%/metrics
echo.

pause
