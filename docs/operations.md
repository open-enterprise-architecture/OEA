# Operations

How operators set up the database for OEA, run the migration step, manage the key-encryption key and perform the first administration. The shipped Docker Compose setup performs these steps automatically; this document covers operator-managed PostgreSQL (for example a managed cloud database or an existing cluster).

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
OEA_SECURITY_KEK_CURRENT=/run/secrets/oea_kek \
OEA_WEB_PUBLIC_URL=https://oea.example.org \
java -jar oea-backend.jar
```

`oea.web.public-url` (`OEA_WEB_PUBLIC_URL`) is the address of the web portal as users reach it; it is required as soon as an identity provider is enabled and must use `https` (not a loopback address) outside the profile `dev`.

If the migration step has not run for the current version, the application refuses to start with a message naming the migration step. The health probe is `GET /api/v1/health`; it returns only the overall status (`UP` or `DOWN`).

## Key-Encryption Key

Stored secrets – TOTP secrets and OIDC client secrets – are encrypted with AES-256-GCM under a key-encryption key that lives in a file outside the database ([REQ-188](../requirements/req/REQ-188-secrets-key-management.md)). Every encrypted value records the ID of its key (the first 16 hex digits of the key's SHA-256).

```bash
head -c 32 /dev/urandom | base64 > /secure/path/oea-kek.key
```

- `oea.security.kek.current` (`OEA_SECURITY_KEK_CURRENT`) names the key file; the shipped Compose setup mounts the file named by `OEA_KEK_FILE` as the secret `/run/secrets/oea_kek` (the file must be readable by the container user, see `.env.example`)
- The application refuses to start if the file is missing, unreadable, not exactly 256 bits, or the development key `dev/secrets/oea-kek-dev-only.key` (guard G4)
- Keep the key out of database backups and store it separately; a database dump without the key does not reveal the secrets, and the secrets are lost without it

### Key Rotation

1. Create a new key file and configure it as `oea.security.kek.current`; configure the old one as `oea.security.kek.previous` (a list of files, `OEA_SECURITY_KEK_PREVIOUS`)
2. Re-encrypt all stored secrets with the runtime credentials:

   ```bash
   java -jar oea-backend.jar secrets reencrypt \
     --spring.datasource.url=jdbc:postgresql://db.example.org:5432/oea \
     --spring.datasource.username=oea_app \
     --spring.datasource.password="$OEA_APP_PASSWORD" \
     --oea.security.kek.current=/run/secrets/oea_kek \
     --oea.security.kek.previous=/run/secrets/oea_kek_previous
   ```

3. Remove the old key from the configuration and destroy it

The command re-encrypts in one transaction and prints `OK <n> secret(s) re-encrypted with key <key ID>`. Exit codes: `0` done, `1` a value could not be decrypted with the configured keys (nothing was changed), `2` usage or connection error.

## Setup Phase and First Administration

After the bootstrapping the instance is in its setup phase ([ADR-042](../adrs/ADR-042-setup-phase-without-second-factor.md)): the local system admin account logs in with login name and password only. The first identity binding or IdP group mapping – the first action that gives another identity access – is rejected with `403` and problem type `urn:oea:problem:second-factor-required` until the system admin has enrolled a TOTP second factor and confirmed the current session with it. That action ends the setup phase for good (audit event `system.setup-phase-ended`); from then on every login of the system admin needs a TOTP code.

During the setup phase the instance is protected by the admin password and login throttling only. Do not expose it publicly before the setup phase has ended.

Administration has no screens yet; providers, persons, bindings and mappings are managed through the API. The examples use `curl` and `jq` and keep passwords and secrets out of command lines:

```bash
OEA=https://oea.example.org/api/v1
read -rs OEA_ADMIN_PASSWORD

# Log in; the session is the cookie __Host-OEA_SESSION, the CSRF token is in the response body
jq -n --arg p "$OEA_ADMIN_PASSWORD" '{loginName: "admin", password: $p}' |
  curl -sS -D login.headers -o login.json -H 'Content-Type: application/json' -d @- "$OEA/auth/local/login"
SESSION=$(sed -n 's/^set-cookie: __Host-OEA_SESSION=\([^;]*\).*/\1/Ip' login.headers)
CSRF=$(jq -r .csrfToken login.json)
auth=(-sS -H "Cookie: __Host-OEA_SESSION=$SESSION" -H "X-XSRF-TOKEN: $CSRF" -H 'Content-Type: application/json')
```

Changing a provider and enrolling a second factor need an authentication within the last 5 minutes; log in again if the response is `401 reauthentication-required`.

```bash
# OIDC provider (outside the profile dev: https issuer, no loopback address); the client secret is write-only
read -rs OEA_OIDC_CLIENT_SECRET
jq -n --arg s "$OEA_OIDC_CLIENT_SECRET" '{name: "Corporate IdP", type: "oidc", vendor: "entra-id",
    issuerUrl: "https://login.microsoftonline.com/<tenant-id>/v2.0", clientId: "<client-id>", clientSecret: $s}' |
  curl "${auth[@]}" -d @- "$OEA/admin/auth/providers" | tee provider.json
PROVIDER=$(jq -r .id provider.json)

# Person (status invited) and binding to the immutable subject of the provider (Entra ID: oid)
PERSON=$(curl "${auth[@]}" -d '{"name": "Ada Lovelace", "email": "ada@example.org"}' "$OEA/admin/persons" | jq -r .id)
curl "${auth[@]}" -d "{\"authProviderId\": \"$PROVIDER\", \"subject\": \"<subject>\"}" \
  "$OEA/admin/persons/$PERSON/identities"
