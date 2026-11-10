@echo off
setlocal
title FormosaBearV Portable Server

rem ----------------------------------------------------
rem Set working directory
rem ----------------------------------------------------
cd /d "%~dp0"
set "BASE_DIR=%~dp0"

rem ----------------------------------------------------
rem 1. Check for Java 21+ Runtime (from JAVA_HOME or PATH)
rem ----------------------------------------------------
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

if not defined JAVA_CMD goto no_java
goto java_ok

:no_java
echo ========================================================
echo   [ERROR] Java 21+ (JDK 21) Runtime Not Found!
echo ========================================================
echo   FormosaBearV requires Java 21 or higher to run.
echo   Please install and configure Java 21:
echo.
echo   1. Download and install OpenJDK 21 or higher:
echo      https://adoptium.net/temurin/releases/?version=21
echo.
echo   2. Set JAVA_HOME environment variable to the installation directory,
echo      or add java.exe directory to system PATH.
echo ========================================================
echo.
pause
exit /b 1

:java_ok

rem ----------------------------------------------------
rem 2. Port Configuration
rem ----------------------------------------------------
set "PORT=8080"
if not "%~1"=="" set "PORT=%~1"
set "STOP_PORT=18080"
set "STOP_KEY=formosabearv"

rem ----------------------------------------------------
rem 3. Storage Directory (Default to portable ./data)
rem ----------------------------------------------------
if not defined DOCS_HOME set "DOCS_HOME=%BASE_DIR%data"
if not exist "%DOCS_HOME%" mkdir "%DOCS_HOME%"

echo ========================================================
echo   FormosaBearV (v2.03) Portable Server
echo ========================================================
echo   Web URL:       http://localhost:%PORT%/
echo   Data Path:     %DOCS_HOME%
echo   Java Runtime:  %JAVA_CMD%
echo   Stop Command:  Run stop.bat or press Ctrl+C
echo ========================================================
echo Starting server, please wait...
echo.

rem Check Jetty home exists
if not exist "%BASE_DIR%jetty-home\start.jar" (
    echo [ERROR] jetty-home\start.jar not found in %BASE_DIR%
    pause
    exit /b 1
)

rem Auto-initialize start.d if not present
if not exist "%BASE_DIR%start.d" if not exist "%BASE_DIR%start.ini" (
    echo [INFO] Initializing Jetty 12 base modules...
    "%JAVA_CMD%" -jar jetty-home\start.jar --create-startd --add-modules=ee10-deploy,http >nul 2>&1
)

rem Launch browser in background after 4 seconds
start "" cmd /c "timeout /t 4 /nobreak >nul & start http://localhost:%PORT%/"

rem Start Jetty Server
"%JAVA_CMD%" -Ddocs.home="%DOCS_HOME%" -Djetty.http.port=%PORT% -DSTOP.PORT=%STOP_PORT% -DSTOP.KEY=%STOP_KEY% -jar jetty-home\start.jar --module=ee10-deploy,http
