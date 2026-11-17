@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME for /d %%J in ("%USERPROFILE%\.jdks\*") do if exist "%%~J\bin\java.exe" set "JAVA_HOME=%%~J"
echo Campus Clubs - http://127.0.0.1:8080
echo Keep this window open. Press Ctrl+C to stop.
if not exist "%~dp0runtime\bin\campus-club.bat" (
  echo Runtime files are missing. Use start-local.bat to build from source.
  pause
  exit /b 1
)
call "%~dp0runtime\bin\campus-club.bat"
if errorlevel 1 pause
