@echo off
rem ======================================================================
rem  VetClinic: create the PostgreSQL database (Windows).
rem  1) asks for the password of the "postgres" superuser (step 00);
rem  2) creates tables, loads test data and runs constraint tests as vet_app.
rem  Usage: scripts\deploy_postgresql.bat [path\to\psql.exe]
rem ======================================================================
chcp 65001 >nul
setlocal
cd /d "%~dp0.."

set "PSQL=%~1"
if not defined PSQL for /f "delims=" %%p in ('where psql 2^>nul') do if not defined PSQL set "PSQL=%%p"
if not defined PSQL for /d %%d in ("C:\Program Files\PostgreSQL\*") do if exist "%%d\bin\psql.exe" set "PSQL=%%d\bin\psql.exe"
if not defined PSQL (
    echo psql.exe not found. Pass the path: scripts\deploy_postgresql.bat "C:\Program Files\PostgreSQL\18\bin\psql.exe"
    exit /b 1
)
echo Using %PSQL%
set PGCLIENTENCODING=UTF8

echo.
echo [00] Role vet_app and database vet_clinic (password of user postgres is required)
"%PSQL%" -h localhost -U postgres -v ON_ERROR_STOP=1 -f sql/postgresql/00_create_database.sql || exit /b 1

set PGPASSWORD=VetApp_2026
echo [01] Schema
"%PSQL%" -h localhost -U vet_app -d vet_clinic -v ON_ERROR_STOP=1 -q -f sql/postgresql/01_schema.sql || exit /b 1
echo [02] Test data
"%PSQL%" -h localhost -U vet_app -d vet_clinic -v ON_ERROR_STOP=1 -q -f sql/postgresql/02_data.sql || exit /b 1
echo [03] Constraint tests (expect PASS = 34, FAIL = 0)
"%PSQL%" -h localhost -U vet_app -d vet_clinic -q -f sql/postgresql/03_constraint_tests.sql
echo Done.
endlocal
