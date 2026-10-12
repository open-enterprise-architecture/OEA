---
id: REQ-069
title: Arc42-Dokumentation zu einer Entität bearbeiten und anzeigen
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-09
  business_objects:
    - arc42
    - entity
  stakeholders:
    - SH-04
    - SH-03
    - SH-05
  concept: []
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-069: Arc42-Dokumentation zu einer Entität bearbeiten und anzeigen

## Aussage

Das System MUSS für jede ArchitectureEntity, deren EntityType mindestens einer Arc42ChapterCollection zugewiesen ist, einen Tab „Arc42 Dokumentation" anzeigen. Dieser Tab zeigt alle konfigurierten Fragen der zugewiesenen Collections und ermöglicht das Anlegen, Bearbeiten und Löschen von Arc42MetaObject-Antwort-Entitäten (REQ-068). Das Anlegen einer Antwort MUSS eine `arc42-describes`-Connection zwischen Antwort-Entität und Subject-Entität automatisch erzeugen. Das System MUSS einen Fortschrittsindikator anzeigen (beantwortete vs. offene Fragen).

## Begründung

Ohne den Tab als Einstiegspunkt fehlt dem Solution Architekten der Kontext: er würde Arc42-Antworten als freistehende Entitäten suchen müssen, anstatt sie im Kontext des Systems zu sehen. Der Tab macht Arc42-Dokumentation zu einem First-Class-Feature im System-Detail.

## Kontext

Die Implementierung folgt dem Muster: „Detailansicht einer ArchitectureEntity" + „Arc42-Tab" als zusätzlicher Tab neben Properties, Connections, Diagramme. Die Fragen kommen aus den Collections (MetamodelConfiguration), die Antworten sind Entitäten im Repository.

## Typ-spezifische Felder

### Tab-Aufbau

```
[Basisdaten] [Properties] [Connections] [Diagramme] [Arc42 Dokumentation]
                                                      ↑ nur wenn Collection zugewiesen

Arc42 Dokumentation
├── [Sammlung A: Standard Arc42 (4/12 beantwortet)]    ← Fortschritt-Chip
│   ├── 1. Kontextabgrenzung  ✓  [Bearbeiten]
│   ├── 2. Qualitätsziele     ✗  [Jetzt beantworten]
│   ├── ...
└── [Sammlung B: Security Review (0/5)]
    ├── ...
```

### Antwort anlegen / bearbeiten

- `POST /api/v1/entities` mit entityTypeId=`arc42-meta-object` (oder Subtyp), `questionRef`, `name`, `content`
- Gleichzeitig: `POST /api/v1/entities` mit entityTypeId=`arc42-describes`, sourceEntityId=neue-Antwort-Id, targetEntityId=Subject-Id
- Beide in einer Transaktion

### Antwort löschen

- `DELETE /api/v1/entities/{arc42AnswerId}`
- Kaskadiert: arc42-describes-Connection wird mitgelöscht
- Frage erscheint wieder als „unbeanwortet"

### API für Tab-Daten

`GET /api/v1/entities/{id}/arc42` liefert:

```json
{
  "collections": [
    {
      "collectionId": "arc42-standard",
      "name": "Standard Arc42",
      "progress": { "answered": 4, "total": 12 },
      "questions": [
        {
          "questionId": "context-goals",
          "title": "1. Kontextabgrenzung",
          "answered": true,
          "answerId": 201,
          "answerName": "Kontextabgrenzung CRM-System"
        },
        {
          "questionId": "constraints",
          "title": "2. Randbedingungen",
          "answered": false,
          "answerId": null
        }
      ]
    }
  ]
}
```

## Akzeptanzkriterien

**AC1** (Tab sichtbar bei Collection-Zuweisung):
- Gegeben: EntityType `application-component` hat Collection `arc42-standard` zugewiesen
- Wenn: Michael öffnet CRM-System-Entität (id=1)
- Dann: Tab „Arc42 Dokumentation" sichtbar; 12 Fragen aufgelistet; alle zunächst unbeanwortet

**AC2** (Tab fehlt ohne Zuweisung):
- Gegeben: EntityType `technology-component` hat keine Collection
- Wenn: Nutzer öffnet Technology-Component-Entität
- Dann: Kein Arc42-Tab; kein Fehler

**AC3** (Antwort anlegen):
- Wenn: Michael klickt „Jetzt beantworten" bei Frage 1, schreibt Content, speichert
- Dann: arc42-meta-object-Entität angelegt (id=201); arc42-describes(source=201, target=1) angelegt; Frage 1 zeigt „✓"

**AC4** (Antwort bearbeiten):
- Wenn: Michael klickt „Bearbeiten" bei Frage 1 (id=201)
- Dann: WYSIWYG-Editor öffnet sich mit aktuellem Content; nach Speichern Entity 201 aktualisiert

**AC5** (Fortschrittsindikator):
- Wenn: 4 von 12 Fragen beantwortet
- Dann: Chip zeigt „4 / 12"; offene Fragen visuell unterschieden (Farbe oder Icon)

**AC6** (Mehrere Collections):
- Gegeben: EntityType hat 2 Collections
- Wenn: Arc42-Tab geöffnet
- Dann: Beide Collections als separate Sektionen (oder Tabs); Fortschritt pro Collection separat

**AC7** (Web Portal read-only):
- Wenn: CIO öffnet Entität im Web Portal
- Dann: Arc42-Tab sichtbar; Inhalte mit gerenderten Diagrammen; kein „Beantworten"-Button

**AC8** (Antwort löschen):
- Wenn: Michael klickt „Löschen" bei beantworteter Frage
- Dann: arc42-meta-object-Entität (201) und arc42-describes-Connection gelöscht; Frage wieder „unbeanwortet"

## Abhängigkeiten

- **Voraussetzungen**: REQ-067 (Collections), REQ-068 (Editor)
- **Folgewirkungen**: UC-06-Katalog kann arc42-meta-object-Entitäten auflisten (Dokumentationsübersicht)

## Realisierungs-Hinweise

- `GET /api/v1/entities/{id}/arc42`: aggregierter Endpoint; traversiert arc42-describes-Connections invers
- Arc42-Tab erscheint nur wenn `GET .../arc42` mindestens eine Collection zurückgibt

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
