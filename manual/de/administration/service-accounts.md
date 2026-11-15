---
title: Service-Accounts
covers:
  screens: [SCR-041]
  stories: [US-142, US-149]
---

# Service-Accounts

Externe Systeme wie Scanner, CI-Pipelines oder ein CMDB-Importer nutzen die API, ohne dass sich eine Person anmeldet. Jedes von ihnen bekommt einen **Service-Account** mit einer Client-ID und einem Client-Secret. Alles, was ein Service-Account ändert, steht im Audit-Log unter seiner Client-ID. So lassen sich Änderungen von Maschinen immer von Änderungen durch Personen unterscheiden.

Service-Accounts verwaltest du auf der Seite **Service-Accounts** (Link in der Kopfzeile). Du brauchst die Berechtigung `service-account:manage`, die die Rolle `system-admin` hat. Um Rollen für einen Service-Account auszuwählen, brauchst du zusätzlich `role:assign`.

Das externe System tauscht Client-ID und Secret gegen ein Access-Token mit 15 Minuten Gültigkeit und ruft damit die API auf; siehe [API-Zugriff ohne Browser](../api/access-without-browser). Die Spalte **Letzte Nutzung** zeigt, wann ein Service-Account zuletzt ein Token geholt hat.

## Service-Account anlegen

