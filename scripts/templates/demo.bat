@echo off
setlocal EnableDelayedExpansion
title FormosaBearV Portable Demo Server (Auto-Reset)

rem ----------------------------------------------------
rem Set working directory
rem ----------------------------------------------------
cd /d "%~dp0"
set "BASE_DIR=%~dp0"

rem ----------------------------------------------------
rem 1. Check for Java 21+ Runtime (from JAVA_HOME or PATH)
rem ----------------------------------------------------
set "JAVA_CMD="

if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
    "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /r "version..2[1-9] version..[3-9][0-9] version..[1-9][0-9][0-9]" >nul 2>&1
    if not errorlevel 1 set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
)

if not defined JAVA_CMD (
    where java.exe >nul 2>&1
    if not errorlevel 1 (
        java.exe -version 2>&1 | findstr /r "version..2[1-9] version..[3-9][0-9] version..[1-9][0-9][0-9]" >nul 2>&1
        if not errorlevel 1 set "JAVA_CMD=java.exe"
    )
)

if not defined JAVA_CMD (
    echo ========================================================
    echo   [ERROR] Java 21+ ^(JDK 21^) Runtime Not Found!
    echo ========================================================
    echo   FormosaBearV requires Java 21 or higher to run.
    pause
    exit /b 1
)

rem ----------------------------------------------------
rem 2. Port & Reset Interval Configuration
rem ----------------------------------------------------
set "PORT=8080"
if not "%~1"=="" set "PORT=%~1"
set "RESET_HOURS=24"
if not "%~2"=="" set "RESET_HOURS=%~2"
if not defined RESET_SECONDS set /a "RESET_SECONDS=%RESET_HOURS% * 3600"
set "STOP_PORT=18080"
set "STOP_KEY=formosabearv"

rem ----------------------------------------------------
rem 3. Storage & Backup Directory
rem ----------------------------------------------------
if not defined DOCS_HOME set "DOCS_HOME=%BASE_DIR%data"
set "BACKUP_DIR=%BASE_DIR%data.bak"

if not exist "%BASE_DIR%jetty-home\start.jar" (
    echo [ERROR] jetty-home\start.jar not found in %BASE_DIR%
    pause
    exit /b 1
)

if not exist "%BASE_DIR%start.d" if not exist "%BASE_DIR%start.ini" (
    echo [INFO] Initializing Jetty 12 base modules...
    "%JAVA_CMD%" -jar jetty-home\start.jar --create-startd --add-modules=ee10-deploy,http >nul 2>&1
)

echo ========================================================
echo   FormosaBearV Portable Demo Server ^(Auto-Reset^)
echo ========================================================
echo   Web URL:        http://localhost:%PORT%/
echo   Data Path:      %DOCS_HOME%
echo   Baseline Path:  %BACKUP_DIR%
echo   Reset Interval: %RESET_HOURS% hour^(s^) ^(%RESET_SECONDS%s^)
echo   Java Runtime:   %JAVA_CMD%
echo   Stop Command:   Run stop.bat and close this window
echo ========================================================
echo.

if not exist "%BACKUP_DIR%" (
    echo [WARNING] Baseline directory "data.bak" was not found!
    echo           The server will start normally using "data", but automatic reset/restart
    echo           will be skipped until you manually copy "data" to "data.bak".
    echo.
    if not exist "%DOCS_HOME%" mkdir "%DOCS_HOME%"
    echo [%date% %time%] Starting FormosaBearV Demo Server on port %PORT%...
    "%JAVA_CMD%" -Ddocs.home="%DOCS_HOME%" -Djetty.http.port=%PORT% -DSTOP.PORT=%STOP_PORT% -DSTOP.KEY=%STOP_KEY% -jar jetty-home\start.jar --module=ee10-deploy,http
    exit /b %errorlevel%
)

:demo_loop
echo [%date% %time%] Stopping any running Jetty instance on stop port %STOP_PORT%...
"%JAVA_CMD%" -DSTOP.PORT=%STOP_PORT% -DSTOP.KEY=%STOP_KEY% -jar jetty-home\start.jar --stop >nul 2>&1
ping 127.0.0.1 -n 4 >nul 2>&1

if not exist "%BACKUP_DIR%" (
    echo [WARNING] Baseline directory "data.bak" was not found!
    echo           Please manually copy "data" to "data.bak" to enable automatic reset/restart.
    echo           Starting server normally without resetting "data"...
    if not exist "%DOCS_HOME%" mkdir "%DOCS_HOME%"
    "%JAVA_CMD%" -Ddocs.home="%DOCS_HOME%" -Djetty.http.port=%PORT% -DSTOP.PORT=%STOP_PORT% -DSTOP.KEY=%STOP_KEY% -jar jetty-home\start.jar --module=ee10-deploy,http
    exit /b %errorlevel%
)

echo [%date% %time%] Resetting "%DOCS_HOME%" from "%BACKUP_DIR%"...
if exist "%DOCS_HOME%" (
    rmdir /s /q "%DOCS_HOME%"
    if exist "%DOCS_HOME%" (
        echo [WARN] Waiting for file locks to release...
        ping 127.0.0.1 -n 6 >nul 2>&1
        rmdir /s /q "%DOCS_HOME%"
    )
    if exist "%DOCS_HOME%" (
        echo [ERROR] Failed to remove "%DOCS_HOME%". Please check if another process is locking files.
        pause
        exit /b 1
    )
)

xcopy /e /i /h /y /q "%BACKUP_DIR%\*" "%DOCS_HOME%\" >nul
if errorlevel 1 (
    echo [ERROR] Failed to copy "%BACKUP_DIR%" to "%DOCS_HOME%".
    pause
    exit /b 1
)
echo [%date% %time%] Demo data restored successfully.

echo [%date% %time%] Starting FormosaBearV Demo Server on port %PORT%...
start /b "" "%JAVA_CMD%" -Ddocs.home="%DOCS_HOME%" -Djetty.http.port=%PORT% -DSTOP.PORT=%STOP_PORT% -DSTOP.KEY=%STOP_KEY% -jar jetty-home\start.jar --module=ee10-deploy,http

echo [%date% %time%] Server running. Next automatic reset in %RESET_SECONDS% seconds...
timeout /t %RESET_SECONDS% /nobreak >nul

echo.
echo [%date% %time%] Reset interval reached. Restarting and resetting demo data...
goto demo_loop
