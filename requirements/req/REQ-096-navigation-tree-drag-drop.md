---
id: REQ-096
title: Navigationsbaum Drag & Drop mit Zyklus-Schutz
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-13
  business_objects:
    - tree-node
    - catalog
    - entity
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-096: Navigationsbaum Drag & Drop mit Zyklus-Schutz

## Aussage

Das System MUSS das Neuanordnen von Items (`sortOrder`) und das Verschieben von Ordnern und Items per Drag & Drop unterstützen. Beim Verschieben eines Ordners MUSS das System Zyklen erkennen (BR-03: Ordner darf nicht in einen eigenen Nachkommen verschoben werden) und das Drop-Target visuell als ungültig markieren; API-Versuche mit Zyklus MÜSSEN mit HTTP 422 abgewiesen werden.

## Begründung

Drag & Drop ist die ergonomischste Form der Baum-Reorganisation. Ohne Zyklus-Schutz würde ein versehentliches Verschieben den Baum inkonsistent machen und zu einer unendlichen Traversierung führen.

## Akzeptanzkriterien

**AC1** (Zyklus-Erkennung):
- Wenn: Ordner A per Drag & Drop unter Ordner B gezogen wird und B ein Nachkomme von A ist
- Dann: wird das Drop-Target visuell als ungültig markiert; ein API-Versuch antwortet mit HTTP 422

**AC2** (Neuanordnung von Items):
- Wenn: Items innerhalb eines Ordners per Drag & Drop neu angeordnet werden
- Dann: wird `sortOrder` korrekt aktualisiert; die neue Reihenfolge ist persistent

**AC3** (Item verschieben zwischen Ordnern):
- Wenn: ein Item per Drag & Drop von Ordner A nach Ordner B verschoben wird
- Dann: ist das Item in B vorhanden und nicht mehr in A

## Abhängigkeiten

- **Voraussetzungen**: REQ-094 (Ordner-CRUD), REQ-095 (Items)
- **Folgewirkungen**: keine

## Realisierungs-Hinweise

- Zyklus-Prüfung: Ancestor-Traversierung vor dem Move (rekursive Parent-Prüfung)
- `sortOrder` als Integer-Feld; bei Neuordnung werden betroffene Nachbarn aktualisiert
- Drag & Drop-Bibliothek muss valide Drop-Targets in Echtzeit markieren können

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
