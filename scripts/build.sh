#!/usr/bin/env bash
# ======================================================================
#  ВетКлиник: сборка app/target/vetclinic.jar (Linux, macOS).
#  Maven ставить не нужно — его скачает Maven Wrapper (нужен curl или wget и unzip).
#  bash scripts/build.sh        — сборка без тестов
#  bash scripts/build.sh test   — модульные и интеграционные тесты, затем сборка
#                                 (на Linux: -Dtest.databases=postgresql подставляется сам)
# ======================================================================
set -euo pipefail
cd "$(dirname "$0")/../app"
chmod +x mvnw
if [ "${1:-}" = "test" ]; then
    ./mvnw -B package -Dtest.databases=postgresql
else
    ./mvnw -B package -DskipTests
fi
