@echo off
setlocal
cd /d "%~dp0"

echo [FormosaBearV] Launching Portable Distribution Builder...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\package-portable.ps1"
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Packaging failed. Please see the error message above.
    pause
    exit /b %errorlevel%
)

echo.
pause
