---
id: REQ-067
title: Arc42ChapterCollection im Metamodell konfigurieren
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-04
    - UC-09
  business_objects:
    - arc42
    - metamodel-configuration
  stakeholders:
    - SH-07
    - SH-03
  concept:
    - concept/20-entities/14-erweiterbarkeit.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-067: Arc42ChapterCollection im Metamodell konfigurieren

## Aussage

Das System MUSS dem Metamodell-Administrator ermöglichen, `Arc42ChapterCollection`-Objekte in der MetamodelConfiguration anzulegen, zu bearbeiten und zu löschen. Eine Collection besteht aus einem `name`, einer geordneten Liste von `Arc42QuestionTemplate`-Einträgen und einer Liste von `assignedEntityTypeIds`. Das System MUSS ausserdem eine vordefinierte Built-in-Collection `arc42-standard` mit den 12 klassischen Arc42-Kapiteln mitliefern, die als Startpunkt importiert werden kann.

## Begründung

Ohne konfigurierbare Collections wäre Arc42 ein hartes Format, das nicht zum jeweiligen Kontext passt. Ein KMU braucht 5 Fragen, ein Konzern 15. Die Collection-Ebene erlaubt beliebige Konfiguration pro Instanz — maximalem Freiheitsgrad gemäss dem OEA-Grundprinzip.

## Akzeptanzkriterien

**AC1** (Collection anlegen):
- Wenn: Admin postet `{ id: "arc42-kmu", name: "Arc42 KMU", questions: [...], assignedEntityTypeIds: ["application-component"] }`
- Dann: HTTP 201; Collection in MetamodelConfiguration gespeichert; EntityType `application-component` zeigt Collection beim Öffnen

**AC2** (Frage hinzufügen):
- Wenn: Admin fügt `{ questionId: "context", title: "Kontext", questionText: "...", sortOrder: 1 }` zu einer Collection hinzu
- Dann: Frage erscheint an Position 1 in der Collection

**AC3** (Built-in Standard-Collection importieren):
- Wenn: Admin klickt „Standard Arc42 importieren"
- Dann: Collection `arc42-standard` mit 12 Fragen wird als user-definierte Kopie angelegt (editierbar); Original bleibt unveränderlich

**AC4** (Mehrere zugewiesene EntityTypes):
- Wenn: `assignedEntityTypeIds: ["application-component", "technology-component"]`
- Dann: Beide EntityTypes zeigen die Collection im Arc42-Tab

**AC5** (Löschen Collection):
- Wenn: Admin löscht eine Collection, die bereits Antwort-Entitäten besitzt
- Dann: HTTP 422; Meldung: „Collection hat {N} Antworten — zuerst Antworten entfernen oder Collection als inaktiv markieren"; kein Datenverlust

## Abhängigkeiten

- **Voraussetzungen**: REQ-036 (EntityTypeDefinition), REQ-033 (Import — Collections müssen per MetamodelConfiguration-Import mitkommen)
- **Folgewirkungen**: REQ-068 (WYSIWYG-Editor), REQ-069 (Anzeige und Bearbeitung)

## Realisierungs-Hinweise

- Arc42ChapterCollection wird als Teil der MetamodelConfiguration gespeichert (neues Top-Level-Array `arc42Collections: Arc42ChapterCollection[]`)
- Built-in `arc42-standard` wird bei Systemstart automatisch registriert; ist nicht in der DB, sondern im Code
- Import/Export von Collections via REQ-033/REQ-058 (MetamodelConfiguration-Import/Export deckt arc42Collections mit ab)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
