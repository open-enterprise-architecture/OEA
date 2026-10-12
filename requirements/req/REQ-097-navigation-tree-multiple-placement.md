---
id: REQ-097
title: Navigationsbaum Mehrfach-Einordnung von Objekten
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

# REQ-097: Navigationsbaum Mehrfach-Einordnung von Objekten

## Aussage

Dasselbe Repository-Objekt (Entität, Diagramm oder Katalog) MUSS gleichzeitig als Item in mehreren Ordnern referenziert sein dürfen. Jeder Ordnereintrag ist ein eigenständiges TreeNodeItem mit eigener `referenceId`; keine Datenduplizierung des Objekts.

## Begründung

Ein Applikations-Cluster kann gleichzeitig zur Domäne "Finance" und zur Initiative "Cloud-Migration" gehören. Exklusive Einordnung würde die Abbildung realer Strukturen einschränken und Nutzer zur Datenduplizierung zwingen.

## Akzeptanzkriterien

**AC1** (Mehrfach-Einordnung):
- Wenn: die Entität "SAP ERP" als Item in Ordner "Finance" und "Cloud-Migration" eingehängt wird
- Dann: existieren zwei eigenständige TreeNodeItems mit derselben `referenceId`; die Entität selbst ist unverändert

**AC2** (Unabhängige Entfernung):
- Wenn: das Item "SAP ERP" aus Ordner "Finance" entfernt wird
- Dann: ist der Eintrag in "Finance" gelöscht; der Eintrag in "Cloud-Migration" bleibt gültig

## Abhängigkeiten

- **Voraussetzungen**: REQ-095 (Soft-Reference-Prinzip)
- **Folgewirkungen**: keine

## Realisierungs-Hinweise

- Kein Unique-Constraint auf `(folderId, referenceId)` — Mehrfach-Einordnung explizit erlaubt
- TreeNodeItem hat eigene ID (`referenceId`) unabhängig vom referenzierten Objekt
- Kein Datenbankmodell-Duplikat des Objekts; nur der Verknüpfungs-Eintrag wird mehrfach angelegt

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
