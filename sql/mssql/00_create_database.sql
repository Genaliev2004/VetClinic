-- =====================================================================
-- ВетКлиник. Microsoft SQL Server. Шаг 0: база данных.
-- Запуск (Windows-аутентификация):
--   sqlcmd -S localhost -E -C -I -f 65001 -i sql\mssql\00_create_database.sql
-- Скрипт идемпотентный: если база уже есть, ничего не делает.
-- =====================================================================
IF DB_ID(N'vet_clinic') IS NULL
BEGIN
    -- Кириллица хранится в NVARCHAR, поэтому сортировка БД может быть любой;
    -- явно задаём кириллическую, чтобы ORDER BY по фамилиям работал правильно.
    CREATE DATABASE vet_clinic COLLATE Cyrillic_General_CI_AS;
END;
GO

-- -----------------------------------------------------------------------
-- Необязательно: вход с SQL-аутентификацией для одногруппника/Linux-клиента.
-- Работает, только если на сервере включён смешанный режим аутентификации.
-- Раскомментируйте при необходимости (пароль — как в config/db.properties).
-- -----------------------------------------------------------------------
-- IF SUSER_ID(N'vet_app') IS NULL
--     CREATE LOGIN vet_app WITH PASSWORD = N'VetApp_2026', CHECK_POLICY = OFF;
-- GO
-- USE vet_clinic;
-- IF USER_ID(N'vet_app') IS NULL
-- BEGIN
--     CREATE USER vet_app FOR LOGIN vet_app;
--     ALTER ROLE db_owner ADD MEMBER vet_app;
-- END;
-- GO
