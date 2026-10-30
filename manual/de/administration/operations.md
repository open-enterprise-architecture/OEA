---
title: Betrieb
covers:
  screens: []
  stories: [US-011, US-019, US-022, US-027, US-031]
---

# Betrieb

## Datenbankrollen

| Rolle | Verwendet von | Besitzt |
|---|---|---|
| `oea_owner` | Migrationsschritt: `db/migration`, `db/migration-audit` | Schemas `public`, `audit` und ihre Objekte |
| `oea_reporting_owner` | Migrationsschritt: `db/migration-reporting` | Schemas `reporting`, `reporting_ctx` und ihre Objekte |
| `oea_app` | Backend zur Laufzeit | nichts |
| `oea_audit_cleanup` | Job für die Audit-Aufbewahrung | nichts |
| `oea_auditor` | Audit-Prüfung, externe Prüfer | nichts |
| `oea_analytics` | direkte Analytics-Verbindungen | nichts |

Keine Rolle ist `SUPERUSER`, `CREATEROLE`, `CREATEDB` oder `BYPASSRLS`, und keine OEA-Rolle ist Mitglied einer anderen OEA-Rolle. Die Laufzeitrolle `oea_app` hat auf den Audit-Tabellen nur `INSERT` und `SELECT`; andernfalls startet das Backend nicht. Rollenpasswörter rotierst du, indem du das Provisioning-Skript erneut ausführst (siehe [Installation](../setup/installation#provisioning-einmalig)).

## Größenlimits für Requests {#request-size-limits}

Request-Bodys werden begrenzt, bevor sie verarbeitet werden. Ein größerer Body wird mit `413` und dem Problem-Typ `urn:oea:problem:request-too-large` beantwortet (`package-too-large` bei Metamodell-Paketen):

| Einstellung | Standard | Gilt für |
|---|---|---|
| `oea.web.request-size.default` (`OEA_WEB_REQUESTSIZE_DEFAULT`) | `1MB` | jede Operation mit Request-Body |
| `oea.web.request-size.entity-batch` (`OEA_WEB_REQUESTSIZE_ENTITYBATCH`) | `10MB` | `POST /entities/batch` |
| `oea.metamodel.import.max-size` (`OEA_METAMODEL_IMPORT_MAXSIZE`) | `10MB` | `POST /admin/metamodel/import` |

Ein Reverse Proxy vor OEA sollte mindestens den größten dieser Werte zulassen.

## Rate Limits {#rate-limits}

Jede angemeldete Person und jedes Systemadministrator-Konto darf pro Minute eine begrenzte Zahl von Requests senden. Die Minute beginnt mit dem ersten Request; ein Request über dem Limit wird mit `429`, dem Problem-Typ `urn:oea:problem:too-many-requests` und dem Header `Retry-After` beantwortet, der die Sekunden bis zum nächsten angenommenen Request nennt. Von einem abgewiesenen Request wird nichts verarbeitet, und andere Benutzer sind nicht betroffen.

| Einstellung | Standard | Gilt für |
|---|---|---|
| `oea.web.rate-limit.requests-per-minute` (`OEA_WEB_RATELIMIT_REQUESTSPERMINUTE`) | `600` | jeden Request eines angemeldeten Principals |
| `oea.web.rate-limit.entity-batch-per-minute` (`OEA_WEB_RATELIMIT_ENTITYBATCHPERMINUTE`) | `10` | `POST /entities/batch`, zusätzlich zum Request-Limit |

Beide Werte müssen mindestens `1` sein, sonst startet das Backend nicht. Die erste Abweisung eines Principals innerhalb einer Minute wird im Audit-Trail als `auth.throttled` mit dem Scope `principal` festgehalten.

Die Zähler liegen im Speicher des Backends. Betreibst du mehrere Backend-Instanzen hinter einem Load Balancer, zählt jede Instanz für sich, sodass ein Principal bis zum Limit mal der Zahl der Instanzen senden kann. Nach einem Neustart beginnt die Zählung von vorn.

Logins und die Setup-Seite werden getrennt davon pro Login-Name und Absenderadresse gedrosselt (100 Fehlversuche innerhalb von 10 Minuten). Ein Reverse Proxy mit eigenem Rate Limiting ist eine sinnvolle zusätzliche Schicht, wenn die Instanz aus dem Internet erreichbar ist; er ersetzt diese Limits nicht.

## Key-Encryption-Key

Gespeicherte Geheimnisse – TOTP-Secrets und Client-Secrets von Identity-Providern – werden mit AES-256-GCM unter dem Key-Encryption-Key verschlüsselt, einer Datei außerhalb der Datenbank. Jeder verschlüsselte Wert speichert die ID seines Schlüssels (die ersten 16 Hex-Ziffern des SHA-256 des Schlüssels).

- `oea.security.kek.current` (`OEA_SECURITY_KEK_CURRENT`) nennt die Schlüsseldatei; das Compose-Setup bindet die mit `OEA_KEK_FILE` genannte Datei als Secret `/run/secrets/oea_kek` ein.
- Halte den Schlüssel aus Datenbank-Backups heraus und bewahre ihn getrennt auf. Ein Datenbank-Dump ohne den Schlüssel gibt die Geheimnisse nicht preis – und ohne den Schlüssel sind die Geheimnisse verloren.

### Schlüssel rotieren

1. Erzeuge eine neue Schlüsseldatei und konfiguriere sie als `oea.security.kek.current`; konfiguriere die alte als `oea.security.kek.previous` (eine Liste von Dateien, `OEA_SECURITY_KEK_PREVIOUS`).
2. Verschlüssle alle gespeicherten Geheimnisse mit den Laufzeit-Zugangsdaten neu:

   ```bash
   java -jar oea-backend.jar secrets reencrypt \
     --spring.datasource.url=jdbc:postgresql://db.example.org:5432/oea \
     --spring.datasource.username=oea_app \
     --spring.datasource.password="$OEA_APP_PASSWORD" \
     --oea.security.kek.current=/run/secrets/oea_kek \
     --oea.security.kek.previous=/run/secrets/oea_kek_previous
   ```

3. Entferne den alten Schlüssel aus der Konfiguration und vernichte ihn.

Der Befehl verschlüsselt in einer Transaktion neu und gibt `OK <n> secret(s) re-encrypted with key <key ID>` aus. Exit-Codes: `0` erledigt, `1` ein Wert konnte mit den konfigurierten Schlüsseln nicht entschlüsselt werden (nichts wurde geändert), `2` Aufruf- oder Verbindungsfehler.

## Log-Format {#log-format}

Das Backend und seine Befehle loggen als Klartext auf die Konsole. Für eine Log-Plattform oder ein SIEM stellst du auf JSON-Zeilen in einem der Formate `ecs` (Elastic Common Schema), `logstash` oder `gelf` (Graylog) um:

```bash
# .env der Compose-Installation
OEA_LOG_FORMAT=ecs
```

Ohne Compose setzt du die Umgebungsvariable `LOGGING_STRUCTURED_FORMAT_CONSOLE` (oder die Option `--logging.structured.format.console=ecs`). Ein leerer Wert lässt den Klartext.

- Jede JSON-Zeile enthält Zeitstempel, Level, Logger und Nachricht. Ein Eintrag, der während eines HTTP-Requests geschrieben wird, hat zusätzlich das Feld `requestId` – denselben Wert wie der Header `X-Request-Id` und die Request-ID des Audit-Eintrags.
- Kein Eintrag enthält Passwörter, Setup-Tokens, Geheimnisse zweiter Faktoren oder Sitzungs-Tokens, in keinem Format.
- Checkpoint-Einträge behalten ihre Nachricht und bekommen die Felder `event`, `chainSeq`, `recordHash` und `at`. GELF stellt ihnen `_` voran. `audit verify --checkpoints` liest Checkpoints aus Klartext- und JSON-Zeilen, auch gemischt in einer Datei (siehe unten).

## Passwort-Login von Personen {#password-login-of-persons}

Personen melden sich über deinen Identity-Provider an. Auf einer Instanz ohne Identity-Provider kannst du Personen stattdessen mit ihrer E-Mail-Adresse und einem Passwort anmelden lassen. Das ist auf einer neuen Instanz aus; du erlaubst es ausdrücklich:

```bash
# Umgebung des Backends (oder --oea.auth.person-password.enabled=true)
OEA_AUTH_PERSONPASSWORD_ENABLED=true
# optional: wie lange ein Initialpasswort gilt (1 Stunde bis 30 Tage, Standard 72 Stunden)
OEA_AUTH_PERSONPASSWORD_TEMPORARYPASSWORDVALIDITY=PT72H
```

- Eine Administratorin oder ein Administrator setzt dann pro Person ein Initialpasswort (siehe [Identity-Provider und Personen](./identity-providers#set-an-initial-password)); die Person ändert es bei der ersten Anmeldung.
- Administratorinnen und Administratoren können den Passwort-Login von Personen mit `personPasswordLogin` der [Passwort-Richtlinie](./identity-providers#password-policy) aus- und wieder einschalten. Er funktioniert nur, solange deine Einstellung ihn erlaubt und ihr Schalter an ist; ohne deine Erlaubnis können sie ihn nicht einschalten.
- Rollen mit administrativen Berechtigungen gelten für eine Person erst nach einer Anmeldung mit Passwort und dem Code einer Authenticator-App (siehe [Zweiten Faktor einrichten](../usage/login#set-up-a-second-factor)); nach einer reinen Passwort-Anmeldung gelten sie nicht.
- Solange der Passwort-Login von Personen nicht möglich ist, scheitert er wie ein falsches Passwort, und das Setzen oder Zurücksetzen eines Passworts wird mit `409` abgelehnt.

### Pflicht zum zweiten Faktor {#second-factor-enforcement}

Administratorinnen und Administratoren schalten die [Richtlinie für zweite Faktoren](./identity-providers#second-factor-policy) ein und aus. Um sie unabhängig davon verbindlich zu machen, erzwingst du sie:

```bash
# bei jeder Passwort-Anmeldung einer Person ist ein zweiter Faktor Pflicht (Standard false)
OEA_AUTH_SECONDFACTOR_ENFORCED=true
```

Die ausgenommenen Rollen bleiben bei der Administration.

### Passkeys {#passkeys}

Personen mit Passwort können auch Passkeys einrichten. Ein Passkey ist an die Domain der Instanz gebunden (die Relying Party). OEA übernimmt sie aus der öffentlichen URL (`OEA_WEB_PUBLIC_URL`, siehe [Installation](../setup/installation)); ohne öffentliche URL sind Passkeys nicht verfügbar, und ihre Operationen antworten mit `409`. Setze sie nur ausdrücklich, wenn das Webportal unter mehreren Origins läuft:

```bash
# Relying-Party-ID: der Host der öffentlichen URL oder eine übergeordnete Domain jedes Origins
OEA_AUTH_PASSKEY_RPID=example.org
# erlaubte Origins, durch Komma getrennt (Standard: die öffentliche URL ohne Pfad)
OEA_AUTH_PASSKEY_ORIGINS=https://oea.example.org,https://ea.example.org
# Name, den der Authenticator anzeigt (Standard OEA)
OEA_AUTH_PASSKEY_RPNAME=OEA
```

- Eine Relying-Party-ID, die nicht jeden Origin abdeckt, verhindert den Start des Backends.
- Ein späterer Wechsel der Domain macht alle eingerichteten Passkeys unbrauchbar; die Personen melden sich dann mit Passwort und Authenticator-App an, oder eine Administratorin bzw. ein Administrator setzt ihre zweiten Faktoren zurück.

### Mindestwerte der Passwort-Richtlinie {#password-policy-floors}

Die [Passwort-Richtlinie](./identity-providers#password-policy) pflegen Administratorinnen und Administratoren über die API. Du legst die Mindestwerte fest, unter die sie nicht gehen kann; jede geltende Regel ist die strengere aus deinem Mindestwert und dem Wert der Administration. Die Standardwerte schalten jede Regel aus:

```bash
# Mindestlänge, 12 bis 128 (Standard 12)
OEA_AUTH_PASSWORDPOLICY_MINLENGTH=12
# Zeichentypen, die in jedem Fall verlangt sind (Standard false)
OEA_AUTH_PASSWORDPOLICY_REQUIREUPPERCASE=false
OEA_AUTH_PASSWORDPOLICY_REQUIRELOWERCASE=false
OEA_AUTH_PASSWORDPOLICY_REQUIREDIGITS=false
OEA_AUTH_PASSWORDPOLICY_REQUIRESPECIALCHARS=false
# längster Ablauf in Tagen, 0 = keine Obergrenze (Standard 0)
OEA_AUTH_PASSWORDPOLICY_MAXEXPIRYDAYS=0
# kleinste Historie, 0 bis 24 (Standard 0)
OEA_AUTH_PASSWORDPOLICY_MINHISTORYCOUNT=0
```

- Ein Wert außerhalb seines Bereichs verhindert den Start des Backends.
- Ein erhöhter Mindestwert gilt sofort, auch für eine Richtlinie, die die Administration vorher gespeichert hat.
- Die Mindestwerte gelten für die Passwörter von Personen und des lokalen Systemadmin-Kontos.

## Audit-Trail prüfen {#verify-the-audit-trail}

Jeder Audit-Eintrag ist ein Glied einer Hash-Kette, die die Datenbank berechnet. Der Prüfbefehl berechnet die Kette mit der nur lesenden Rolle `oea_auditor` neu:

```bash
java -jar oea-backend.jar audit verify \
  --oea.audit-verify.url=jdbc:postgresql://db.example.org:5432/oea \
  --oea.audit-verify.password="$OEA_AUDITOR_PASSWORD" \
  --checkpoints=/var/log/oea/backend.log
```

Mit Compose: `docker compose run --rm migrate audit verify --oea.audit-verify.url=jdbc:postgresql://postgres:5432/oea --oea.audit-verify.password=…`. Die Umgebungsvariablen `OEA_AUDITVERIFY_URL` und `OEA_AUDITVERIFY_PASSWORD` funktionieren ebenfalls.

| Exit-Code | Bedeutung |
|---|---|
| `0` | Kette intakt (`OK <links> links, head <chain_seq> <hash>`) |
| `1` | erstes gebrochenes Glied, fehlendes Glied oder ein Datenbank-Head, der hinter einem Checkpoint liegt oder von ihm abweicht (`BROKEN …`) |
| `2` | Aufruf- oder Verbindungsfehler |

**Checkpoints**: Das Backend hängt alle 60 Minuten (`oea.audit.checkpoint-interval`, 5–1440 Minuten) und beim geordneten Herunterfahren einen Checkpoint an und schreibt eine Logzeile `event=audit.checkpoint chainSeq=… recordHash=… at=…` (in JSON-Logs ein JSON-Objekt mit diesen Feldern, siehe [Log-Format](#log-format)). Bewahre das Backend-Log außerhalb des Datenbank-Hosts auf (Log-Shipper, journald, SIEM): Checkpoints sind nur so vertrauenswürdig wie der Ort, an dem sie liegen. Übergib die Logdatei mit `--checkpoints=`, um gelöschte neueste Einträge oder ein eingespieltes älteres Backup zu erkennen.

## Lokales Systemadmin-Konto deaktivieren {#deactivate-the-local-system-admin-account}

Nach der Übergabe der Systemadministration an eine IdP-Gruppe (siehe [Rollen und Berechtigungen](./roles-and-permissions#hand-over-system-administration-to-an-idp-group)) deaktivierst du das lokale Konto des Bootstrappings, damit kein zusätzlicher Passwortzugang offen bleibt. Du brauchst die Berechtigung `auth-config:manage`:

```bash
curl "${auth[@]}" "$OEA/admin/system-admin-account"                     # Zustand des Kontos
curl "${auth[@]}" -X POST "$OEA/admin/system-admin-account/deactivate"
```

- Die Sitzungen des Kontos enden sofort, und sein Passwort funktioniert nicht mehr; Anmeldungen über den Identity-Provider sind nicht betroffen.
- OEA lehnt die Deaktivierung mit `409` und dem Problemtyp `urn:oea:problem:last-admin-path` ab, solange es keinen anderen funktionierenden Weg zur Systemadministration gibt: eine aktive Zuordnung eines aktiven Providers zu `system-admin`, die die Rolle bei mindestens einer Anmeldung vergeben hat (`lastMatchedAt`), oder eine `system-admin`-Zuweisung ohne `validTo` an eine aktive Person (siehe [Rollen einer einzelnen Person zuweisen](./roles-and-permissions#assign-roles-to-a-single-person)). Verlässt du dich auf eine Gruppe, lass kurz vor der Deaktivierung ein Mitglied der Gruppe sich anmelden.
- Die Deaktivierung und ein abgelehnter Versuch stehen im Audit-Trail (`system-admin-account.deactivated`).
- Dieselbe Prüfung lehnt den Entzug der letzten `system-admin`-Zuweisung und das Löschen, Deaktivieren oder Umhängen der letzten funktionierenden Zuordnung zu `system-admin` ab. Sperrst du dich trotzdem aus, etwa weil die Mitglieder der Gruppe sie beim Provider verlassen haben, hilft die Wiederherstellung unten.

## Administratorzugang wiederherstellen {#recover-administrator-access}

Kann niemand mehr die Instanz administrieren – das Passwort des lokalen Kontos ist verloren oder das Konto ist deaktiviert, und keine IdP-Gruppenzuordnung zu `system-admin` funktioniert –, stellst du das lokale Konto mit einem Befehl im Backend-Container wieder her. Er nutzt die Konfiguration des Containers (Datenbank und Key-Encryption-Key):

```bash
docker compose exec -it backend java -jar /app/oea-backend.jar admin recover
```

1. Gib das neue Passwort zweimal ein (12 bis 128 Zeichen). Es wird vom Terminal gelesen und steht nie in der Befehlszeile.
2. Ist die Setup-Phase beendet, ersetzt der Befehl auch den zweiten Faktor: Er widerruft den alten Authenticator, zeigt einmal ein neues Geheimnis und eine `otpauth`-URI und fragt nach einem Code aus deiner Authenticator-App (drei Versuche).
3. Das Konto ist wieder aktiv, alle seine Sitzungen sind widerrufen, und seine Anmeldedrosselung ist zurückgesetzt. Melde dich mit dem neuen Passwort und, nach der Setup-Phase, einem Code des neuen Authenticators an.

Ohne Terminal (`docker compose exec -T`) liest der Befehl eine Antwort pro Zeile von der Standardeingabe. Exit-Codes: `0` wiederhergestellt, `1` abgelehnt (Instanz nicht gebootstrappt, falsche Codes oder Eingabe beendet – nichts wurde geändert), `2` Aufruf- oder Verbindungsfehler.

Die Wiederherstellung steht im Audit-Trail als `system.admin-recovered` mit der Quelle `CLI`. Weder Passwort noch Geheimnis werden gespeichert oder geloggt. Das Geheimnis bleibt im Verlauf deines Terminals; leere das Terminal danach. Wer Befehle im Backend-Container ausführen kann, kann die Instanz auf diesem Weg übernehmen – das erlauben das Datenbankpasswort und der Schlüssel im selben Container ohnehin. Behandle Zugriff auf den Container wie Administratorzugriff.

## PostgreSQL auf eine neue Hauptversion aktualisieren

Das Compose-Setup verwendet PostgreSQL 18. Datenverzeichnisse sind zwischen Hauptversionen nicht kompatibel, ein Upgrade der Hauptversion braucht daher Dump und Restore oder `pg_upgrade`:

1. Stoppe das Backend, lass PostgreSQL laufen und erstelle als Superuser einen Dump der Datenbank: `pg_dumpall` (Rollen und Daten) oder `pg_dump -Fc oea` plus das Provisioning-Skript für die Rollen.
2. Starte die neue PostgreSQL-Version auf einem leeren Volume; das Provisioning-Skript legt die Rollen erneut an.
3. Spiel den Dump ein, führe dann den Migrationsschritt aus und starte das Backend.

Seit PostgreSQL 18 legt das offizielle Image seine Daten in `/var/lib/postgresql/18/docker` ab; das Compose-Volume ist deshalb unter `/var/lib/postgresql` eingebunden. Die minimal unterstützte Version bleibt PostgreSQL 15; das Backend prüft sie beim Start.
