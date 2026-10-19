#!/usr/bin/env bash
# ======================================================================
#  ВетКлиник: запуск приложения (Linux, macOS).
#  Настройки: config/db.properties + config/os/linux.properties.
#  СУБД можно выбрать в окне входа или так: bash scripts/run.sh -Ddb.active=mssql
# ======================================================================
set -euo pipefail
cd "$(dirname "$0")/.."
if [ ! -f app/target/vetclinic.jar ]; then
    echo "Нет app/target/vetclinic.jar — сначала выполните: bash scripts/build.sh"
    exit 1
fi
exec java "$@" -jar app/target/vetclinic.jar
