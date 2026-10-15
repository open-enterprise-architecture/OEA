#!/usr/bin/env bash
# Spec platform/deployment, scenario "All secrets supplied": starts compose.yaml with
# random secrets and expects GET /api/v1/health = 200 within 60 seconds.
# Requires the image ${OEA_BACKEND_IMAGE:-oea-backend:local}. Usage: scripts/compose_smoke_test.sh
# OEA_HOST_PROJECT_DIR: repository path as seen by the Docker daemon, if it differs
# (e.g. inside a Distrobox).
set -euo pipefail
cd "$(dirname "$0")/.."
limit=60
port="${OEA_SMOKE_PORT:-18080}"
env_file="$(mktemp)"
trap 'docker compose -p oea-smoke --env-file "$env_file" -f compose.yaml ${OEA_HOST_PROJECT_DIR:+--project-directory "$OEA_HOST_PROJECT_DIR"} down -v >/dev/null 2>&1 || true; rm -f "$env_file"' EXIT
for name in POSTGRES_PASSWORD OEA_OWNER_PASSWORD OEA_REPORTING_OWNER_PASSWORD OEA_APP_PASSWORD \
            OEA_AUDIT_CLEANUP_PASSWORD OEA_AUDITOR_PASSWORD OEA_ANALYTICS_PASSWORD; do
  echo "$name=$(head -c 24 /dev/urandom | base64 | tr -d '/+=')" >> "$env_file"
done
echo "OEA_HTTP_PORT=$port" >> "$env_file"

compose=(docker compose -p oea-smoke --env-file "$env_file" -f compose.yaml ${OEA_HOST_PROJECT_DIR:+--project-directory "$OEA_HOST_PROJECT_DIR"})
start=$(date +%s)
if ! "${compose[@]}" up -d --no-build; then
  echo "✗ a service failed to start"
  "${compose[@]}" ps -a
  "${compose[@]}" logs --tail 50
  exit 1
fi
status=000
while (( $(date +%s) - start <= limit )); do
  status=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:$port/api/v1/health" || true)
  [[ "$status" == 200 ]] && break
  sleep 1
done
elapsed=$(( $(date +%s) - start ))
"${compose[@]}" ps -a --format '{{.Service}}: {{.State}} (exit {{.ExitCode}})'
if [[ "$status" != 200 ]]; then
  echo "✗ health endpoint returned $status after ${elapsed}s (limit ${limit}s)"
  "${compose[@]}" logs --tail 50
  exit 1
fi
echo "✓ migration step done and GET /api/v1/health = 200 after ${elapsed}s (limit ${limit}s)"
