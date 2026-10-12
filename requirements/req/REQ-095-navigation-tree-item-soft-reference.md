---
id: REQ-095
title: Navigationsbaum Items als Soft-References
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

# REQ-095: Navigationsbaum Items als Soft-References

## Aussage

Das System MUSS das Hinzufügen und Entfernen von Repository-Inhalten (Entitäten, Diagramme, Kataloge) als TreeNodeItems zu Ordnern ermöglichen. Das Entfernen eines Items MUSS ausschliesslich den Navigationseintrag (TreeNodeItem) löschen; das referenzierte Objekt DARF dabei NICHT verändert oder gelöscht werden. Das System MUSS verwaiste Items (Zielobjekt inzwischen gelöscht) mit einem Warnsymbol kennzeichnen und die Bereinigung ermöglichen.

## Begründung

Der Navigationsbaum ist eine reine Navigationshilfe (Soft-Reference-Prinzip). Eine Kopplung von Navigation und Datenlöschung würde zu unbeabsichtigtem Datenverlust führen und den Baum als unabhängige Strukturierungsebene wertlos machen.

## Akzeptanzkriterien

**AC1** (Item-Entfernung ohne Datenverlust):
- Wenn: ein TreeNodeItem entfernt wird
- Dann: ist das TreeNodeItem gelöscht; die referenzierte Entität existiert weiterhin (HTTP 200 auf die Entität)

**AC2** (Verwaiste Items markieren):
- Wenn: eine Entität gelöscht wird, die als Item in 2 Ordnern referenziert ist
- Dann: zeigen beide Items ein Warnsymbol; die Entität ist nicht durch Item-Entfernung wiederherstellbar

## Abhängigkeiten

- **Voraussetzungen**: REQ-094 (Ordner existieren)
- **Folgewirkungen**: REQ-097 (Mehrfach-Einordnung)

## Realisierungs-Hinweise

- Soft-Reference-Prinzip gemäss BR-05, BR-06 in `tree-node.md`
- Verwaiste Items per Background-Job oder bei Anzeige prüfen (lazy vs. eager)
- Bereinigung: Bulk-Delete für verwaiste Items als eigene UI-Aktion

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
