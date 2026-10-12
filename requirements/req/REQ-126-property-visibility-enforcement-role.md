---
id: REQ-126
title: Enforcement role-restricted Property-Sichtbarkeit
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-21
  business_objects:
    - entity
    - metamodel-configuration
    - role
  stakeholders:
    - SH-03
    - SH-04
    - SH-02
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-126: Enforcement role-restricted Property-Sichtbarkeit

## Aussage

Die Entity-API MUSS bei Rückgabe einer Entität alle `PropertyDefinition`-Werte mit `visibilityMode = role-restricted` für Nutzer ohne eine der konfigurierten `allowedRoles` durch `null` ersetzen. Das Frontend MUSS ein `null`-Wert-Feld mit `visibilityMode ≠ public` als **leer und nicht editierbar** darstellen — ohne Hinweis auf den tatsächlichen Wert. Die Filterung erfolgt serverseitig; der tatsächliche Wert DARF den Client für nicht-berechtigte Nutzer niemals erreichen.

## Begründung

Clientseitiges Ausblenden reicht nicht — ein technisch versierter Nutzer könnte den API-Response direkt lesen. Die Vertraulichkeit muss serverseitig erzwungen werden.

## Akzeptanzkriterien

**AC1** (API-Response):
- Wenn: Nutzer ohne erlaubte Rolle Entity mit role-restricted Property abruft
- Dann: API-Response enthält `"investitionskostenPrognose": null`; kein tatsächlicher Wert im Response

**AC2** (UI leer und read-only):
- Wenn: Nutzer ohne Berechtigung das Entity-Detailformular öffnet
- Dann: Eingeschränktes Feld ist leer dargestellt; Eingabe ist deaktiviert; kein Tooltip mit echtem Wert

**AC3** (Schreibschutz):
- Wenn: Nutzer ohne Berechtigung versucht, das Feld via API direkt zu beschreiben (PATCH)
- Dann: API antwortet mit HTTP 403; Wert wird nicht geändert

**AC4** (Berechtigter Nutzer):
- Wenn: Nutzer mit einer der `allowedRoles` dasselbe Feld abruft
- Dann: Tatsächlicher Wert wird zurückgegeben; Feld ist editierbar

**AC5** (Katalog-Konsistenz):
- Wenn: Katalog (UC-06) eine Spalte auf ein role-restricted Property zeigt
- Dann: Spalte zeigt für nicht-berechtigte Nutzer leere Zellen; Sortierung und Filterung dieser Spalte sind für diese Nutzer deaktiviert

## Abhängigkeiten

- **Voraussetzungen**: REQ-124 (Datenmodell), REQ-001 (Authentifizierung), REQ-003 (Rollenzuweisung)
- **Folgewirkungen**: –

## Realisierungs-Hinweise

- Filterung auf API-Ebene in der Entity-Read-Pipeline (Middleware/Interceptor); kein DB-Filter nötig — der Wert wird nach dem Lesen maskiert
- Rollen-Check: `currentUser.roles ∩ property.allowedRoles ≠ ∅` → sichtbar

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
