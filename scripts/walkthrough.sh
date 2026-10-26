#!/usr/bin/env bash
# Walkthrough tests of the user manual (ADR-045 rule 5; change s2c-manual-release, design D2).
# Starts the development stack (compose.dev.yaml) with fresh volumes under its own Compose project,
# reads the setup token from the backend container, runs the Playwright walkthrough in manual/ and
# removes the stack again. Screenshots land in manual/screenshots/<language>/.
#
# Requires Docker with Compose, Node.js 24, Python 3 and the Playwright browser
# (cd manual && npm ci --ignore-scripts && npx playwright install chromium).
# The ports of the development stack (5173, 8080, 8180, 55432) must be free: stop scripts/dev.sh first.
#
# Usage: scripts/walkthrough.sh
#   OEA_WALKTHROUGH_LANGUAGES  languages of the user interface to render (default: en)
set -euo pipefail
cd "$(dirname "$0")/.."
compose=(docker compose -p oea-walkthrough -f compose.dev.yaml --profile dev)
trap '"${compose[@]}" down -v --remove-orphans >/dev/null 2>&1 || true' EXIT

wait_for() {
  local name=$1 url=$2 limit=$3 start
  start=$(date +%s)
  until curl -fsS -o /dev/null "$url"; do
    if (( $(date +%s) - start > limit )); then
      echo "✗ $name not reachable at $url after ${limit} s"
      "${compose[@]}" ps -a
      "${compose[@]}" logs --tail 80
      return 1
    fi
    sleep 3
  done
  echo "✓ $name reachable ($(( $(date +%s) - start )) s)"
}

for language in ${OEA_WALKTHROUGH_LANGUAGES:-en}; do
  echo "── Walkthrough in language $language"
  "${compose[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
  "${compose[@]}" build migrate
  "${compose[@]}" up -d
  wait_for Keycloak http://localhost:8180/realms/oea-dev/.well-known/openid-configuration 300
  wait_for Backend http://localhost:8080/api/v1/health 300
  wait_for Frontend http://localhost:5173/ 300

  token=$("${compose[@]}" exec -T backend cat /run/oea/setup-token)
  rm -rf "manual/screenshots/$language"
  (cd manual && OEA_WALKTHROUGH_LANGUAGE="$language" OEA_WALKTHROUGH_SETUP_TOKEN="$token" npm run walkthrough)
done
echo "✓ Screenshots in manual/screenshots/"
