-- =====================================================================
-- ВетКлиник. PostgreSQL. Шаг 0: роль приложения и база данных.
-- Запускать от суперпользователя (обычно postgres):
--   psql -h localhost -U postgres -f sql/postgresql/00_create_database.sql
-- Скрипт идемпотентный: повторный запуск ничего не ломает.
-- =====================================================================

-- Роль, от имени которой работает приложение (пароль совпадает с config/db.properties)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'vet_app') THEN
        CREATE ROLE vet_app LOGIN PASSWORD 'VetApp_2026';
    END IF;
END $$;

-- CREATE DATABASE нельзя выполнить внутри блока DO, поэтому используем \gexec:
-- команда сформируется и выполнится только если базы ещё нет.
SELECT 'CREATE DATABASE vet_clinic OWNER vet_app ENCODING ''UTF8'' TEMPLATE template0'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'vet_clinic')
\gexec
