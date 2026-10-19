@echo off
rem ======================================================================
rem  VetClinic: build app\target\vetclinic.jar (Windows).
rem  Maven does not need to be installed: the Maven Wrapper downloads it.
rem  Usage: scripts\build.bat            - build without tests
rem         scripts\build.bat test       - run unit and integration tests, then build
rem ======================================================================
setlocal
cd /d "%~dp0..\app"
rem '.\' is required: without it cmd fails to start mvnw.cmd from a folder with non-ASCII characters
if /i "%~1"=="test" (
    call .\mvnw.cmd -B package
) else (
    call .\mvnw.cmd -B package -DskipTests
)
exit /b %ERRORLEVEL%
