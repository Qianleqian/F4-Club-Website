@echo off
setlocal
cd /d "%~dp0"
if defined JAVA_HOME goto run
for /d %%J in ("%USERPROFILE%\.jdks\*") do if exist "%%~J\bin\java.exe" set "JAVA_HOME=%%~J"
:run
echo Campus Clubs - http://127.0.0.1:8080
echo Keep this window open. Press Ctrl+C to stop.
call "%~dp0gradlew.bat" run
if errorlevel 1 pause
