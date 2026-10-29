---
title: Identity-Provider und Personen
covers:
  screens: []
  stories: [US-004, US-007, US-023, US-148]
---

# Identity-Provider und Personen

Personen melden sich über einen Identity-Provider (IdP) mit OpenID Connect an. Du speicherst den Provider in OEA, legst die Personen an und bindest jede Person an ihre Identität beim Provider. Die gespeicherten, aktivierten Provider sind die einzige Quelle der Anmeldung.

## OEA beim Identity-Provider registrieren

Registriere OEA als vertraulichen Web-Client mit Authorization Code Flow und PKCE (`S256`) und der Redirect-URI `<öffentliche URL>/api/v1/auth/oidc/callback`. Das ID-Token muss die Gruppenmitgliedschaft als unveränderliche Gruppenkennungen im Claim `groups` enthalten und, für privilegierte Rollen, die Authentisierungsmethoden in `amr`:

| Provider | Scopes | Gruppen | Zweiter Faktor |
|---|---|---|---|
| Microsoft Entra ID | `openid profile email offline_access` (`offline_access` liefert das Refresh-Token) | Token-Konfiguration: Groups-Claim mit Gruppen-Objekt-IDs; zu viele Gruppen erzeugen einen Overage-Indikator, den OEA wie keine Gruppen behandelt | `amr` enthält `mfa`, wenn eine Conditional-Access-Richtlinie MFA verlangt hat |
| Authentik | `openid profile email` | Property Mapping, das die Gruppen-UUIDs als `groups` ausgibt | Property Mapping für `amr` (`pwd`, `otp`, `mfa`) |
| Keycloak | `openid profile email` | Mapper, der Gruppenkennungen ausgibt, keine Namen | Authentication Flow mit Methodenreferenzen (`pwd`, `otp`) und einem *Max Age* von mindestens der OEA-Sitzungsdauer (zum Beispiel 43200 s); mit Max Age 0 lässt Keycloak `amr` in späteren Tokens weg |

## Provider speichern

Der Issuer muss `https` verwenden und darf keine Loopback-Adresse sein. Das Client-Secret ist nur schreibbar: OEA gibt es nie zurück.

```bash
read -rs OEA_OIDC_CLIENT_SECRET
jq -n --arg s "$OEA_OIDC_CLIENT_SECRET" '{name: "Corporate IdP", type: "oidc", vendor: "entra-id",
    issuerUrl: "https://login.microsoftonline.com/<tenant-id>/v2.0", clientId: "<client-id>", clientSecret: $s}' |
  curl "${auth[@]}" -d @- "$OEA/admin/auth/providers" | tee provider.json
PROVIDER=$(jq -r .id provider.json)
```

`GET` und `PUT /admin/auth/providers/{id}` lesen und ändern einen Provider; `"enabled": false` deaktiviert ihn. Das Backend startet nicht, wenn ein aktivierter Provider einen Issuer ohne `https` oder auf einer Loopback-Adresse hat.

Erreicht das Backend den Provider unter einer anderen Adresse als die Browser (Split-Horizon-Netz), ordne dem Issuer in der Konfiguration die interne Adresse zu; der Issuer im Discovery-Dokument und in den Tokens wird weiterhin gegen den gespeicherten Issuer geprüft:

```yaml
oea:
  security:
    oidc:
      discovery-locations:
        - issuer: https://login.example.org/realms/oea
          location: https://idp.internal.example.org/realms/oea
```

## Personen anlegen und Identitäten binden

Eine Person bekommt nur dann eine Sitzung, wenn das Paar aus Issuer und Subject (`sub`) an eine Person im Status `invited` oder `active` gebunden ist. Bindungen werden nie aus E-Mail-Adressen abgeleitet. Die erste Anmeldung ändert `invited` in `active`.

```bash
PERSON=$(curl "${auth[@]}" -d '{"name": "Ada Lovelace", "email": "ada@example.org"}' "$OEA/admin/persons" | jq -r .id)
curl "${auth[@]}" -d "{\"authProviderId\": \"$PROVIDER\", \"subject\": \"<subject>\"}" \
  "$OEA/admin/persons/$PERSON/identities"
```

