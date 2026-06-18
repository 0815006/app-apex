:: 本地 Docker Compose 全家桶一键启动（开发验证环境）
:: 包含：app(Java 21) + nginx(Vue3) + mysql(8.4)
@echo off
chcp 65001 > nul

echo ===================================================
echo  本地 Docker Compose 开发环境一键启动
echo  服务组成：app + nginx + mysql 8.4
echo ===================================================
echo.

cd /d "%~dp0"

echo [1/2] 正在构建并启动所有容器（含镜像重建）...
echo.

docker compose up --build -d

if %errorlevel% neq 0 (
    echo.
    echo [错误] Docker Compose 启动失败！请检查 Docker Desktop 是否运行。
    pause
    exit /b
)

echo.
echo ===================================================
echo  全部容器启动成功！
echo ---------------------------------------------------
echo  前端访问：  http://localhost
echo  后端 API：  http://localhost:8093/api/health
echo  MySQL：     localhost:3306  (root / root123)
echo ===================================================
echo.
echo 提示：查看实时日志请执行 docker compose logs -f
echo       停止所有服务请执行 docker compose down
echo.
pause
