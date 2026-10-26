#!/usr/bin/env bash
# Provisioning of the OEA database roles and schemas (ADR-037 §1-§2).
#
# Runs once with database superuser rights:
# - automatically by the official PostgreSQL image on an empty data volume
#   (mounted into /docker-entrypoint-initdb.d), or
# - manually for operator-managed PostgreSQL, see the user manual,
#   https://open-enterprise-architecture.github.io/OEA/setup/installation#provisioning-once
#
# Role passwords are read from the environment only and are never passed on
# the command line. The script is idempotent: existing roles get their
# attributes and passwords reset, existing schemas get their owners reset.
set -euo pipefail

required=(
  OEA_OWNER_PASSWORD
  OEA_REPORTING_OWNER_PASSWORD
  OEA_APP_PASSWORD
  OEA_AUDIT_CLEANUP_PASSWORD
  OEA_AUDITOR_PASSWORD
  OEA_ANALYTICS_PASSWORD
)
missing=()
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    missing+=("$name")
  fi
done
if (( ${#missing[@]} > 0 )); then
  echo "10-oea-roles.sh: missing required variables: ${missing[*]}" >&2
  exit 1
fi

database="${OEA_DB_NAME:-${POSTGRES_DB:-oea}}"
superuser="${PGUSER:-${POSTGRES_USER:-postgres}}"

psql -v ON_ERROR_STOP=1 --no-psqlrc --username "$superuser" --dbname "$database" <<'SQL'
\getenv owner_password OEA_OWNER_PASSWORD
\getenv reporting_owner_password OEA_REPORTING_OWNER_PASSWORD
\getenv app_password OEA_APP_PASSWORD
\getenv audit_cleanup_password OEA_AUDIT_CLEANUP_PASSWORD
\getenv auditor_password OEA_AUDITOR_PASSWORD
\getenv analytics_password OEA_ANALYTICS_PASSWORD

BEGIN;

-- Roles: login only, no elevated attributes, no inheritance.
SELECT format('CREATE ROLE %I', role_name)
FROM unnest(ARRAY['oea_owner', 'oea_reporting_owner', 'oea_app',
                  'oea_audit_cleanup', 'oea_auditor', 'oea_analytics']) AS role_name
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = role_name)
\gexec

ALTER ROLE oea_owner           WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT PASSWORD :'owner_password';
ALTER ROLE oea_reporting_owner WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT PASSWORD :'reporting_owner_password';
ALTER ROLE oea_app             WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT PASSWORD :'app_password';
ALTER ROLE oea_audit_cleanup   WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT PASSWORD :'audit_cleanup_password';
ALTER ROLE oea_auditor         WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT PASSWORD :'auditor_password';
ALTER ROLE oea_analytics       WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT PASSWORD :'analytics_password';

-- Database-level grants: only the OEA roles may connect.
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database())
\gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO oea_owner, oea_reporting_owner, oea_app, '
              'oea_audit_cleanup, oea_auditor, oea_analytics', current_database())
\gexec

-- Schemas and owners. Object-level grants (schema USAGE, tables, functions)
-- are part of the migrations.
ALTER SCHEMA public OWNER TO oea_owner;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
CREATE SCHEMA IF NOT EXISTS audit AUTHORIZATION oea_owner;
ALTER SCHEMA audit OWNER TO oea_owner;
CREATE SCHEMA IF NOT EXISTS reporting AUTHORIZATION oea_reporting_owner;
ALTER SCHEMA reporting OWNER TO oea_reporting_owner;
CREATE SCHEMA IF NOT EXISTS reporting_ctx AUTHORIZATION oea_reporting_owner;
ALTER SCHEMA reporting_ctx OWNER TO oea_reporting_owner;
REVOKE ALL ON SCHEMA audit, reporting, reporting_ctx FROM PUBLIC;

COMMIT;
SQL

echo "10-oea-roles.sh: provisioned OEA roles and schemas in database ${database}"
