---
id: REQ-100
title: Historischer Entitätszustand als unveränderlicher Snapshot
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-14
  business_objects:
    - entity
    - entity-version
    - person
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-100: Historischer Entitätszustand als unveränderlicher Snapshot

## Aussage

Das System MUSS den vollständigen Entitätszustand zu jeder historischen Version abrufbar machen (aus `entity_versions.snapshot`). Der vollständige Stand MUSS in der UI aufklappbar sein ("Vollständiger Stand vN anzeigen") und alle Felder inkl. `propertyDefinitions` enthalten. Die Snapshots MÜSSEN unveränderlich sein — kein Löschen, kein Editieren.

## Begründung

Der Diff allein reicht für Compliance-Nachweise nicht aus. Manchmal muss belegt werden, wie eine Entität zu einem bestimmten Zeitpunkt vollständig ausgesehen hat — z.B. für Audits oder Regulierungs-Nachweise.

## Akzeptanzkriterien

**AC1** (Vollständiger Stand aufklappbar):
- Wenn: "Vollständiger Stand v4 anzeigen" in der Zeitlinie geklickt wird
- Dann: werden alle Felder inkl. leerer Properties des Snapshots dargestellt

**AC2** (Löschen abgewiesen):
- Wenn: ein DELETE-Request an `/entity-versions/{id}` gesendet wird
- Dann: antwortet die API mit HTTP 405 Method Not Allowed

**AC3** (Bearbeiten abgewiesen):
- Wenn: ein PUT-Request an `/entity-versions/{id}` gesendet wird
- Dann: antwortet die API mit HTTP 405 Method Not Allowed

## Abhängigkeiten

- **Voraussetzungen**: REQ-098 (Zeitlinie), REQ-102 (Snapshot-Anlage)
- **Folgewirkungen**: REQ-099 (Diff), REQ-101 (Vollwiederherstellung)

## Realisierungs-Hinweise

- `entity_versions.snapshot` als JSONB in PostgreSQL; nicht normalisiert
- HTTP 405 statt 422 — die Methode ist generell nicht erlaubt, nicht nur im Einzelfall
- `propertyDefinitions` werden zum Zeitpunkt der Snapshot-Erstellung eingefroren

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
