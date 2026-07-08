@echo off
setlocal
call "%~dp0start_pulselink_pc_bridge.cmd" -Mode Hotspot %*
exit /b %ERRORLEVEL%
