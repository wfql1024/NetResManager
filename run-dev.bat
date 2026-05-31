@echo off
title NetResManager (Dev Mode)
cd /d "%~dp0"
.mvn\wrapper\maven-home\bin\mvn javafx:run
pause