Verwende das unveränderliche Subject des Providers (Entra ID: die Objekt-ID `oid`). Die erste Bindung beendet die Setup-Phase und braucht eine mit einem zweiten Faktor bestätigte Sitzung (siehe [Erster Administrator](../setup/first-administrator#setup-phase)); ohne sie lautet die Antwort `403` mit `urn:oea:problem:second-factor-required`.

Weitere Operationen: `GET /admin/persons`, `GET /admin/persons/{personId}`, `GET` und `DELETE /admin/persons/{personId}/identities[/{identityId}]`.

## Initialpasswort setzen {#set-an-initial-password}

Ist der Passwort-Login von Personen eingeschaltet (siehe [Betrieb](./operations#password-login-of-persons)), kannst du einer Person statt einer Identitätsbindung ein Initialpasswort geben (du brauchst `person:manage`):

```bash
curl "${auth[@]}" -d '{"password": "<12 bis 128 Zeichen>"}' "$OEA/admin/persons/$PERSON/password"
```

- OEA speichert nur einen Hash. Die Antwort enthält, wann das Initialpasswort abläuft (Standard 72 Stunden), aber nie das Passwort; gib es der Person über einen getrennten Kanal weiter.
- Die Person meldet sich mit der E-Mail-Adresse ihres Personeneintrags an und muss zuerst ein eigenes Passwort wählen.
- Abgelehnt werden eine Person, die schon ein Passwort hat, deine eigene Person, Service-Accounts und Personen, die weder eingeladen noch aktiv sind.
- Das Setzen steht im Audit-Trail (`person.password-set`).

## Zugriff entziehen: inaktiv und offboarded {#take-access-away}

Eine Person ist bis zur ersten Anmeldung `invited`, danach `active`. Mit `person:manage` änderst du den Status:

```bash
curl "${auth[@]}" -d '{"status": "inactive", "reason": "Elternzeit"}' "$OEA/admin/persons/$PERSON/status"
curl "${auth[@]}" -d '{"status": "offboarded", "reason": "hat das Unternehmen verlassen"}' "$OEA/admin/persons/$PERSON/status"
```

| Änderung | Wirkung |
|---|---|
| `active` → `inactive` | alle Sitzungen enden sofort; Anmeldung wird abgelehnt; lokale Rollenzuweisungen bleiben erhalten |
| `inactive` → `active` | Anmeldung funktioniert wieder; die erhaltenen Zuweisungen gelten wieder |
| `invited`, `active` oder `inactive` → `offboarded` | alle Sitzungen enden sofort; Anmeldung wird abgelehnt; jede lokale Rollenzuweisung wird entzogen (`revokedBy` = du, Grund „offboarding“) |

- Rollen aus IdP-Gruppen brauchen keine Aktion: Eine offboarded oder inaktive Person bekommt keine Sitzung, auch wenn sie noch Mitglied der Gruppe ist.
- Die Person und ihre Historie bleiben: frühere Änderungen und Audit-Einträge sind ihr weiter zugeordnet.
- Andere Änderungen lehnt OEA mit `409` (`invalid-status-transition`) ab: ein unveränderter Status, `invited` → `active` (das macht nur die erste Anmeldung) und jede Änderung einer offboarded Person. Offboarded Personen zu reaktivieren ist noch nicht möglich.
- Deinen eigenen Status kannst du nicht ändern (`403`). Service-Accounts lehnt OEA mit `422` ab.
- Ist die Person der letzte Weg, die Instanz zu administrieren (eine unbefristete `system-admin`-Zuweisung), lehnt OEA die Änderung mit `409` ab (`last-admin-path`, siehe [Rollen einer einzelnen Person zuweisen](./roles-and-permissions#assign-roles-to-a-single-person)).
- Jede Änderung steht im Audit-Trail (`person.status-changed`, `session.revoked` je beendeter Sitzung, `role.revoked` je entzogener Zuweisung).
