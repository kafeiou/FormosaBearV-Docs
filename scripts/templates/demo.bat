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

set "SERVER_RUNNING=0"

:demo_loop
if not exist "%BACKUP_DIR%" (
    echo [WARNING] Baseline directory "data.bak" was not found!
    echo           Please manually copy "data" to "data.bak" to enable automatic reset/restart.
    if "!SERVER_RUNNING!"=="0" (
        echo           Starting server normally using "data" ^(will check again at the next interval^)...
        if not exist "%DOCS_HOME%" mkdir "%DOCS_HOME%"
        echo [%date% %time%] Starting FormosaBearV Demo Server on port %PORT%...
        start /b "" "%JAVA_CMD%" -Ddocs.home="%DOCS_HOME%" -Djetty.http.port=%PORT% -DSTOP.PORT=%STOP_PORT% -DSTOP.KEY=%STOP_KEY% -jar jetty-home\start.jar --module=ee10-deploy,http
        set "SERVER_RUNNING=1"
    ) else (
        echo           Keeping current server running without resetting "data" ^(will check again at the next interval^)...
    )
    echo [%date% %time%] Next check in %RESET_SECONDS% seconds...
    timeout /t %RESET_SECONDS% /nobreak >nul
    echo.
    goto demo_loop
)

echo [%date% %time%] Stopping any running Jetty instance on stop port %STOP_PORT%...
"%JAVA_CMD%" -DSTOP.PORT=%STOP_PORT% -DSTOP.KEY=%STOP_KEY% -jar jetty-home\start.jar --stop --stop-wait=30 >nul 2>&1

echo [%date% %time%] Waiting for Jetty process to exit completely...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$docsHome = $env:DOCS_HOME; $stopPort = $env:STOP_PORT; " ^
  "function Get-DemoJetty { Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -and $_.CommandLine -like '*jetty-home*start.jar*' -and ($_.CommandLine -like ('*' + $docsHome + '*') -or $_.CommandLine -like ('*-DSTOP.PORT=' + $stopPort + '*')) } }; " ^
  "$deadline = (Get-Date).AddSeconds(30); " ^
  "while ((Get-Date) -lt $deadline) { if (-not (Get-DemoJetty)) { exit 0 }; Start-Sleep -Seconds 1 }; " ^
  "$procs = @(Get-DemoJetty); " ^
  "if ($procs.Count -gt 0) { Write-Host '[WARN] Jetty did not exit within 30s; forcing termination...'; foreach ($p in $procs) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }; Start-Sleep -Seconds 3 }"
set "SERVER_RUNNING=0"

echo [%date% %time%] Resetting "%DOCS_HOME%" from "%BACKUP_DIR%"...
if exist "%DOCS_HOME%" (
    set "RM_TRIES=0"
    :retry_rmdir
    set /a "RM_TRIES+=1"
    rmdir /s /q "%DOCS_HOME%" >nul 2>&1
    if exist "%DOCS_HOME%" (
        if !RM_TRIES! lss 5 (
            echo [WARN] Waiting for file locks to release ^(attempt !RM_TRIES!/5^)...
            ping 127.0.0.1 -n 3 >nul 2>&1
            goto retry_rmdir
        )
        echo [ERROR] Failed to remove "%DOCS_HOME%" after 5 attempts. Retrying reset cycle in 10 seconds...
        ping 127.0.0.1 -n 11 >nul 2>&1
        goto demo_loop
    )
)

xcopy /e /i /h /y /q "%BACKUP_DIR%\*" "%DOCS_HOME%\" >nul
if errorlevel 1 (
    echo [ERROR] Failed to copy "%BACKUP_DIR%" to "%DOCS_HOME%". Retrying reset cycle in 10 seconds...
    ping 127.0.0.1 -n 11 >nul 2>&1
    goto demo_loop
)
echo [%date% %time%] Demo data restored successfully.

echo [%date% %time%] Starting FormosaBearV Demo Server on port %PORT%...
start /b "" "%JAVA_CMD%" -Ddocs.home="%DOCS_HOME%" -Djetty.http.port=%PORT% -DSTOP.PORT=%STOP_PORT% -DSTOP.KEY=%STOP_KEY% -jar jetty-home\start.jar --module=ee10-deploy,http
set "SERVER_RUNNING=1"

echo [%date% %time%] Server running. Next automatic reset in %RESET_SECONDS% seconds...
timeout /t %RESET_SECONDS% /nobreak >nul

echo.
echo [%date% %time%] Reset interval reached. Restarting and resetting demo data...
goto demo_loop
