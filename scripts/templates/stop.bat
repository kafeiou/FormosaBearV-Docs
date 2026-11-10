@echo off
setlocal
cd /d "%~dp0"
set "BASE_DIR=%~dp0"

set "JAVA_CMD="

rem Check JAVA_HOME first
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /r "version..2[1-9] version..[3-9][0-9] version..[1-9][0-9][0-9]" >nul 2>&1
    if not errorlevel 1 set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
)

rem Fall back to system PATH
if not defined JAVA_CMD (
    where java.exe >nul 2>&1
    if not errorlevel 1 (
        java.exe -version 2>&1 | findstr /r "version..2[1-9] version..[3-9][0-9] version..[1-9][0-9][0-9]" >nul 2>&1
        if not errorlevel 1 set "JAVA_CMD=java.exe"
    )
)

if not defined JAVA_CMD (
    echo [ERROR] Java 21+ runtime not found. Please set JAVA_HOME to JDK 21+ or add java to PATH.
    pause
    exit /b 1
)

set "STOP_PORT=18080"
if not "%~1"=="" set "STOP_PORT=%~1"
set "STOP_KEY=formosabearv"

echo Stopping FormosaBearV Server on port %STOP_PORT%...
"%JAVA_CMD%" -DSTOP.PORT=%STOP_PORT% -DSTOP.KEY=%STOP_KEY% -jar jetty-home\start.jar --stop
if %errorlevel% equ 0 (
    echo [OK] Server stopped successfully.
) else (
    echo [INFO] Stop signal sent. If the server is still running, close its terminal window.
)
ping 127.0.0.1 -n 3 >nul 2>&1
