@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

:: 设置压缩包名称
set ZIP_NAME=apex-source.zip

echo ==================================================
echo   正在打包源码项目...
echo   排除目录: java-apex-server/target, web-apex-vue/node_modules, web-apex-vue/dist
echo ==================================================

:: 如果旧的压缩包存在，先删除
if exist %ZIP_NAME% (
    echo 正在删除旧的压缩包...
    del %ZIP_NAME%
)

:: 使用 Windows 自带的 tar 命令进行打包
:: -a: 根据后缀名自动选择压缩算法 (zip)
:: -c: 创建新归档
:: -v: 显示过程
:: -f: 指定文件名
tar -acvf %ZIP_NAME% ^
    --exclude="*/target" ^
    --exclude="*/node_modules" ^
    --exclude="*/dist" ^
    java-* web-*

if %ERRORLEVEL% equ 0 (
    echo.
    echo ==================================================
    echo   打包成功: %ZIP_NAME%
    echo ==================================================
) else (
    echo.
    echo ##################################################
    echo   打包失败，请检查是否安装了 tar 命令或文件被占用
    echo ##################################################
)

pause