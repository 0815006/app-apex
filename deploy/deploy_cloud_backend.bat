@echo off
chcp 65001 > nul
set SERVER_IP=129.211.9.238
set SERVER_DIR=/var/www/app-apex
:: 后端在云端宿主机也用 8093 承接
set PORT=8093
set CONTAINER_NAME=app-apex
:: 【核心修改 1】对应你本地 target 目录下生成的真实 jar 包名字
set JAR_NAME=java-apex-server-1.0.0-SNAPSHOT.jar

echo ===================================================
echo [1/4] 进入后端源码目录，开始 Maven 编译打包...
echo ===================================================
cd /d "%~dp0..\java-apex-server"
call mvn clean package -Dmaven.test.skip=true
set BUILD_RESULT=%errorlevel%
cd /d "%~dp0."

if %BUILD_RESULT% neq 0 (
    echo [错误] Maven 打包失败，终止部署！
    pause
    exit /b
)

echo ===================================================
echo [2/4] 正在将编译产物与 Dockerfile 上传至腾讯云...
echo ===================================================
ssh root@%SERVER_IP% "mkdir -p %SERVER_DIR%"
:: 【核心修改 2】本地传过去的是长名字，但传到服务器后，scp 顺手改名为干净的 app.jar
scp "%~dp0..\java-apex-server\target\%JAR_NAME%" root@%SERVER_IP%:%SERVER_DIR%/app.jar
scp "%~dp0cloud-dockerfile" root@%SERVER_IP%:%SERVER_DIR%/Dockerfile

echo ===================================================
echo [3/4] 远程连接服务器，通过环境变量注入参数并重启容器...
echo ===================================================
:: -p 8093:8093：把云端规划的外部 8093 端口映射到代码里的 8093
ssh root@%SERVER_IP% "cd %SERVER_DIR% && docker build -t %CONTAINER_NAME% . && docker stop %CONTAINER_NAME% 2>/dev/null || true && docker rm %CONTAINER_NAME% 2>/dev/null || true && docker run -d --name %CONTAINER_NAME% -p %PORT%:8093 --restart always --add-host=host.docker.internal:host-gateway -v /data/apex/file-share:/data/apex/file-share -e DB_HOST=host.docker.internal -e DB_PORT=3306 -e DB_USER=root -e DB_PASSWORD=root -e FILE_STORAGE_PATH=/data/apex/file-share %CONTAINER_NAME%"

echo ===================================================
echo 恭喜！项目 %CONTAINER_NAME% 后端部署成功！
echo 腾讯云外部 API 端口: %PORT%
echo ===================================================
pause