@echo off
chcp 65001 >nul
title NetResManager
echo ============================================
echo   NetResManager - 网络资源管理器 v1.0.0
echo ============================================
echo.

cd /d "%~dp0"

if not exist "target\netresmanager-1.0.0.jar" (
    echo [INFO] 首次运行，正在编译打包...
    echo.
    call .mvn\wrapper\maven-home\bin\mvn package -DskipTests -q
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] 编译失败，请检查错误信息
        pause
        exit /b 1
    )
    echo [OK] 编译完成
    echo.
)

echo [INFO] 正在启动应用...
echo.
start javaw -jar target\netresmanager-1.0.0.jar

echo [INFO] 应用已启动！请查看桌面窗口。
echo.
pause
