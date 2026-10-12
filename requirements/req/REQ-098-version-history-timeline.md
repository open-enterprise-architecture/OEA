---
id: REQ-098
title: Versionshistorie als chronologische Zeitlinie
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

# REQ-098: Versionshistorie als chronologische Zeitlinie

## Aussage

Das System MUSS für jede ArchitectureEntity eine chronologische Versionshistorie (Zeitlinie) anzeigen. Pro Version MÜSSEN folgende Informationen sichtbar sein: Versionsnummer, Zeitstempel (UTC), Name der ändernden Person (verlinkt auf Profil), geänderte Felder (aus `EntityVersion.changedFields`) und optionaler Änderungsgrund. Die Zeitlinie MUSS nach Zeitraum und Actor filterbar sein. Bei mehr als 50 Versionen MUSS paginiert werden.

## Begründung

Ohne nachvollziehbare Änderungshistorie ist ein Architecture Repository kein vertrauenswürdiges System of Record. Compliance-Anforderungen erfordern lückenlose Audit-Trails, die für jeden EA-relevanten Zustand belegen, wer was wann geändert hat.

## Akzeptanzkriterien

**AC1** (Zeitlinie mit Versionen):
- Wenn: eine Entität mit 5 Versionen geöffnet wird
- Dann: zeigt die Zeitlinie 5 Einträge, neueste zuerst

**AC2** (Filter nach Actor):
- Wenn: die Zeitlinie nach Actor "Michael" gefiltert wird
- Dann: werden nur Versionen angezeigt, die von Michael erstellt wurden

**AC3** (Entität ohne Historie):
- Wenn: eine Entität ohne Änderungshistorie geöffnet wird
- Dann: zeigt der Tab "Keine Änderungen seit Anlage. Erstellt am [Datum] von [Person]."

## Abhängigkeiten

- **Voraussetzungen**: REQ-102 (EntityVersion-Snapshots müssen existieren)
- **Folgewirkungen**: REQ-099 (Diff-Ansicht), REQ-100 (Snapshot-Abruf), REQ-101 (Vollwiederherstellung)

## Realisierungs-Hinweise

- Paginierung bei > 50 Versionen; Page-Size konfigurierbar
- Filter nach Zeitraum per `from`/`to`-Query-Parameter; Actor-Filter per `actorId`
- Zeitstempel immer in UTC anzeigen; Formatierung im Frontend nach lokalem Timezone-Offset

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
