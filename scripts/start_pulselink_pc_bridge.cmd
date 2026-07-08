@echo off
setlocal
where pwsh.exe >nul 2>nul
if %ERRORLEVEL%==0 (
    pwsh.exe -NoProfile -File "%~dp0start_pulselink_pc_bridge.ps1" %*
) else (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start_pulselink_pc_bridge.ps1" %*
)
exit /b %ERRORLEVEL%