# → 403 second-factor-required while the setup phase lasts and the session has no second factor

# TOTP enrollment: render the otpauth URI as QR code locally (for example with qrencode), never online
curl "${auth[@]}" -d '{"label": "phone"}' "$OEA/me/second-factors/totp" | tee totp.json
qrencode -t ansiutf8 "$(jq -r .otpauthUri totp.json)"
curl "${auth[@]}" -d '{"code": "<code from the app>"}' \
  "$OEA/me/second-factors/totp/$(jq -r .credentialId totp.json)/confirm"
rm totp.json

# Repeat the binding (ends the setup phase), then map a provider group (its immutable UUID) to a role
curl "${auth[@]}" -d "{\"authProviderId\": \"$PROVIDER\", \"subject\": \"<subject>\"}" \
  "$OEA/admin/persons/$PERSON/identities"
curl "${auth[@]}" -d "{\"authProviderId\": \"$PROVIDER\", \"groupIdentifier\": \"<group-uuid>\", \"roleId\": \"viewer\"}" \
  "$OEA/admin/idp-group-mappings"

# Log out
curl "${auth[@]}" -X POST "$OEA/auth/logout"
```

After the setup phase the password step answers `401` with problem type `urn:oea:problem:second-factor-required` and a `loginTransaction`; the login completes with `POST /auth/local/login/totp` and `{"loginTransaction": "…", "code": "…"}`. Five wrong codes invalidate the login transaction. Wrong passwords are throttled per login name (exponential backoff after 5 failures within 15 minutes, at most 15 minutes) and per source address (100 failures within 10 minutes); throttled responses carry `Retry-After`.

Other operations: `GET`/`PUT /admin/auth/providers/{id}` (deactivate with `"enabled": false`), `GET /admin/persons`, `GET`/`DELETE /admin/persons/{personId}/identities[/{identityId}]`, `GET /admin/idp-group-mappings`, `PUT`/`DELETE /admin/idp-group-mappings/{mappingId}` (soft delete). Group identifiers must be UUIDs (the group object IDs of Entra ID, Authentik and Keycloak); display names are rejected with `422`. In the profile `dev` the issuer may use `http` and a loopback address, for the Keycloak of the development environment.

## Login Through an Identity Provider

Persons log in through the identity providers stored with the admin API (see above); the stored, enabled providers are the only source of the OIDC login. The former property list `oea.security.oidc.providers` no longer exists and is ignored. At startup the application refuses to start if an enabled provider has an issuer without `https` or on a loopback address (outside `dev`, ADR-036 G2/G3); disabled providers are ignored.

**At the provider**: register OEA as confidential web client with the authorization code flow and PKCE (`S256`) and the redirect URI `<public URL>/api/v1/auth/oidc/callback`. The ID token must carry the group membership as immutable group identifiers in the claim `groups` and, for privileged roles, the authentication methods in `amr`:

| Provider | Scopes of the stored provider | Groups | Second factor |
|---|---|---|---|
| Microsoft Entra ID | `openid profile email offline_access` (`offline_access` yields the refresh token) | Token configuration: groups claim with group object IDs; too many groups produce an overage indicator, which OEA treats as no groups | `amr` contains `mfa` when a Conditional Access policy required MFA |
| Authentik | `openid profile email` | Property mapping that emits the group UUIDs as `groups` | Property mapping for `amr` (`pwd`, `otp`, `mfa`) |
| Keycloak | `openid profile email` | Mapper that emits group identifiers, not names | Authentication flow with method references (`pwd`, `otp`) and a *max age* of at least the OEA session lifetime (e.g. 43200 s), as in the development realm; with max age 0 Keycloak drops `amr` from tokens issued later, including the refreshed ID tokens of the 15-minute re-evaluation |

**Persons and roles**: a person gets a session only if the pair of issuer and `sub` is bound to a person in status `invited` or `active` (bindings are never derived from e-mail addresses); the first login changes `invited` to `active`. Roles come from the IdP group mappings of the provider; privileged roles (with admin-class permissions) are effective only if `amr` shows a second factor. A person whose own groups claim contains a group cannot map that group (SoD-1).

**Session lifetime**: a session established through an IdP ends at the first of the OEA idle timeout (30 minutes), the OEA absolute lifetime (12 hours) and the expiry or rejection of the IdP refresh token; OEA never extends an IdP token. Group membership is re-evaluated with the refresh token at the latest 15 minutes after the previous evaluation: removing a person from a group at the IdP removes the derived role within 15 minutes. Without a refresh token, with an unreachable IdP, or with a missing or truncated groups claim, no IdP-derived role applies (fail closed, audit event `auth.idp-groups-unusable`).

**Split-horizon networks**: if the backend reaches the IdP under another address than browsers, map the issuer to the internal address; the issuer in the discovery document and in the tokens is still checked against the stored issuer. Outside `dev` the location must use `https` and must not be a loopback address.

```yaml
oea:
  security:
    oidc:
      discovery-locations:
        - issuer: https://login.example.org/realms/oea
          location: https://idp.internal.example.org/realms/oea
```

**Failures**: a refused login (unknown identity, inactive person, invalid token, cancelled login) returns the browser to `<public URL>/login?error=login-failed` without revealing the reason; the reason is in the audit event `auth.login-failed`. An unreachable provider leads to `?error=provider-unavailable`; a retry is possible.
