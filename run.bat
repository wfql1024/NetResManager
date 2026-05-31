@echo off
title NetResManager
cd /d "%~dp0"

if not exist "target\netresmanager-1.0.0.jar" (
    echo [BUILD] First run - building project...
    call .mvn\wrapper\maven-home\bin\mvn package -DskipTests -q
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Build failed!
        pause
        exit /b 1
    )
    echo [OK] Build complete.
)

echo [START] Launching NetResManager...
start javaw -jar target\netresmanager-1.0.0.jar
