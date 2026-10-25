---
title: Administration
covers:
  screens: []
  stories: []
---

# Administration

Die Administration hat in dieser Version keine Bildschirme; du administrierst die Instanz über die [API](../api/). Die Beispiele in diesem Kapitel verwenden `curl` und `jq` und halten Passwörter und Geheimnisse von der Kommandozeile fern.

- [Identity-Provider und Personen](./identity-providers): einen OpenID-Connect-Provider anbinden und Personen an ihre Identitäten binden
- [Rollen und Berechtigungen](./roles-and-permissions): die eingebauten Rollen und wie du sie über IdP-Gruppen vergibst
- [Metamodell-Pakete](./metamodel-packages): die Starterkonfiguration oder ein eigenes Metamodell importieren
- [Betrieb](./operations): Datenbankrollen, Größenlimits für Requests, Schlüsselrotation, Prüfung des Audit-Trails und PostgreSQL-Upgrades

## Für die Administration über die API anmelden {#api-login}

Melde dich mit dem Systemadmin-Konto an. Die Sitzung ist das Cookie `__Host-OEA_SESSION`; das CSRF-Token steht im Antwort-Body und muss jeden ändernden Request begleiten:

```bash
OEA=https://oea.example.org/api/v1
read -rs OEA_ADMIN_PASSWORD

jq -n --arg p "$OEA_ADMIN_PASSWORD" '{loginName: "admin", password: $p}' |
  curl -sS -D login.headers -o login.json -H 'Content-Type: application/json' -d @- "$OEA/auth/local/login"
SESSION=$(sed -n 's/^set-cookie: __Host-OEA_SESSION=\([^;]*\).*/\1/Ip' login.headers)
CSRF=$(jq -r .csrfToken login.json)
auth=(-sS -H "Cookie: __Host-OEA_SESSION=$SESSION" -H "X-XSRF-TOKEN: $CSRF" -H 'Content-Type: application/json')
```

Nach der Setup-Phase antwortet der Passwortschritt mit `401`, dem Problem-Typ `urn:oea:problem:second-factor-required` und einer `loginTransaction`; schließe die Anmeldung mit `POST /auth/local/login/totp` und `{"loginTransaction": "…", "code": "…"}` ab.

Das Ändern eines Providers und das Einrichten eines zweiten Faktors brauchen eine Anmeldung innerhalb der letzten 5 Minuten; melde dich erneut an, wenn die Antwort `401 reauthentication-required` lautet. Melde dich am Ende ab:

```bash
curl "${auth[@]}" -X POST "$OEA/auth/logout"
```
