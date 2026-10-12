---
id: REQ-115
title: TRM-Kategorie-Hierarchie verwalten
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-19
  business_objects:
    - trm-category
    - solution-building-block
    - entity
  stakeholders:
    - SH-03
supersedes: []
superseded_by: []
---

# REQ-115: TRM-Kategorie-Hierarchie verwalten

## Aussage

Das System MUSS das Anlegen, Umbenennen und Löschen von TRM-Kategorien mit `scope=organization` ermöglichen. Beim Anlegen MUSS das `level`-Feld automatisch als `parent.level + 1` gesetzt werden. Das Löschen einer Kategorie mit Unterknoten MUSS mit HTTP 422 abgewiesen werden (erst Kinder entfernen). Das Löschen einer Kategorie mit zugeordneten Entitäten (`classifiedEntities` nicht leer) MUSS eine Warnung erzeugen, aber nicht blockieren. TRM-Kategorien mit `scope=organization` MÜSSEN per Drag & Drop innerhalb der Hierarchie reorganisierbar sein (Zyklus-Schutz: BR-01 aus trm-category.md).

## Begründung

Das TRM ist eine lebende Taxonomie. Ohne CRUD und Reorganisation kann eine Organisation ihr TRM nicht an eigene Strukturen anpassen, was die Akzeptanz des Werkzeugs gefährdet.

## Akzeptanzkriterien

**AC1** (Automatisches level):
- Wenn: eine Unterkategorie unter „Security Services" angelegt wird
- Dann: ist das `level`-Feld automatisch auf `parent.level + 1` gesetzt

**AC2** (Löschen mit Unterknoten):
- Wenn: eine Kategorie mit 3 Unterknoten gelöscht werden soll
- Dann: antwortet das System mit HTTP 422 „Unterknoten müssen zuerst entfernt werden"

**AC3** (Drag & Drop Zyklus-Schutz):
- Wenn: eine Kategorie per Drag & Drop unter einen ihrer eigenen Nachkommen gezogen wird
- Dann: wird der Drop visuell blockiert; ein API-Versuch antwortet mit HTTP 422

## Abhängigkeiten

- **Voraussetzungen**: REQ-112 (Import erzeugt scope=imported Kategorien als Basis)
- **Folgewirkungen**: REQ-116 (SBB-Governance-Zuordnung je Kategorie), REQ-117 (Review-Datum)

## Realisierungs-Hinweise

- Hierarchie: Adjacency-List-Modell mit `parentId`; `level` als berechnetes Feld oder denormalisiert
- Drag & Drop: Zyklus-Prüfung im Frontend (vor Drop) und Backend (HTTP 422)
- Löschen mit Entitäten: Warn-Modal mit Anzahl betroffener Entitäten; keine API-Blockierung

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
