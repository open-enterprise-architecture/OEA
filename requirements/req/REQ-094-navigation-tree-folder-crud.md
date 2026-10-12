---
id: REQ-094
title: Navigationsbaum Ordner-CRUD
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

# REQ-094: Navigationsbaum Ordner-CRUD

## Aussage

Das System MUSS das Anlegen, Umbenennen und Löschen von TreeNode-Ordnern im Navigationsbaum ermöglichen. Ordnernamen MÜSSEN unter Geschwistern eindeutig sein (BR-01). Das Löschen eines Ordners MUSS alle Kind-Knoten und Items rekursiv entfernen; referenzierte Objekte bleiben unberührt (BR-05). Der Wurzelknoten DARF NICHT löschbar sein (BR-02); Umbenennen ist erlaubt.

## Begründung

Der Navigationsbaum ist das primäre Strukturierungsinstrument für das EA-Repository. Ohne CRUD-Operationen auf Ordnern kann keine sinnvolle Navigation aufgebaut werden.

## Akzeptanzkriterien

**AC1** (Ordner anlegen):
- Wenn: ein neuer Ordner unter einem Parent angelegt wird
- Dann: antwortet die API mit HTTP 201; der Ordner ist im Baum sichtbar

**AC2** (Doppelter Name abweisen):
- Wenn: ein Ordner mit einem unter demselben Parent bereits vorhandenen Namen angelegt wird
- Dann: antwortet die API mit HTTP 422 "Name bereits vorhanden"

**AC3** (Rekursives Löschen):
- Wenn: ein Ordner mit 3 Kind-Ordnern und 10 Items gelöscht wird
- Dann: werden Kind-Ordner und Items entfernt; referenzierte Entitäten bleiben unberührt

**AC4** (Wurzelknoten-Schutz):
- Wenn: ein DELETE-Request an `/tree-nodes/root` gesendet wird
- Dann: antwortet die API mit HTTP 422 "Wurzelknoten kann nicht gelöscht werden"

## Abhängigkeiten

- **Voraussetzungen**: keine
- **Folgewirkungen**: REQ-095 (Items), REQ-096 (Drag & Drop), REQ-097 (Mehrfach-Einordnung)

## Realisierungs-Hinweise

- Rekursives Löschen als DB-CASCADE oder explizite rekursive Traversierung implementieren
- Unique-Constraint auf `(parentId, name)` in der DB
- Wurzelknoten per Seeding angelegt; kein API-Endpunkt zum Anlegen des Roots

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
