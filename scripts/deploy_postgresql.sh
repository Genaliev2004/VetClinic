#!/usr/bin/env bash
# ======================================================================
#  ВетКлиник: создание базы данных PostgreSQL (Linux).
#  Шаг 00 выполняется от системного пользователя postgres (sudo),
#  шаги 01–03 — от пользователя приложения vet_app.
#  Запуск из корня проекта: bash scripts/deploy_postgresql.sh
# ======================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
export PGCLIENTENCODING=UTF8

echo "[00] Роль vet_app и база vet_clinic"
# файл копируется во /tmp, т.к. у пользователя postgres может не быть доступа к домашнему каталогу
cp sql/postgresql/00_create_database.sql /tmp/vet_00.sql
sudo -u postgres psql -v ON_ERROR_STOP=1 -f /tmp/vet_00.sql
rm -f /tmp/vet_00.sql

export PGPASSWORD=VetApp_2026
PSQL="psql -h localhost -U vet_app -d vet_clinic"
echo "[01] Схема"
$PSQL -v ON_ERROR_STOP=1 -q -f sql/postgresql/01_schema.sql
echo "[02] Тестовые данные"
$PSQL -v ON_ERROR_STOP=1 -q -f sql/postgresql/02_data.sql
echo "[03] Проверка ограничений (ожидается PASS = 34, FAIL = 0)"
$PSQL -q -f sql/postgresql/03_constraint_tests.sql
echo "Готово."
