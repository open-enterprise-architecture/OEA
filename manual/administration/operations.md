---
title: Operations
covers:
  screens: []
  stories: [US-011, US-019, US-022, US-027, US-031]
---

# Operations

## Database roles

| Role | Used by | Owns |
|---|---|---|
| `oea_owner` | migration step: `db/migration`, `db/migration-audit` | schemas `public`, `audit` and their objects |
| `oea_reporting_owner` | migration step: `db/migration-reporting` | schemas `reporting`, `reporting_ctx` and their objects |
| `oea_app` | backend at runtime | nothing |
| `oea_audit_cleanup` | audit retention job | nothing |
| `oea_auditor` | audit verification, external auditors | nothing |
| `oea_analytics` | raw analytics connections | nothing |

No role is `SUPERUSER`, `CREATEROLE`, `CREATEDB` or `BYPASSRLS`, and no OEA role is a member of another OEA role. The runtime role `oea_app` holds only `INSERT` and `SELECT` on the audit tables; the backend refuses to start otherwise. You rotate role passwords by running the provisioning script again (see [Installation](../setup/installation#provisioning-once)).

## Request size limits

Request bodies are limited before they are parsed. A larger body is answered with `413` and the problem type `urn:oea:problem:request-too-large` (`package-too-large` for metamodel packages):

| Setting | Default | Applies to |
|---|---|---|
| `oea.web.request-size.default` (`OEA_WEB_REQUESTSIZE_DEFAULT`) | `1MB` | every operation with a request body |
| `oea.web.request-size.entity-batch` (`OEA_WEB_REQUESTSIZE_ENTITYBATCH`) | `10MB` | `POST /entities/batch` |
| `oea.metamodel.import.max-size` (`OEA_METAMODEL_IMPORT_MAXSIZE`) | `10MB` | `POST /admin/metamodel/import` |

A reverse proxy in front of OEA should allow at least the largest of these values.

## Rate limits

Every logged-in person and system admin account may send a limited number of requests per minute. The minute starts with the first request; a request above the limit is answered with `429`, the problem type `urn:oea:problem:too-many-requests` and the header `Retry-After` with the seconds until the next request is accepted. Nothing of a rejected request is processed, and other users are not affected.

| Setting | Default | Applies to |
|---|---|---|
| `oea.web.rate-limit.requests-per-minute` (`OEA_WEB_RATELIMIT_REQUESTSPERMINUTE`) | `600` | every request of a logged-in principal |
| `oea.web.rate-limit.entity-batch-per-minute` (`OEA_WEB_RATELIMIT_ENTITYBATCHPERMINUTE`) | `10` | `POST /entities/batch`, in addition to the request limit |

Both values must be at least `1`; otherwise the backend does not start. The first rejection of a principal within a minute is recorded in the audit trail as `auth.throttled` with the scope `principal`.

The counters are kept in the memory of the backend. If you run several backend instances behind a load balancer, each instance counts on its own, so a principal can send up to the limit times the number of instances. After a restart, the counting starts again.

Logins and the setup page are throttled separately per login name and source address (100 failed attempts within 10 minutes). A rate-limiting reverse proxy in front of OEA is a useful additional layer when the instance is reachable from the internet; it does not replace these limits.

## Key-encryption key

Stored secrets – TOTP secrets and client secrets of identity providers – are encrypted with AES-256-GCM under the key-encryption key, a file outside the database. Every encrypted value records the ID of its key (the first 16 hex digits of the key's SHA-256).

- `oea.security.kek.current` (`OEA_SECURITY_KEK_CURRENT`) names the key file; the Compose setup mounts the file named by `OEA_KEK_FILE` as the secret `/run/secrets/oea_kek`.
- Keep the key out of database backups and store it separately. A database dump without the key does not reveal the secrets – and without the key, the secrets are lost.

### Rotate the key

1. Create a new key file and configure it as `oea.security.kek.current`; configure the old one as `oea.security.kek.previous` (a list of files, `OEA_SECURITY_KEK_PREVIOUS`).
2. Re-encrypt all stored secrets with the runtime credentials:

   ```bash
   java -jar oea-backend.jar secrets reencrypt \
     --spring.datasource.url=jdbc:postgresql://db.example.org:5432/oea \
     --spring.datasource.username=oea_app \
     --spring.datasource.password="$OEA_APP_PASSWORD" \
     --oea.security.kek.current=/run/secrets/oea_kek \
     --oea.security.kek.previous=/run/secrets/oea_kek_previous
   ```

3. Remove the old key from the configuration and destroy it.

The command re-encrypts in one transaction and prints `OK <n> secret(s) re-encrypted with key <key ID>`. Exit codes: `0` done, `1` a value could not be decrypted with the configured keys (nothing was changed), `2` usage or connection error.

## Log format {#log-format}

The backend and its commands log to the console as plain text. For a log platform or SIEM, switch to JSON lines in one of the formats `ecs` (Elastic Common Schema), `logstash` or `gelf` (Graylog):

```bash
# .env of the Compose installation
OEA_LOG_FORMAT=ecs
```

Without Compose, set the environment variable `LOGGING_STRUCTURED_FORMAT_CONSOLE` (or the option `--logging.structured.format.console=ecs`). An empty value keeps plain text.

- Every JSON line carries timestamp, level, logger and message. An entry written during an HTTP request also has the field `requestId`, the same value as the `X-Request-Id` header and the request ID of the audit record.
- No entry contains passwords, setup tokens, second-factor secrets or session tokens, in any format.
- Checkpoint entries keep their message and get the fields `event`, `chainSeq`, `recordHash` and `at`. GELF prefixes them with `_`. `audit verify --checkpoints` reads checkpoints from plain text and JSON lines, also mixed in one file (see below).

## Password login of persons {#password-login-of-persons}

Persons log in through your identity provider. On an instance without an identity provider you can let persons log in with their e-mail address and a password instead. This is off on a new instance; permit it explicitly:

```bash
# environment of the backend (or --oea.auth.person-password.enabled=true)
OEA_AUTH_PERSONPASSWORD_ENABLED=true
# optional: how long an initial password stays valid (1 hour to 30 days, default 72 hours)
OEA_AUTH_PERSONPASSWORD_TEMPORARYPASSWORDVALIDITY=PT72H
```

- An administrator then sets an initial password per person (see [Identity providers and persons](./identity-providers#set-an-initial-password)); the person changes it at the first login.
- Administrators can switch password login of persons off and on again with `personPasswordLogin` of the [password policy](./identity-providers#password-policy). It works only while your setting permits it and their switch is on; without your permission they cannot switch it on.
- Roles with administrative permissions apply to a person only after a login with password and the code of an authenticator app (see [Set up a second factor](../usage/login#set-up-a-second-factor)); after a password login alone they do not apply.
- While password login of persons is not possible, a password login of a person fails like a wrong password, and setting or resetting a password is refused with `409`.

### Enforcement of a second factor {#second-factor-enforcement}

Administrators switch the [second-factor policy](./identity-providers#second-factor-policy) on and off. To make it mandatory regardless of them, force it:

```bash
# a second factor is enforced at every password login of a person (default false)
OEA_AUTH_SECONDFACTOR_ENFORCED=true
```

The exempt roles stay with the administrators.

### Passkeys {#passkeys}

Persons with a password can also set up passkeys. A passkey is bound to the domain of the instance (the relying party). OEA takes it from the public URL (`OEA_WEB_PUBLIC_URL`, see [Installation](../setup/installation)); without a public URL, passkeys are unavailable and their operations answer `409`. Set it explicitly only if the web portal runs on several origins:

```bash
# relying party ID: the host of the public URL or a parent domain of every origin
OEA_AUTH_PASSKEY_RPID=example.org
# allowed origins, comma-separated (default: the public URL without path)
OEA_AUTH_PASSKEY_ORIGINS=https://oea.example.org,https://ea.example.org
# name shown by the authenticator (default OEA)
OEA_AUTH_PASSKEY_RPNAME=OEA
```

- A relying party ID that does not cover every origin stops the backend at startup.
- Changing the domain later makes every registered passkey unusable; persons then log in with their password and their authenticator app, or an administrator resets their second factors.

### Floors of the password policy {#password-policy-floors}

Administrators maintain the [password policy](./identity-providers#password-policy) through the API. You set the floors it cannot go below; each rule in force is the stricter of your floor and the administrators' value. The defaults switch every rule off:

```bash
# minimum length, 12 to 128 (default 12)
OEA_AUTH_PASSWORDPOLICY_MINLENGTH=12
# character types required in any case (default false)
OEA_AUTH_PASSWORDPOLICY_REQUIREUPPERCASE=false
OEA_AUTH_PASSWORDPOLICY_REQUIRELOWERCASE=false
OEA_AUTH_PASSWORDPOLICY_REQUIREDIGITS=false
OEA_AUTH_PASSWORDPOLICY_REQUIRESPECIALCHARS=false
# longest expiry in days, 0 = no ceiling (default 0)
OEA_AUTH_PASSWORDPOLICY_MAXEXPIRYDAYS=0
# smallest history, 0 to 24 (default 0)
OEA_AUTH_PASSWORDPOLICY_MINHISTORYCOUNT=0
```

- A value outside its range stops the backend at startup.
- A raised floor applies at once, also to a policy the administrators stored before.
- The floors apply to the passwords of persons and of the local system admin account.

## Verify the audit trail

Every audit record is a link of a hash chain computed by the database. The verification command recomputes the chain with the read-only role `oea_auditor`:

```bash
java -jar oea-backend.jar audit verify \
  --oea.audit-verify.url=jdbc:postgresql://db.example.org:5432/oea \
  --oea.audit-verify.password="$OEA_AUDITOR_PASSWORD" \
  --checkpoints=/var/log/oea/backend.log
```

With Compose: `docker compose run --rm migrate audit verify --oea.audit-verify.url=jdbc:postgresql://postgres:5432/oea --oea.audit-verify.password=…`. The environment variables `OEA_AUDITVERIFY_URL` and `OEA_AUDITVERIFY_PASSWORD` work as well.

| Exit code | Meaning |
|---|---|
| `0` | chain intact (`OK <links> links, head <chain_seq> <hash>`) |
| `1` | first broken link, missing link, or a database head behind or diverging from a checkpoint (`BROKEN …`) |
| `2` | usage or connection error |

**Checkpoints**: the backend appends a checkpoint every 60 minutes (`oea.audit.checkpoint-interval`, 5–1440 minutes) and at orderly shutdown, and writes a log line `event=audit.checkpoint chainSeq=… recordHash=… at=…` (in JSON logs a JSON object with these fields, see [Log format](#log-format)). Keep the backend log outside the database host (log shipper, journald, SIEM): checkpoints are only as trustworthy as the place they are stored. Pass the log file with `--checkpoints=` to detect deleted newest records or a restored older backup.

## Deactivate the local system admin account {#deactivate-the-local-system-admin-account}

After the hand-over of system administration to an IdP group (see [Roles and permissions](./roles-and-permissions#hand-over-system-administration-to-an-idp-group)), deactivate the local account of the bootstrapping so that no additional password access stays open. You need the permission `auth-config:manage`:

```bash
curl "${auth[@]}" "$OEA/admin/system-admin-account"                     # state of the account
curl "${auth[@]}" -X POST "$OEA/admin/system-admin-account/deactivate"
```

- The account's sessions end at once and its password no longer works; logins through the identity provider are not affected.
- OEA refuses the deactivation with `409` and problem type `urn:oea:problem:last-admin-path` as long as no other working path to system administration exists: an active mapping of an active provider to `system-admin` that has granted the role in at least one login (`lastMatchedAt`), or a `system-admin` assignment without `validTo` of an active person (see [Assign roles to a single person](./roles-and-permissions#assign-roles-to-a-single-person)). If you rely on a group, have a member of the group log in shortly before you deactivate the account.
- Both the deactivation and a refused attempt are recorded in the audit trail (`system-admin-account.deactivated`).
- The same check refuses revoking the last `system-admin` assignment and deleting, deactivating or retargeting the last working mapping to `system-admin`. If you still lock yourself out, for example because the members of the group left it at the provider, use the recovery below.

## Recover administrator access {#recover-administrator-access}

If nobody can administer the instance any more – the password of the local account is lost or the account is deactivated, and no IdP group mapping to `system-admin` works – restore the local account with a command inside the backend container. It uses the configuration of the container (database and key-encryption key):

```bash
docker compose exec -it backend java -jar /app/oea-backend.jar admin recover
```

1. Enter the new password twice (12 to 128 characters). It is read from the terminal and never appears on the command line.
2. If the setup phase has ended, the command also replaces the second factor: it revokes the old authenticator, shows a new secret and an `otpauth` URI once, and asks for a code from your authenticator app (three attempts).
3. The account is active again, all its sessions are revoked, and its login throttling is cleared. Log in with the new password and, after the setup phase, a code of the new authenticator.

Without a terminal (`docker compose exec -T`) the command reads one answer per line from standard input. Exit codes: `0` recovered, `1` refused (instance not bootstrapped, wrong codes or input ended – nothing was changed), `2` usage or connection error.

The recovery is recorded in the audit trail as `system.admin-recovered` with source `CLI`. Neither the password nor the secret is recorded or logged. The secret stays in your terminal's scrollback, so clear the terminal afterwards. Anyone who can run commands in the backend container can take over the instance this way – the database password and the key in the same container allow that anyway. Treat access to the container as administrator access.

## Upgrade PostgreSQL to a new major version

The Compose setup uses PostgreSQL 18. Data directories are not compatible across major versions, so a major upgrade needs a dump and restore or `pg_upgrade`:

1. Stop the backend, keep PostgreSQL running, and dump the database as superuser: `pg_dumpall` (roles and data) or `pg_dump -Fc oea` plus the provisioning script for the roles.
2. Start the new PostgreSQL version on an empty volume; the provisioning script creates the roles again.
3. Restore the dump, then run the migration step and start the backend.

Since PostgreSQL 18, the official image keeps its data in `/var/lib/postgresql/18/docker`; the Compose volume is therefore mounted at `/var/lib/postgresql`. The minimum supported version stays PostgreSQL 15; the backend checks it at startup.
