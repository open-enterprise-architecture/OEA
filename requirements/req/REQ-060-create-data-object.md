---
id: REQ-060
title: Datenobjekt (data-object) als Entität anlegen und verwalten
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-26
last_updated: 2026-06-26
author: requirements-engineer
references:
  use_cases:
    - UC-08
  business_objects:
    - entity
    - metamodel-configuration
  business_rules: []
  stakeholders:
    - SH-02
    - SH-03
  concept:
    - concept/20-entities/06-kern-entitaetstypen.md
    - concept/40-extensibility/14-erweiterbarkeit.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-060: Datenobjekt (data-object) als Entität anlegen und verwalten

## Aussage

Das System MUSS es Personen mit Entitäts-Bearbeiter-Berechtigung ermöglichen, ArchitectureEntities mit dem EntityType `data-object` anzulegen, zu bearbeiten und zu löschen; das System MUSS beim Anlegen automatisch eine instanzweit eindeutige Integer-ID vergeben und `entityTypeId=data-object` als unveränderliche Metatyp-Bindung setzen. Für `data-object` MUSS der EntityType im Metamodell die folgenden PropertyDefinitions unterstützen: `dataClassification` (enum, mandatory), `personalDataCategories` (varchar, optional), `dataOwner` (varchar, warning).

## Begründung

Datenobjekte (logische Datenentitäten wie „Kundenstamm", „Bestellposition") sind die Ankerpunkte jeder Lineage-Analyse. Ohne sie als First-Class-Entitäten mit stabiler Integer-ID ist keine traversierbare Lineage möglich — man kann nur auf String-Referenzen setzen, die nicht validiert werden können.

## Kontext

`data-object` ist kein built-in EntityType, sondern wird vom Data Architect oder Lead EA via UC-04 im Metamodell angelegt. Dieser REQ beschreibt die Verhaltensanforderungen an das System beim Umgang mit solchen Entitäten — unabhängig davon, wie der Typ konkret heisst, solange `isConnection=false` gilt.

Die PropertyDefinitions in diesem REQ sind Empfehlungen für den Standard-`data-object`-EntityType; abweichende Benennungen sind über das Metamodell möglich.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- `entityTypeId`: `data-object` (oder äquivalenter Custom-Typ mit `isConnection=false`)
- `name`: Anzeigename (z.B. „Kundenstamm")
- `properties`: PropertyValue-Liste gemäss EntityType-PropertyDefinitions

**Verarbeitung**:
1. System vergibt nächste freie Integer-ID (instanzweit, Elemente und Connections teilen Nummernraum)
2. `entityTypeId` wird gesetzt und ist unveränderlich
3. PropertyValues werden gegen PropertyDefinitions des EntityTypes validiert (mandatory → 422 wenn leer; warning → 200 mit Warnung)
4. Entität wird persistiert; Audit-Log-Eintrag

**Ausgaben**:
- HTTP 201 mit vollständiger Entitäts-Repräsentation inkl. vergebener Integer-ID
- Bei Validierungswarnung: HTTP 200 mit `warnings`-Array in Response

**Fehlerfälle**:
- Ungültige `entityTypeId` → HTTP 422
- Mandatory Property leer → HTTP 422 mit Liste aller verletzten Properties
- Keine Berechtigung → HTTP 403

## Akzeptanzkriterien

**AC1** (Anlegen mit ID-Vergabe):
- Wenn: Lukas legt Entität `Kundenstamm` (entityTypeId=`data-object`) an
- Dann: HTTP 201; `id` ist Integer ≥ 1; `entityTypeId=data-object` ist gesetzt; ID ist in der Instanz einmalig

**AC2** (Mandatory Property erzwingt Wert):
- Gegeben: `dataClassification` ist mandatory
- Wenn: Lukas legt Entität ohne `dataClassification` an
- Dann: HTTP 422; Fehlermeldung benennt `dataClassification` als fehlend

**AC3** (Warning-Property speichert trotzdem):
- Gegeben: `dataOwner` ist warning
- Wenn: Lukas legt Entität ohne `dataOwner` an
- Dann: HTTP 200; Entität gespeichert; Response enthält `warnings: ["dataOwner: Wert fehlt"]`

**AC4** (entityTypeId unveränderlich):
- Gegeben: Entität mit id=42 und entityTypeId=`data-object` existiert
- Wenn: Lukas versucht via PUT `entityTypeId=application-component` zu setzen
- Dann: HTTP 422; `entityTypeId` wurde nicht geändert

**AC5** (Löschen entfernt Referenzen-Warnung):
- Gegeben: Entität id=42 wird von carries-data-Connection id=103 referenziert
- Wenn: Lukas löscht Entität id=42
- Dann: System warnt vor referenzierenden Connections; nach Bestätigung: Entität gelöscht; carries-data-Connection id=103 bekommt `targetEntityId=null` oder wird ebenfalls gelöscht (Kaskade TBD in ADR)

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Bestanden-Kriterium: AC1–AC5 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: UC-04 / REQ-032 (EntityType `data-object` muss im Metamodell existieren bevor Instanzen angelegt werden können)
- **Folgewirkungen**: REQ-061 (carries-data) referenziert Integer-IDs aus REQ-060

## Realisierungs-Hinweise

- `POST /api/v1/entities` mit `entityTypeId=data-object`
- Integer-ID: DB-Sequenz, instanzweit; kein UUID
- PropertyValue-Validierung: serverseitig gegen MetamodelConfiguration

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-26 | requirements-engineer | Initial draft |