1. Wähle **Neuer Service-Account**.
2. Gib einen Namen ein (1 bis 100 Zeichen, eindeutig unter den Service-Accounts, unabhängig von Groß- und Kleinschreibung) und optional eine Beschreibung, etwa das System, dem der Account dient.
3. Wähle, wie viele Tage das Secret gültig bleibt: 90 Tage, wenn du nichts anderes einträgst, höchstens das Maximum deiner Instanz (365 Tage, sofern nicht [anders konfiguriert](#maximum-secret-validity)).
4. Wähle die Rollen. Angeboten werden nur Rollen, die Service-Accounts haben dürfen; `integration-writer` ist vorausgewählt.
5. Wähle **Service-Account anlegen**.

OEA zeigt dann das **Client-Secret ein einziges Mal**. Kopiere es und speichere es sofort, zum Beispiel als Secret deines CI/CD-Systems. Nachdem du den Dialog geschlossen hast, kann es niemand mehr anzeigen: OEA speichert nur einen Hash davon. Ist das Secret verloren, [rotiere](#rotate-the-secret) es.

Das Secret beginnt mit `oea_cs_`. So erkennen Secret-Scanner es, falls es einmal in einem Repository oder einem Log landet.

## Rollen eines Service-Accounts

Ein Service-Account hat nur Rollen, die als Service-Accounts zuweisbar markiert sind. Von den eingebauten Rollen sind das:

| Rolle | Erlaubt dem externen System |
|---|---|
| `integration-writer` | Entitäten lesen und schreiben |
| `viewer` | Entitäten lesen |
| `auditor` | das Audit-Log lesen |

Eine Rolle mit einer administrativen Berechtigung ist nie einem Service-Account zuweisbar. So lässt sich ein geleaktes Secret nie zur Administration von OEA nutzen. Eine Anfrage mit einer solchen Rolle wird mit `422` abgelehnt, die die Rolle nennt, und der Versuch steht im Audit-Log. Eigene Rollen können als zuweisbar markiert werden, wenn sie keine administrative Berechtigung enthalten (siehe [Rollen und Berechtigungen](./roles-and-permissions)).

Um die Rollen zu ändern, wähle **Rollen bearbeiten** in der Zeile des Service-Accounts. Jede hinzugefügte und entfernte Rolle steht wie jede Rollenzuweisung im Audit-Log.

## Ablauf des Secrets

Jedes Secret läuft ab. Die Spalte **Secret läuft ab** zeigt wann; ein Secret, das innerhalb von 14 Tagen abläuft, ist mit **Läuft bald ab** markiert. Rotiere es rechtzeitig und gib das neue Secret an das externe System weiter.

## Secret rotieren {#rotate-the-secret}

Wähle **Secret rotieren** und bestätige. OEA erzeugt ein neues Secret, zeigt es einmal an und macht das bisherige **sofort** ungültig, zusammen mit allen damit geholten Access-Tokens; die Client-ID bleibt gleich. Rotiere das Secret, wenn es bald abläuft, wenn es geleakt sein könnte oder wenn eine Person geht, die es kannte.

## Service-Account deaktivieren

Wähle **Deaktivieren** und bestätige. Der Service-Account wird inaktiv, sein Secret wird widerrufen und seine Rollen werden entfernt. **Eine Deaktivierung ist endgültig**: Ein deaktivierter Service-Account lässt sich nicht reaktivieren, und jede weitere Änderung wird mit `409` abgelehnt. Lege für eine neue Integration einen neuen Service-Account an; seine neue Client-ID hält das Audit-Log eindeutig.

## Für heikle Aktionen erneut anmelden

Einen Service-Account anlegen, sein Secret rotieren und ihn deaktivieren brauchen eine Anmeldung innerhalb der letzten 5 Minuten. Sonst bittet OEA dich, dich erneut anzumelden (`401 reauthentication-required`). Das Ändern der Rollen braucht keine neue Anmeldung.

In der Setup-Phase beendet der erste Service-Account die Setup-Phase wie die erste Rollenzuweisung. Er braucht einen zweiten Faktor (siehe [Erster Administrator](../setup/first-administrator#setup-phase)).

## Über die API

Die Seite nutzt die Operationen unter `/admin/service-accounts`; mit einer [Sitzung der API](./index#api-login):

```bash
# anlegen; die Antwort enthält das Secret einmal
curl "${auth[@]}" -d '{"name": "ci-scanner", "roles": ["integration-writer"], "secretValidityDays": 30}' \
  "$OEA/admin/service-accounts" > ci-scanner.json
CLIENT_ID=$(jq -r .id ci-scanner.json)
# das Secret aus ci-scanner.json im Secret-Speicher ablegen, dann die Datei löschen

# auflisten; secretExpiresSoon markiert Secrets, die innerhalb von 14 Tagen ablaufen
curl "${auth[@]}" "$OEA/admin/service-accounts?active=true" | jq '.[] | {name, roles, secretExpiresAt, secretExpiresSoon}'

# rotieren; die Antwort enthält das neue Secret einmal
curl "${auth[@]}" -X POST -d '{}' "$OEA/admin/service-accounts/$CLIENT_ID/credentials/rotate" > ci-scanner.json

# endgültig deaktivieren
curl "${auth[@]}" -X DELETE "$OEA/admin/service-accounts/$CLIENT_ID"
```

## Maximale Gültigkeit des Secrets {#maximum-secret-validity}

Die längste Gültigkeit, die eine Administratorin oder ein Administrator wählen kann, ist eine Einstellung des Backends, standardmäßig 365 Tage. Setze sie zwischen 1 und 3650 Tagen; mit einem anderen Wert startet das Backend nicht:

```bash
# Umgebung des Backends (oder --oea.service-accounts.secret-max-validity-days=180)
OEA_SERVICEACCOUNTS_SECRETMAXVALIDITYDAYS=180
```

Liegt das Maximum unter 90 Tagen, bekommt ein Secret ohne gewählte Gültigkeit das Maximum.

## Bereinigung abgelaufener Tokens {#token-cleanup}

OEA löscht Access-Tokens einen Tag nach ihrem Ablauf; ihre Ausstellung bleibt im Audit-Log als `token.issued` erhalten. Der Job läuft stündlich; ein anderes Intervall setzt du als ISO-8601-Dauer:

```bash
# Umgebung des Backends (oder --oea.access-tokens.cleanup-interval=PT30M)
OEA_ACCESSTOKENS_CLEANUPINTERVAL=PT30M
```
