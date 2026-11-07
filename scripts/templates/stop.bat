@echo off
setlocal
cd /d "%~dp0"
set "BASE_DIR=%~dp0"

set "JAVA_CMD="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"

if not defined JAVA_CMD (
    for %%d in (
        "%BASE_DIR%jdk-21"
        "%BASE_DIR%..\jdk-21"
        "%BASE_DIR%..\..\jdk-21"
        "%BASE_DIR%..\..\..\jdk-21.0.9"
        "%BASE_DIR%..\..\..\jdk-21"
        "W:\developer\jdk-21.0.9"
        "W:\developer\jdk-21"
        "%ProgramFiles%\Eclipse Adoptium\jdk-21*"
        "%ProgramFiles%\Java\jdk-21*"
    ) do (
        if not defined JAVA_CMD if exist "%%~d\bin\java.exe" set "JAVA_CMD=%%~d\bin\java.exe"
    )
)

if not defined JAVA_CMD (
    where java.exe >nul 2>&1
    if not errorlevel 1 set "JAVA_CMD=java.exe"
)

if not defined JAVA_CMD (
    echo [ERROR] Java runtime not found.
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
timeout /t 2 >nul
