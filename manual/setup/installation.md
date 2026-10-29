---
title: Installation
covers:
  screens: []
  stories: []
---

# Installation

OEA consists of a PostgreSQL database, a one-shot migration step and the backend. The operator file `compose.yaml` in the repository starts all three with Docker Compose (or Podman Compose). If you run your own PostgreSQL, follow [Your own PostgreSQL](#your-own-postgresql).

## Requirements

- Docker with Compose v2, or Podman with Compose
- PostgreSQL 15 or later if you use your own database (the Compose setup uses PostgreSQL 18)
- A file for the key-encryption key, readable by the container user

## Install with Docker Compose

1. Copy `.env.example` to `.env` and fill in **every** value. Use long random values (at least 16 characters); known development values are rejected. Never commit `.env`.

   | Variable | Purpose |
   |---|---|
   | `POSTGRES_PASSWORD` | PostgreSQL superuser, used only by the provisioning script on the first start |
   | `OEA_OWNER_PASSWORD`, `OEA_REPORTING_OWNER_PASSWORD` | owner roles of the migration step |
   | `OEA_APP_PASSWORD` | runtime role of the backend |
   | `OEA_AUDIT_CLEANUP_PASSWORD`, `OEA_AUDITOR_PASSWORD`, `OEA_ANALYTICS_PASSWORD` | roles for audit retention, audit verification and analytics |
   | `OEA_KEK_FILE` | path of the key-encryption key file |
   | `OEA_HTTP_BIND`, `OEA_HTTP_PORT` | optional: published address, default `127.0.0.1:8080` |
   | `OEA_LOG_FORMAT` | optional: `ecs`, `logstash` or `gelf` for JSON logs, default plain text (see [Log format](../administration/operations#log-format)) |

2. Create the key-encryption key. It encrypts stored secrets (second factors, client secrets of identity providers). Keep it outside the database backups:

   ```bash
   head -c 32 /dev/urandom | base64 > /secure/path/oea-kek.key
   chmod 0444 /secure/path/oea-kek.key
   ```

3. Start OEA:

   ```bash
   docker compose up -d
   ```

   On the first start, PostgreSQL runs the provisioning script and creates the database roles. Then the migration step creates the schema, and the backend starts.

4. Check that the instance is up:

   ```bash
   curl http://127.0.0.1:8080/api/v1/health
   ```

   The answer contains only the overall status `UP` or `DOWN`.

Continue with the [first administrator](./first-administrator).

::: warning Public access
Do not make the instance reachable from the internet before the setup phase has ended (see [First administrator](./first-administrator)). Put a reverse proxy with TLS in front of OEA and set the public address with `OEA_WEB_PUBLIC_URL` (`https`) before you connect an identity provider.
:::

## Your own PostgreSQL

If you use a managed cloud database or an existing cluster, you perform the provisioning and the migration yourself.

### Provisioning (once)

The script `deploy/postgres/initdb/10-oea-roles.sh` creates the six database roles, sets their passwords, restricts `CONNECT` on the database to the OEA roles and creates the schemas with their owners. You need an empty database (default name `oea`), a role that may create roles, and `psql` 15 or later:

```bash
export PGHOST=db.example.org PGPORT=5432 PGUSER=postgres PGPASSWORD=...
export OEA_DB_NAME=oea
export OEA_OWNER_PASSWORD=... OEA_REPORTING_OWNER_PASSWORD=... OEA_APP_PASSWORD=...
export OEA_AUDIT_CLEANUP_PASSWORD=... OEA_AUDITOR_PASSWORD=... OEA_ANALYTICS_PASSWORD=...
deploy/postgres/initdb/10-oea-roles.sh
```

- The script reads passwords from the environment only; they never appear on the command line or in the output.
- If a password is missing, the script fails and names the missing variables.
- You can run the script again: it resets role attributes, passwords and schema owners, so you can also use it to rotate role passwords.
- Treat every password as a deployment secret: keep it in a secret store, not in your shell history.

The roles are described in [Operations](../administration/operations#database-roles).

### Migration (before every start of a new version)

Schema migrations run in a one-shot step before the backend starts, never inside the running backend:

```bash
java -jar oea-backend.jar migrate \
  --oea.migration.url=jdbc:postgresql://db.example.org:5432/oea \
  --oea.migration.owner.password="$OEA_OWNER_PASSWORD" \
  --oea.migration.reporting-owner.password="$OEA_REPORTING_OWNER_PASSWORD"
```

You can also pass the settings as environment variables (`OEA_MIGRATION_URL`, `OEA_MIGRATION_OWNER_PASSWORD`, `OEA_MIGRATION_REPORTINGOWNER_PASSWORD`); without `oea.migration.url`, `spring.datasource.url` is used. The step exits with code `0` on success and `1` on any failure.

| Migration set | Runs as | Schema history table |
|---|---|---|
| `db/migration` | `oea_owner` | `public.flyway_schema_history` |
| `db/migration-audit` | `oea_owner` | `audit.flyway_schema_history` |
| `db/migration-reporting` | `oea_reporting_owner` | `reporting.flyway_schema_history` |

### Start the backend

The backend connects with the runtime role `oea_app`:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://db.example.org:5432/oea \
SPRING_DATASOURCE_USERNAME=oea_app \
SPRING_DATASOURCE_PASSWORD="$OEA_APP_PASSWORD" \
OEA_SECURITY_KEK_CURRENT=/run/secrets/oea_kek \
OEA_WEB_PUBLIC_URL=https://oea.example.org \
java -jar oea-backend.jar
```

- `OEA_WEB_PUBLIC_URL` (`oea.web.public-url`) is the address under which users reach OEA. You need it as soon as an identity provider is enabled; it must use `https` and must not be a loopback address.
- If the migration step has not run for the current version, the backend refuses to start and names the migration step.
- The backend refuses to start if the key file is missing, unreadable, not exactly 256 bits, or the development key of the repository.
