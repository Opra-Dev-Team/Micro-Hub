@echo off
setlocal EnableExtensions
cd /d "%~dp0"
rem Hand off to the window launcher and exit so this console does not stay open.
start "" wscript //nologo "%~dp0OpraMicroHub-Setup.vbs"
endlocal
exit /b 0
