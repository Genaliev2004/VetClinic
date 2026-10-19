@echo off
rem ======================================================================
rem  VetClinic: create the SQL Server database (Windows authentication).
rem  Usage: scripts\deploy_mssql.bat [server]     (default: localhost)
rem  For a named instance: scripts\deploy_mssql.bat localhost\SQLEXPRESS
rem  (then also change mssql.url in config\db.properties)
rem ======================================================================
chcp 65001 >nul
setlocal
cd /d "%~dp0.."

set "SERVER=%~1"
if not defined SERVER set "SERVER=localhost"
where sqlcmd >nul 2>nul || (
    echo sqlcmd not found. Install "Microsoft Command Line Utilities for SQL Server" or SSMS.
    exit /b 1
)
set "SQLCMD=sqlcmd -S %SERVER% -E -C -I -f 65001 -b"

echo [00] Database vet_clinic on %SERVER%
%SQLCMD% -i sql\mssql\00_create_database.sql || exit /b 1
echo [01] Schema
%SQLCMD% -d vet_clinic -i sql\mssql\01_schema.sql || exit /b 1
echo [02] Test data
%SQLCMD% -d vet_clinic -i sql\mssql\02_data.sql || exit /b 1
echo [03] Constraint tests (expect PASS = 34, FAIL = 0)
sqlcmd -S %SERVER% -E -C -I -f 65001 -W -d vet_clinic -i sql\mssql\03_constraint_tests.sql
echo Done.
endlocal
