@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup batch script, version 3.2.0
@REM ----------------------------------------------------------------------------

@echo off
setlocal enabledelayedexpansion

set "MAVEN_PROJECTBASEDIR=%CD%"
set "MAVEN_HOME=%~dp0\.mvn\wrapper\maven-home"
if not exist "%MAVEN_HOME%" (
    mkdir "%MAVEN_HOME%" 2>nul
)

set "WRAPPER_JAR=%~dp0\.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_PROPERTIES=%~dp0\.mvn\wrapper\maven-wrapper.properties"

if not exist "%WRAPPER_JAR%" (
    echo Maven Wrapper JAR not found. Downloading...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar' -OutFile '%WRAPPER_JAR%'}"
    if not exist "%WRAPPER_JAR%" (
        echo Failed to download Maven Wrapper JAR.
        exit /b 1
    )
)

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo Maven not found in wrapper, downloading...
    for /f "tokens=1,2 delims==" %%a in (%WRAPPER_PROPERTIES%) do (
        if "%%a"=="distributionUrl" set "DIST_URL=%%b"
    )
    set "DIST_URL=!DIST_URL:https://=!"
    set "DIST_ZIP=%MAVEN_HOME%\maven.zip"
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://!DIST_URL!' -OutFile '!DIST_ZIP!'}"
    powershell -Command "& {Expand-Archive -Path '%DIST_ZIP%' -DestinationPath '%MAVEN_HOME%' -Force}"
    del "%DIST_ZIP%" 2>nul

    for /d %%i in ("%MAVEN_HOME%\*") do (
        if exist "%%i\bin\mvn.cmd" (
            move "%%i\*" "%MAVEN_HOME%\" >nul 2>&1
            rmdir "%%i" >nul 2>&1
        )
    )

    if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
        echo Failed to set up Maven.
        exit /b 1
    )
)

set "MAVEN_CMD_LINE_ARGS=%*"
set "M2_HOME=%MAVEN_HOME%"

"%MAVEN_HOME%\bin\mvn.cmd" %MAVEN_CMD_LINE_ARGS%
