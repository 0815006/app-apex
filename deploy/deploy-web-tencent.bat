:: 部署前端到腾讯云服务器
@echo off
chcp 65001 > nul
set SERVER_IP=129.211.9.238
set PORT=8083
set SERVER_DIR=/var/www/app-apex/dist

echo ===================================================
echo [1/3] 进入前端源码目录，开始生产环境打包...
echo ===================================================
cd /d "%~dp0..\web-apex-vue"
call npm run build
set BUILD_RESULT=%errorlevel%
cd /d "%~dp0."

if %BUILD_RESULT% neq 0 (
    echo [错误] 前端打包失败，终止部署！
    pause
    exit /b
)

echo ===================================================
echo [2/3] 正在清空云端旧静态资源并上传新 dist 目录...
echo ===================================================
ssh root@%SERVER_IP% "mkdir -p %SERVER_DIR% && rm -rf %SERVER_DIR%/*"
scp -r "%~dp0..\web-apex-vue\dist\*" root@%SERVER_IP%:%SERVER_DIR%/

echo ===================================================
echo [3/3] 正在上传 Nginx 配置并重载...
echo ===================================================
scp "%~dp0nginx-apex.conf" root@%SERVER_IP%:/etc/nginx/conf.d/app-apex.conf
ssh root@%SERVER_IP% "nginx -t && nginx -s reload"

echo ===================================================
echo 恭喜！项目前端静态页面部署成功！
echo 腾讯云前端访问端口: %PORT% (通过 Nginx 托管在该端口)
echo ===================================================
pause