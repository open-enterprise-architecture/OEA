---
title: Rollen und Berechtigungen
covers:
  screens: []
  stories: [US-014, US-018, US-143, US-144, US-146, US-147]
---

# Rollen und Berechtigungen

Jede Operation von OEA verlangt eine Berechtigung, und Berechtigungen kommen nur aus Rollen. OEA liefert einen festen Berechtigungskatalog und eingebaute Rollen aus; `GET /admin/permissions` und `GET /admin/roles` geben sie zurück.

## Eingebaute Rollen

| Rolle | Berechtigungen | Für |
|---|---|---|
| `portal-reader` | `portal:read` | geteilte Inhalte im Portal lesen |
| `viewer` | `portal:read`, `entity:read` | das Repository lesen |
| `analyst` | `viewer` + `analytics:query` | Analytics-Abfragen |
| `enterprise-architect` | `viewer` + `entity:write`, `entity:delete`, `entity:restore`, `catalog:manage`, `dashboard:manage`, `viewpoint:manage`, `plateau:go-live`, `webhook:manage` | die Architektur pflegen |
| `integration-writer` | `entity:read`, `entity:write` | Integrationen, die Entitäten schreiben |
| `auditor` | `audit:read` | das Audit-Log lesen und prüfen |
| `metamodel-admin` | `metamodel:write` | das Metamodell ändern und importieren |
| `security-admin` | `visibility:configure`, `visibility-scope:assign`, `role:assign`, `person:manage` | Sichtbarkeitsregeln, Personen und Rollen |
| `system-admin` | `role:assign`, `person:manage`, `service-account:manage`, `auth-config:manage`, `settings:manage` | die Instanz administrieren |

Das lokale Systemadmin-Konto hat die Rolle `system-admin`. **Privilegierte Rollen** – Rollen mit einer Berechtigung der Admin-Klasse (`metamodel:write`, `visibility:configure`, `visibility-scope:assign`, `role:assign`, `person:manage`, `service-account:manage`, `auth-config:manage`, `settings:manage`) – wirken nur in einer Sitzung, die mit einem zweiten Faktor bestätigt ist. Bei einer Anmeldung über einen Identity-Provider muss der Provider den zweiten Faktor in `amr` melden.

## Rollen über IdP-Gruppen vergeben

Du vergibst Rollen, indem du eine Gruppe des Identity-Providers einer Rolle zuordnest. Jede Person in dieser Gruppe bekommt die Rolle bei ihrer nächsten Anmeldung:

```bash
curl "${auth[@]}" -d "{\"authProviderId\": \"$PROVIDER\", \"groupIdentifier\": \"<group-uuid>\", \"roleId\": \"viewer\"}" \
  "$OEA/admin/idp-group-mappings"
```

- Die Gruppenkennung muss die unveränderliche UUID der Gruppe sein (die Gruppen-Objekt-ID von Entra ID, Authentik und Keycloak); Anzeigenamen werden mit `422` abgelehnt.
- Eine Person kann keine Gruppe zuordnen, die in ihrem eigenen Groups-Claim enthalten ist (Funktionstrennung).
- `GET /admin/idp-group-mappings` listet die Zuordnungen; `PUT` und `DELETE /admin/idp-group-mappings/{mappingId}` ändern und entfernen eine.
- Wie die erste Identitätsbindung beendet auch die erste Gruppenzuordnung die Setup-Phase.
- Jede Zuordnung zeigt in `lastMatchedAt`, wann sie ihre Rolle zuletzt bei einer Anmeldung vergeben hat; `null` heißt: noch nie. Eine privilegierte Rolle zählt nur, wenn sich die Person mit zweitem Faktor angemeldet hat.
- Ordnest du eine Gruppe `system-admin` zu und hat noch keine Anmeldung an dieser Instanz diese Gruppe gezeigt, speichert OEA die Zuordnung trotzdem und gibt in `warnings` die Warnung `group-not-seen` zurück: Die Zuordnung gibt niemandem Bekanntem die Systemadministration, bis sich ein Mitglied der Gruppe anmeldet. Prüfe die Gruppenkennung beim Provider. OEA sieht die Gruppenmitglieder nicht und kann nur melden, was Anmeldungen gezeigt haben.

## Systemadministration an eine IdP-Gruppe übergeben {#hand-over-system-administration-to-an-idp-group}

Das lokale Systemadmin-Konto brauchst du nicht mehr, sobald sich deine Administratorinnen und Administratoren über den Identity-Provider anmelden:

