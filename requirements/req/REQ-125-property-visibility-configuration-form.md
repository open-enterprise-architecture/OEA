---
id: REQ-125
title: Konfigurationsmaske für Property-Sichtbarkeit im Metamodell-Editor
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
    - metamodel-configuration
    - role
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-125: Konfigurationsmaske für Property-Sichtbarkeit im Metamodell-Editor

## Aussage

Der Metamodell-Editor MUSS pro `PropertyDefinition` eine Konfigurationsmaske für die Sichtbarkeit bereitstellen. Die Maske MUSS:

1. Einen **Modus-Selektor** (Dropdown oder Radio-Group) für `visibilityMode` mit den drei Optionen `Öffentlich`, `Rollenbasiert`, `Verbindungsbasiert` anzeigen
2. Bei Wahl von `role-restricted`: eine **Rollenauswahl** (Multi-Select aus allen konfigurierten Rollen) für `allowedRoles` einblenden
3. Bei Wahl von `connection-scoped`: eine **Connection-Typ-Auswahl** (Single-Select aus allen `isConnection=true`-Typen) für `scopingConnectionType` einblenden
4. Den aktuell gesetzten Modus visuell hervorheben (z.B. Badge oder Icon am Property-Eintrag in der Liste)

Nicht benötigte Felder (`allowedRoles` bei `public`, `scopingConnectionType` bei `role-restricted`) MÜSSEN ausgeblendet sein.

## Begründung

Ohne dedizierte Konfigurationsmaske müssten EA-Manager Sichtbarkeit über YAML-Import verwalten — das ist fehleranfällig und schließt nicht-technische Nutzer aus.

## Akzeptanzkriterien

**AC1** (Modus-Selektor sichtbar):
- Wenn: Kurt ein Property im Metamodell-Editor öffnet
- Dann: Sichtbarkeits-Sektion ist vorhanden; aktueller Modus ist vorgewählt (`public` für neue Properties)

**AC2** (Bedingte Felder):
- Wenn: Modus-Selektor auf `Rollenbasiert` gestellt
- Dann: Rollenauswahl erscheint; `scopingConnectionType`-Feld ist ausgeblendet

**AC3** (Badge):
- Wenn: Ein Property hat `visibilityMode ≠ public`
- Dann: Property-Eintrag in der Liste zeigt ein „Eingeschränkt"-Badge oder entsprechendes Icon

**AC4** (Speichern-Validierung im UI):
- Wenn: Kurt auf „Speichern" klickt mit leerem `allowedRoles` bei `role-restricted`
- Dann: Inline-Fehlerhinweis erscheint; Server-Request wird nicht abgesetzt

## Abhängigkeiten

- **Voraussetzungen**: REQ-124 (Datenmodell), REQ-032 (Metamodell-Editor vorhanden)
- **Folgewirkungen**: –

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
