#!/usr/bin/env bash
# Development environment helper (ADR-036 §3).
#   scripts/dev.sh            start everything (build the backend image first)
#   scripts/dev.sh down       stop the environment
#   scripts/dev.sh reset      stop and delete the development volumes (database, Keycloak)
#   scripts/dev.sh <args>     any other docker compose arguments
set -euo pipefail
cd "$(dirname "$0")/.."
compose=(docker compose -f compose.dev.yaml --profile dev)
case "${1:-up}" in
  up) "${compose[@]}" up --build ;;
  reset) "${compose[@]}" down -v ;;
  *) "${compose[@]}" "$@" ;;
esac
