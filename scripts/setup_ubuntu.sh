#!/usr/bin/env bash
# ======================================================================
#  ВетКлиник: развёртывание «с нуля» на Ubuntu 22.04 / 24.04 / 25.04 (проверка на второй ОС).
#  Ставит JDK и PostgreSQL, создаёт базу, запускает тесты и собирает приложение.
#  Запуск из корня проекта:  bash scripts/setup_ubuntu.sh
# ======================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

echo "== 1. Список пакетов (apt update)"
if ! sudo apt-get update 2>&1 | tee /tmp/vet_apt.log; then
    true
fi
# У выпусков с закончившейся поддержкой (например, 25.04 после января 2026) пакеты
# переезжают на old-releases.ubuntu.com, и обычные зеркала отвечают 404.
if grep -qE "404|no longer has a Release file|does not have a Release file" /tmp/vet_apt.log; then
    echo "!! Поддержка этого выпуска Ubuntu закончилась — переключаю apt на old-releases.ubuntu.com"
    for f in /etc/apt/sources.list /etc/apt/sources.list.d/*.sources /etc/apt/sources.list.d/*.list; do
        [ -f "$f" ] || continue
        sudo cp "$f" "$f.bak-vetclinic"
        sudo sed -i -E 's#https?://([a-z0-9.-]*\.)?(archive|security)\.ubuntu\.com/ubuntu#http://old-releases.ubuntu.com/ubuntu#g' "$f"
    done
    sudo apt-get update
fi

echo "== 2. Пакеты: JDK, PostgreSQL, unzip, curl, шрифты"
JDK=openjdk-21-jdk
apt-cache show "$JDK" >/dev/null 2>&1 || JDK=openjdk-17-jdk
sudo apt-get install -y "$JDK" postgresql postgresql-contrib unzip curl fonts-dejavu
sudo systemctl enable --now postgresql
java -version

echo "== 3. База данных"
bash scripts/deploy_postgresql.sh

echo "== 4. Тесты (только PostgreSQL) и сборка"
bash scripts/build.sh test

echo
echo "== Готово. Запуск приложения:"
echo "   bash scripts/run.sh"
