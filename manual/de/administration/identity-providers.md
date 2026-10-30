---
title: Identity-Provider und Personen
covers:
  screens: []
  stories: [US-004, US-007, US-023, US-026, US-027, US-028, US-030, US-031, US-148]
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

Ist der Passwort-Login von Personen möglich (siehe [Betrieb](./operations#password-login-of-persons)), kannst du einer Person statt einer Identitätsbindung ein Initialpasswort geben (du brauchst `person:manage`). Gib eines ein, das die [Passwort-Richtlinie](#password-policy) erfüllt, oder lass OEA eines erzeugen:

```bash
curl "${auth[@]}" -d '{"password": "<erfüllt die Passwort-Richtlinie>"}' "$OEA/admin/persons/$PERSON/password"
# oder: erzeugt, einmalig in "password" zurückgegeben
curl "${auth[@]}" -d '{"generate": true}' "$OEA/admin/persons/$PERSON/password"
```

- OEA speichert nur einen Hash. Die Antwort enthält, wann das Initialpasswort abläuft (Standard 72 Stunden). Das Passwort enthält sie nur, wenn OEA es erzeugt hat, und nur dieses eine Mal; gib es der Person über einen getrennten Kanal weiter.
- Ein eingegebenes Passwort, das die Richtlinie nicht erfüllt, wird mit `422` und dem Problemtyp `password-policy-violation` abgelehnt; `invalidFields` nennt jede nicht erfüllte Regel.
- Die Person meldet sich mit der E-Mail-Adresse ihres Personeneintrags an und muss zuerst ein eigenes Passwort wählen.
- Abgelehnt werden eine Person, die schon ein Passwort hat, deine eigene Person, Service-Accounts und Personen, die weder eingeladen noch aktiv sind.
- Das Setzen steht im Audit-Trail (`person.password-set`).

## Passwort zurücksetzen {#reset-a-password}

Wer das Passwort vergessen hat, bekommt von dir ein neues temporäres (du brauchst `person:manage`):

```bash
curl "${auth[@]}" -d '{"reason": "vergessen"}' "$OEA/admin/persons/$PERSON/password/reset"
```

- OEA widerruft das alte Passwort sofort und antwortet mit einem erzeugten temporären Passwort und seinem Ablauf. Nur in dieser Antwort steht das Passwort; gib es über einen getrennten Kanal weiter.
- Alle Sitzungen der Person enden. Bei der nächsten Anmeldung muss sie zuerst ein eigenes Passwort wählen.
- Abgelehnt werden deine eigene Person, eine Person ohne Passwort und Service-Accounts.
- Hat die Person über eine lokale Rollenzuweisung eine Rolle mit administrativer Berechtigung, darf nur das lokale Systemadmin-Konto ihr Passwort zurücksetzen; alle anderen werden mit `403` abgelehnt, festgehalten als `authz.denied`. Rollen aus IdP-Gruppen werden nicht berücksichtigt, weil solche Personen sich über den Identity-Provider anmelden.
- Das Zurücksetzen steht im Audit-Trail (`person.password-reset`) mit dem Grund, nie mit dem Passwort.
- Hat die Person eine Authenticator-App, braucht die nächste Anmeldung weiterhin deren Code vor der Passwortänderung.

## Zweite Faktoren zurücksetzen {#reset-second-factors}

Wer alle Geräte mit einer Authenticator-App verloren hat, bekommt die zweiten Faktoren von dir zurückgesetzt (du brauchst `person:manage`):

```bash
curl "${auth[@]}" -d '{"reason": "Telefon verloren"}' "$OEA/admin/persons/$PERSON/second-factors/reset"
```

- OEA widerruft alle zweiten Faktoren der Person und beendet alle ihre Sitzungen; die Antwort nennt, wie viele zweite Faktoren widerrufen wurden.
- Die Person meldet sich dann nur mit dem Passwort an, und OEA bietet an, eine neue Authenticator-App einzurichten.
- Abgelehnt werden deine eigene Person, eine Person ohne zweiten Faktor und Service-Accounts. Wie beim Passwort-Zurücksetzen setzt nur das lokale Systemadmin-Konto eine Person zurück, die über eine lokale Rollenzuweisung eine Rolle mit administrativer Berechtigung hat.
- Das Zurücksetzen steht im Audit-Trail (`person.second-factors-reset`) mit dem Grund.

## Richtlinie für zweite Faktoren {#second-factor-policy}

Deine Organisation kann für jede Passwort-Anmeldung einer Person einen zweiten Faktor verlangen (du brauchst `settings:manage`):

```bash
curl "${auth[@]}" "$OEA/admin/settings/second-factor-policy"
curl "${auth[@]}" -X PUT -d '{"enforced": true, "exemptRoles": ["viewer"]}' \
  "$OEA/admin/settings/second-factor-policy"
```

- Solange `enforced` den Wert `true` hat, muss eine Person ohne Passkey oder Authenticator-App nach der Passwort-Anmeldung zuerst einen einrichten; andere Anfragen werden mit dem Problemtyp `second-factor-enrollment-required` abgelehnt.
- `exemptRoles` nennt Rollen, die keinen zweiten Faktor brauchen. Ausgenommen ist eine Person nur, wenn sie über eine lokale Rollenzuweisung mindestens eine Rolle hat und jede dieser Rollen in der Liste steht; eine Person ohne Rollen ist nicht ausgenommen. Eine Rolle mit administrativer Berechtigung oder eine unbekannte Rolle wird mit `422` abgelehnt.
- Solange die Pflicht gilt, kann eine nicht ausgenommene Person ihren letzten zweiten Faktor nicht entfernen; dein [Zurücksetzen der zweiten Faktoren](#reset-second-factors) funktioniert weiterhin, und die Person richtet bei der nächsten Anmeldung einen neuen ein.
- Die Pflicht gilt für die Passwort-Anmeldung von Personen. Anmeldungen über einen Identity-Provider folgen dessen Regeln, und das lokale Systemadmin-Konto behält seine eigenen (siehe [Erster Administrator](../setup/first-administrator)).
- Der Betrieb kann die Pflicht erzwingen (siehe [Betrieb](./operations#second-factor-enforcement)); `enforcedByOperator` ist dann `true`, und `enforced: false` wird mit `422` abgelehnt.
- Jede Änderung steht im Audit-Trail (`settings.second-factor-policy-changed`). Kündige die Pflicht an, bevor du sie einschaltest: Sie gilt bei der nächsten Passwort-Anmeldung jeder Person.

## Passwort-Richtlinie {#password-policy}

Die Passwort-Richtlinie gilt für jedes neue lokale Passwort: Initialpasswörter, Zurücksetzen und eigene Änderungen (du brauchst `settings:manage`):

```bash
curl "${auth[@]}" "$OEA/admin/settings/password-policy"
curl "${auth[@]}" -X PUT -d '{"minLength": 16, "requireUppercase": true, "requireLowercase": true,
  "requireDigits": true, "requireSpecialChars": true, "expiryDays": 90, "historyCount": 5,
  "personPasswordLogin": true}' "$OEA/admin/settings/password-policy"
```

| Feld | Bedeutung |
|---|---|
| `minLength` | Mindestlänge, 12 bis 128 |
| `requireUppercase`, `requireLowercase`, `requireDigits`, `requireSpecialChars` | Zeichentypen, die ein Passwort enthalten muss; Sonderzeichen sind alle druckbaren ASCII-Zeichen, die weder Buchstaben noch Ziffern sind |
| `expiryDays` | Tage, nach denen ein selbst gewähltes Passwort bei der nächsten Anmeldung geändert werden muss; `0` = nie |
| `historyCount` | Anzahl früherer Passwörter einschließlich des aktuellen, die nicht wiederverwendet werden dürfen; `0` bis `24` |
| `personPasswordLogin` | Passwort-Login von Personen; funktioniert nur, solange der Betrieb ihn erlaubt (`personPasswordLoginPermitted`) |

- Die Antwort enthält auch die `floors` des Betriebs. Ein Wert unter einem Mindestwert oder eine Mindestlänge, die kürzer ist als die Zahl der verlangten Zeichentypen, wird mit `422` abgelehnt; `invalidFields` nennt jedes Feld.
- Jede Änderung steht im Audit-Trail (`settings.password-policy-changed`) mit den alten und den neuen Werten.
- Bereits gesetzte Passwörter bleiben gültig; die Regeln gelten, wenn ein Passwort gesetzt wird, und für den Ablauf bei der nächsten Anmeldung.

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
