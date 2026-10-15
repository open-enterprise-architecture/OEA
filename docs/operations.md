# Operations

How operators set up the database for OEA and run the migration step. The shipped Docker Compose setup performs these steps automatically; this document covers operator-managed PostgreSQL (for example a managed cloud database or an existing cluster).

Background: [ADR-033](../adrs/ADR-033-audit-trail-hardening.md) (audit trail hardening), [ADR-037](../adrs/ADR-037-database-roles-provisioning.md) (database roles and provisioning), [REQ-156](../requirements/req/REQ-156-postgresql-only-database.md) (PostgreSQL only).

## Requirements

- PostgreSQL 15 or later
- An empty database for OEA (default name `oea`)
- One-time access with a role that may create roles (superuser or equivalent) for the provisioning step
- `psql` 15 or later on the machine that runs the provisioning script

## Database Roles

| Role | Used by | Owns |
|---|---|---|
| `oea_owner` | migration step: `db/migration`, `db/migration-audit` | schemas `public`, `audit` and their objects |
| `oea_reporting_owner` | migration step: `db/migration-reporting` | schemas `reporting`, `reporting_ctx` and their objects |
| `oea_app` | application runtime | nothing |
| `oea_audit_cleanup` | audit retention job | nothing |
| `oea_auditor` | audit verification, external auditors | nothing |
| `oea_analytics` | raw analytics connections | nothing |

No role is `SUPERUSER`, `CREATEROLE`, `CREATEDB` or `BYPASSRLS`, and no OEA role is a member of another OEA role. The runtime role `oea_app` holds only `INSERT` and `SELECT` on the audit tables; the application refuses to start otherwise.

## Step 1: Provisioning (once)

The script [`deploy/postgres/initdb/10-oea-roles.sh`](../deploy/postgres/initdb/10-oea-roles.sh) creates the six roles, sets their passwords, restricts `CONNECT` on the database to the OEA roles and creates the schemas with their owners. The shipped Compose setup runs it automatically on an empty data volume.

For operator-managed PostgreSQL, run it with the connection settings of a privileged role (standard `PG*` variables) and the role passwords in the environment:

```bash
export PGHOST=db.example.org PGPORT=5432 PGUSER=postgres PGPASSWORD=...
export OEA_DB_NAME=oea
export OEA_OWNER_PASSWORD=... OEA_REPORTING_OWNER_PASSWORD=... OEA_APP_PASSWORD=...
export OEA_AUDIT_CLEANUP_PASSWORD=... OEA_AUDITOR_PASSWORD=... OEA_ANALYTICS_PASSWORD=...
deploy/postgres/initdb/10-oea-roles.sh
```

- Passwords are read from the environment only and never appear on the command line or in the output
- The script fails and names the missing variables if a password is not set
- The script is idempotent: running it again resets role attributes, passwords and schema owners, so it can also be used to rotate role passwords
- Each password is a deployment secret (REQ-188); use a secret store, not shell history, in production

## Step 2: Migration (before every start of a new version)

Schema migrations run in a one-shot step before the application starts, never inside the running application. The step uses the owner roles; the application itself connects as `oea_app` and only checks that the schema is current.

```bash
java -jar oea-backend.jar migrate \
  --oea.migration.url=jdbc:postgresql://db.example.org:5432/oea \
  --oea.migration.owner.password="$OEA_OWNER_PASSWORD" \
  --oea.migration.reporting-owner.password="$OEA_REPORTING_OWNER_PASSWORD"
```

The settings can also be passed as environment variables (`OEA_MIGRATION_URL`, `OEA_MIGRATION_OWNER_PASSWORD`, `OEA_MIGRATION_REPORTINGOWNER_PASSWORD`); if `oea.migration.url` is not set, `spring.datasource.url` is used. The step exits with code `0` on success and `1` on any failure.

| Migration set | Runs as | Schema history table |
|---|---|---|
| `db/migration` | `oea_owner` | `public.flyway_schema_history` |
| `db/migration-audit` | `oea_owner` | `audit.flyway_schema_history` |
| `db/migration-reporting` | `oea_reporting_owner` | `reporting.flyway_schema_history` |

## Audit Trail Verification

Every audit record is a link of a hash chain computed by the database ([audit hash chain](audit-hash-chain.md), ADR-033). The verification command recomputes the chain with the read-only role `oea_auditor`:

```bash
java -jar oea-backend.jar audit verify \
  --oea.audit-verify.url=jdbc:postgresql://db.example.org:5432/oea \
  --oea.audit-verify.password="$OEA_AUDITOR_PASSWORD" \
  --checkpoints=/var/log/oea/backend.log
```

With Compose: `docker compose run --rm migrate audit verify --oea.audit-verify.url=jdbc:postgresql://postgres:5432/oea --oea.audit-verify.password=…`. The environment variables `OEA_AUDITVERIFY_URL` and `OEA_AUDITVERIFY_PASSWORD` work as well.

| Exit code | Meaning |
|---|---|
| `0` | Chain intact (`OK <links> links, head <chain_seq> <hash>`) |
| `1` | First broken link, missing link, or a database head behind or diverging from a checkpoint (`BROKEN …`) |
| `2` | Usage or connection error |

**Checkpoints:** the backend appends a checkpoint every 60 minutes (`oea.audit.checkpoint-interval`, 5–1440 minutes) and at orderly shutdown, and writes a log line `event=audit.checkpoint chainSeq=… recordHash=… at=…`. Keep the application log outside the database host (log shipper, journald, SIEM): checkpoints are only as trustworthy as the place they are stored. Pass the log file with `--checkpoints=` to detect deleted newest records or a restored older backup.

## Upgrading PostgreSQL to a New Major Version

The shipped Compose setup uses PostgreSQL 18. Data directories are not compatible across major versions, so a major upgrade (for example from an earlier PostgreSQL in an existing volume) needs a dump and restore or `pg_upgrade`:

1. Stop the backend, keep PostgreSQL running, and dump the database as superuser: `pg_dumpall` (roles and data) or `pg_dump -Fc oea` plus the provisioning script for the roles
2. Start the new PostgreSQL version on an empty volume; the provisioning script creates the roles again
3. Restore the dump, then run the migration step and start the application

Since PostgreSQL 18 the official image keeps its data in `/var/lib/postgresql/18/docker`; the Compose volume is therefore mounted at `/var/lib/postgresql`, not at `/var/lib/postgresql/data`. The minimum supported version stays PostgreSQL 15 (REQ-156); the application checks it at startup.

## Step 3: Application Start

The application connects with the runtime role:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://db.example.org:5432/oea \
SPRING_DATASOURCE_USERNAME=oea_app \
SPRING_DATASOURCE_PASSWORD="$OEA_APP_PASSWORD" \
java -jar oea-backend.jar
```

If the migration step has not run for the current version, the application refuses to start with a message naming the migration step. The health probe is `GET /api/v1/health`; it returns only the overall status (`UP` or `DOWN`).
