@echo off
chcp 65001 >nul
title NetResManager (开发模式)
echo ============================================
echo   NetResManager - 开发模式
echo   带控制台输出，方便调试
echo ============================================
echo.

cd /d "%~dp0"
.mvn\wrapper\maven-home\bin\mvn javafx:run

pause
