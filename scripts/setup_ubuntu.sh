#!/usr/bin/env bash
# ======================================================================
#  ВетКлиник: развёртывание «с нуля» на Ubuntu 22.04 / 24.04 (проверка на второй ОС).
#  Ставит JDK 17 и PostgreSQL, создаёт базу, запускает тесты и собирает приложение.
#  Запуск из корня клонированного репозитория:  bash scripts/setup_ubuntu.sh
# ======================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== 1. Пакеты: JDK 17, PostgreSQL, unzip, curl"
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk postgresql postgresql-contrib unzip curl fonts-dejavu
sudo systemctl enable --now postgresql

echo "== 2. База данных"
bash scripts/deploy_postgresql.sh

echo "== 3. Тесты (только PostgreSQL) и сборка"
bash scripts/build.sh test

echo "== 4. Готово. Запуск приложения:"
echo "   bash scripts/run.sh"
