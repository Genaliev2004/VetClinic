@echo off
rem ======================================================================
rem  VetClinic: start the application (Windows).
rem  Settings: config\db.properties + config\os\windows.properties.
rem  The DBMS can be chosen in the login window or here:
rem      scripts\run.bat -Ddb.active=mssql
rem ======================================================================
chcp 65001 >nul
setlocal
cd /d "%~dp0.."
if not exist "app\target\vetclinic.jar" (
    echo app\target\vetclinic.jar not found - run scripts\build.bat first.
    exit /b 1
)
rem java.library.path - native DLL for SQL Server Windows authentication
java -Dstdout.encoding=UTF-8 "-Djava.library.path=app\target\native" %* -jar app\target\vetclinic.jar
endlocal
