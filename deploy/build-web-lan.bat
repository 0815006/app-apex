@echo off
chcp 65001 >nul 2>&1
title 🔨 构建 Apex Web → 内网 Windows 部署
setlocal

echo ==================================================
echo   🔨 构建 Apex 前端 Web 内网版本
echo ==================================================
echo.

:: 切到项目根目录
set "PROJECT_ROOT=%~dp0.."
cd /d "%PROJECT_ROOT%"
if %errorlevel% neq 0 (
    echo ❌ 无法进入项目根目录！
    pause
    exit /b 1
)
echo 📁 项目根目录: %cd%
echo.

:: ========== Step 1: 校验项目文件 ==========
echo [1/4] 📋 校验项目文件...
if not exist "web-apex-vue\package.json" (
    echo ❌ 未找到 web-apex-vue\package.json！
    pause
    exit /b 1
)
echo ✅ 项目文件就绪
echo.

:: ========== Step 2: npm 编译打包 ==========
echo [2/4] ⚡ Vite 生产环境打包...
echo   执行: npm run build
echo.

cd /d "%PROJECT_ROOT%\web-apex-vue"

:: 检查 node_modules
if not exist "node_modules" (
    echo ⚠️  未找到 node_modules，正在执行 npm install...
    call npm install
    if %errorlevel% neq 0 (
        echo ❌ npm install 失败！
        cd /d "%PROJECT_ROOT%"
        pause
        exit /b 1
    )
    echo ✅ npm install 完成
    echo.
)

call npm run build
set BUILD_RESULT=%errorlevel%

if %BUILD_RESULT% neq 0 (
    echo.
    echo ❌ 前端打包失败！请检查 Node.js 环境和依赖。
    cd /d "%PROJECT_ROOT%"
    pause
    exit /b 1
)
echo.
echo ✅ Vite 打包完成
echo.

:: ========== Step 3: 清空并复制到 bin\apex-web ==========
echo [3/4] 📦 复制 dist 产物到 bin\apex-web\ ...

set "DIST_SRC=%PROJECT_ROOT%\web-apex-vue\dist"
set "WEB_OUT=%PROJECT_ROOT%\bin\apex-web"

if not exist "%DIST_SRC%" (
    echo ❌ 未找到打包产物 %DIST_SRC%
    cd /d "%PROJECT_ROOT%"
    pause
    exit /b 1
)

:: 清理并创建输出目录
if exist "%WEB_OUT%" rmdir /s /q "%WEB_OUT%"
mkdir "%WEB_OUT%" >nul 2>&1

:: 复制 dist 目录下所有文件到 apex-web
xcopy /e /y "%DIST_SRC%\*" "%WEB_OUT%\" >nul
echo   %DIST_SRC%\*
echo   → %WEB_OUT%\
echo.

:: ========== Step 4: 输出完成信息 ==========
echo ==================================================
echo   🏁  构建完成！内网前端 Web 版本
echo ==================================================
echo.
echo   📌 构建产物目录: %WEB_OUT%\
dir /b "%WEB_OUT%"
echo.
echo   📌 Nginx 配置参考:
echo      1. 将 bin\apex-web\ 下所有文件复制到 D:\app\apex-web\
echo      2. Windows Nginx 配置: deploy\nginx-apex-lan.conf
echo.
echo   💡 注: vite.config.ts 已配置 host='0.0.0.0'，支持内网访问
echo   💡 重新构建: 直接运行本 bat 即可
echo ==================================================
echo.

cd /d "%PROJECT_ROOT%"
pause
exit /b 0