1. Ordne die Gruppe der Administratoren der Rolle `system-admin` zu (siehe oben). Ihre Mitglieder brauchen beim Provider einen zweiten Faktor, sonst wirkt die Rolle nicht.
2. Lass ein Mitglied der Gruppe sich anmelden. Dann ist `lastMatchedAt` der Zuordnung gesetzt, und erst ab dann zählt sie als funktionierender Weg zur Systemadministration.
3. Deaktiviere das lokale Systemadmin-Konto (siehe [Lokales Systemadmin-Konto deaktivieren](./operations#deactivate-the-local-system-admin-account)). OEA lehnt das ab, solange keine Zuordnung zu `system-admin` bei einer Anmeldung gegriffen hat.

## Rollen einer einzelnen Person zuweisen {#assign-roles-to-a-single-person}

Neben IdP-Gruppen kannst du einer Person eine Rolle direkt zuweisen (du brauchst `role:assign`). Lies die Rolle vorher, um zu sehen, was sie gewährt: `GET /admin/roles/{roleId}` listet ihre Berechtigungen und ob sie privilegiert ist.

```bash
curl "${auth[@]}" "$OEA/admin/roles/enterprise-architect"
curl "${auth[@]}" -d '{"roleId": "enterprise-architect", "validTo": "2027-06-30T23:59:59Z", "reason": "TICKET-123"}' \
  "$OEA/admin/persons/<person-id>/role-assignments"
curl "${auth[@]}" -X POST -d '{"reason": "hat das Projekt verlassen"}' \
  "$OEA/admin/persons/<person-id>/role-assignments/<assignment-id>/revoke"
```

- Die Rolle gilt innerhalb einer Minute für die Sitzungen der Person. Eine privilegierte Rolle gilt nur in einer Sitzung, die mit einem zweiten Faktor bestätigt ist.
- `validFrom` (Standard: jetzt) und `validTo` (optional) begrenzen die Zuweisung zeitlich. Nach `validTo` gilt die Rolle nicht mehr. Der Ablauf wird im Audit-Trail festgehalten.
- Eine Zuweisung an eine eingeladene Person wirkt ab ihrer ersten Anmeldung.
- Ein Entzug behält die Zuweisung mit `revokedAt` und `revokedBy` in der Historie und beendet alle Sitzungen der Person; die nächste Anmeldung hat nur noch die übrigen Rollen. `GET …/role-assignments?status=revoked,expired` zeigt die Historie.
- Eigene Rollen kannst du weder zuweisen noch entziehen (`403`, Funktionstrennung). Service-Accounts bekommen nur Rollen, die für sie vorgesehen sind (`422`).
- Metamodell-Administration (`metamodel:write`) und Sichtbarkeits-Administration bei derselben Person sind in kleinen Teams erlaubt, aber nur mit `"acknowledgeSodConflict": true`; ohne wird die Anfrage mit `409` (`sod-conflict`) beantwortet, und die Zuweisung wird mit `sodConflict` markiert.
- Mindestens ein Weg zur Administration der Instanz bleibt immer erhalten: Den Entzug einer `system-admin`-Zuweisung, das Deaktivieren des lokalen Systemadmin-Kontos oder das Löschen, Deaktivieren oder Umhängen einer IdP-Gruppenzuordnung zu `system-admin` lehnt OEA mit `409` (`last-admin-path`) ab, wenn danach keiner mehr bliebe. Als Weg zählen das aktive lokale Konto, eine Zuordnung, die die Rolle bei einer Anmeldung vergeben hat, und eine `system-admin`-Zuweisung ohne `validTo` an eine aktive Person.
- Zuweisungen und Entzüge stehen im Audit-Trail (`role.assigned`, `role.revoked`, `role.assignment-expired`).

## Wenn sich die Gruppenmitgliedschaft ändert

OEA wertet die Gruppenmitgliedschaft mit dem Refresh-Token des Providers spätestens 15 Minuten nach der vorherigen Auswertung neu aus: Entfernst du eine Person beim Provider aus einer Gruppe, ist die abgeleitete Rolle innerhalb von 15 Minuten weg. Ohne Refresh-Token, bei einem nicht erreichbaren Provider oder bei einem fehlenden oder abgeschnittenen Groups-Claim gilt keine vom IdP abgeleitete Rolle (Audit-Event `auth.idp-groups-unusable`).

Eine über einen Identity-Provider aufgebaute Sitzung endet beim ersten der folgenden Ereignisse: 30 Minuten ohne Aktivität, 12 Stunden nach der Anmeldung oder Ablauf oder Ablehnung des Refresh-Tokens. OEA verlängert nie ein Token des Providers.
