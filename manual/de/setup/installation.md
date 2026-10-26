---
title: Installation
covers:
  screens: []
  stories: []
---

# Installation

OEA besteht aus einer PostgreSQL-Datenbank, einem einmaligen Migrationsschritt und dem Backend. Die Betreiberdatei `compose.yaml` im Repository startet alle drei mit Docker Compose (oder Podman Compose). Wenn du ein eigenes PostgreSQL betreibst, folge [Eigenes PostgreSQL](#eigenes-postgresql).

## Voraussetzungen

- Docker mit Compose v2 oder Podman mit Compose
- PostgreSQL 15 oder neuer, wenn du eine eigene Datenbank verwendest (das Compose-Setup verwendet PostgreSQL 18)
- Eine Datei für den Key-Encryption-Key, lesbar für den Container-Benutzer

## Mit Docker Compose installieren

1. Kopiere `.env.example` nach `.env` und fülle **jeden** Wert aus. Verwende lange Zufallswerte (mindestens 16 Zeichen); bekannte Entwicklungswerte werden abgelehnt. Committe `.env` nie.

   | Variable | Zweck |
   |---|---|
   | `POSTGRES_PASSWORD` | PostgreSQL-Superuser, nur vom Provisioning-Skript beim ersten Start verwendet |
   | `OEA_OWNER_PASSWORD`, `OEA_REPORTING_OWNER_PASSWORD` | Owner-Rollen des Migrationsschritts |
   | `OEA_APP_PASSWORD` | Laufzeitrolle des Backends |
   | `OEA_AUDIT_CLEANUP_PASSWORD`, `OEA_AUDITOR_PASSWORD`, `OEA_ANALYTICS_PASSWORD` | Rollen für Audit-Aufbewahrung, Audit-Prüfung und Analytics |
   | `OEA_KEK_FILE` | Pfad der Datei mit dem Key-Encryption-Key |
   | `OEA_HTTP_BIND`, `OEA_HTTP_PORT` | optional: veröffentlichte Adresse, Standard `127.0.0.1:8080` |

2. Erzeuge den Key-Encryption-Key. Er verschlüsselt gespeicherte Geheimnisse (zweite Faktoren, Client-Secrets von Identity-Providern). Bewahre ihn außerhalb der Datenbank-Backups auf:

   ```bash
   head -c 32 /dev/urandom | base64 > /secure/path/oea-kek.key
   chmod 0444 /secure/path/oea-kek.key
   ```

3. Starte OEA:

   ```bash
   docker compose up -d
   ```

   Beim ersten Start führt PostgreSQL das Provisioning-Skript aus und legt die Datenbankrollen an. Danach erzeugt der Migrationsschritt das Schema, und das Backend startet.

4. Prüfe, ob die Instanz läuft:

   ```bash
   curl http://127.0.0.1:8080/api/v1/health
   ```

   Die Antwort enthält nur den Gesamtstatus `UP` oder `DOWN`.

Weiter geht es mit dem [ersten Administrator](./first-administrator).

::: warning Öffentlicher Zugriff
Mach die Instanz nicht aus dem Internet erreichbar, bevor die Setup-Phase beendet ist (siehe [Erster Administrator](./first-administrator)). Stell einen Reverse Proxy mit TLS vor OEA und setze die öffentliche Adresse mit `OEA_WEB_PUBLIC_URL` (`https`), bevor du einen Identity-Provider anbindest.
:::

## Eigenes PostgreSQL

Wenn du eine verwaltete Cloud-Datenbank oder einen bestehenden Cluster verwendest, führst du Provisioning und Migration selbst aus.

### Provisioning (einmalig)

Das Skript `deploy/postgres/initdb/10-oea-roles.sh` legt die sechs Datenbankrollen an, setzt ihre Passwörter, beschränkt `CONNECT` auf die Datenbank auf die OEA-Rollen und legt die Schemas mit ihren Ownern an. Du brauchst eine leere Datenbank (Standardname `oea`), eine Rolle, die Rollen anlegen darf, und `psql` 15 oder neuer:

```bash
export PGHOST=db.example.org PGPORT=5432 PGUSER=postgres PGPASSWORD=...
export OEA_DB_NAME=oea
export OEA_OWNER_PASSWORD=... OEA_REPORTING_OWNER_PASSWORD=... OEA_APP_PASSWORD=...
export OEA_AUDIT_CLEANUP_PASSWORD=... OEA_AUDITOR_PASSWORD=... OEA_ANALYTICS_PASSWORD=...
deploy/postgres/initdb/10-oea-roles.sh
```

- Das Skript liest Passwörter nur aus der Umgebung; sie erscheinen nie auf der Kommandozeile oder in der Ausgabe.
- Fehlt ein Passwort, bricht das Skript ab und nennt die fehlenden Variablen.
- Du kannst das Skript erneut ausführen: Es setzt Rollenattribute, Passwörter und Schema-Owner zurück, du kannst damit also auch Rollenpasswörter rotieren.
- Behandle jedes Passwort als Deployment-Geheimnis: Bewahre es in einem Secret Store auf, nicht in deiner Shell-History.

Die Rollen beschreibt [Betrieb](../administration/operations#datenbankrollen).

### Migration (vor jedem Start einer neuen Version)

Schemamigrationen laufen in einem einmaligen Schritt, bevor das Backend startet, nie im laufenden Backend:

```bash
java -jar oea-backend.jar migrate \
  --oea.migration.url=jdbc:postgresql://db.example.org:5432/oea \
  --oea.migration.owner.password="$OEA_OWNER_PASSWORD" \
  --oea.migration.reporting-owner.password="$OEA_REPORTING_OWNER_PASSWORD"
```

Du kannst die Einstellungen auch als Umgebungsvariablen übergeben (`OEA_MIGRATION_URL`, `OEA_MIGRATION_OWNER_PASSWORD`, `OEA_MIGRATION_REPORTINGOWNER_PASSWORD`); ohne `oea.migration.url` wird `spring.datasource.url` verwendet. Der Schritt endet bei Erfolg mit Code `0` und bei jedem Fehler mit `1`.

| Migrationsset | Läuft als | Schema-History-Tabelle |
|---|---|---|
| `db/migration` | `oea_owner` | `public.flyway_schema_history` |
| `db/migration-audit` | `oea_owner` | `audit.flyway_schema_history` |
| `db/migration-reporting` | `oea_reporting_owner` | `reporting.flyway_schema_history` |

### Backend starten

Das Backend verbindet sich mit der Laufzeitrolle `oea_app`:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://db.example.org:5432/oea \
SPRING_DATASOURCE_USERNAME=oea_app \
SPRING_DATASOURCE_PASSWORD="$OEA_APP_PASSWORD" \
OEA_SECURITY_KEK_CURRENT=/run/secrets/oea_kek \
OEA_WEB_PUBLIC_URL=https://oea.example.org \
java -jar oea-backend.jar
```

- `OEA_WEB_PUBLIC_URL` (`oea.web.public-url`) ist die Adresse, unter der die Benutzer OEA erreichen. Du brauchst sie, sobald ein Identity-Provider aktiviert ist; sie muss `https` verwenden und darf keine Loopback-Adresse sein.
- Ist der Migrationsschritt für die aktuelle Version nicht gelaufen, startet das Backend nicht und nennt den Migrationsschritt.
- Das Backend startet nicht, wenn die Schlüsseldatei fehlt, nicht lesbar ist, nicht genau 256 Bit hat oder der Entwicklungsschlüssel des Repositorys ist.
