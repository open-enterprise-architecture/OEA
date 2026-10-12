---
id: REQ-142
title: Implizite Composition-Verbindung bei Verschachtelung instanziierter Komponenten
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-13
  business_objects:
    - entity
  stakeholders:
    - SH-03
    - SH-04
  adrs:
    - ADR-021
supersedes: []
superseded_by: []
---

# REQ-142: Implizite Composition-Verbindung bei Verschachtelung instanziierter Komponenten

## Aussage

Das System MUSS beim Einordnen einer instanziierten ArchitectureEntity (`isStructuralGrouper=false`) als Kind einer anderen instanziierten Entität automatisch eine ArchiMate-Composition-Verbindung anlegen (`autoCreated=true`, `source=parentEntityId`, `target=childEntityId`). Diese Verbindung MUSS in der Kategorie „Verbindungen" der Solution sichtbar sein. Beim Herauslösen MUSS die `autoCreated=true`-Verbindung automatisch gelöscht werden. Strukturierungshilfen (`isStructuralGrouper=true`) DÜRFEN KEINE implizite Verbindung erzeugen.

## Begründung

Die Hierarchie von Komponenten hat semantische Bedeutung in ArchiMate: ein Eltern-Kind-Verhältnis ist eine Composition-Beziehung. Diese Information soll nicht verloren gehen, wenn Nutzer Komponenten im Explorer einordnen. Die Unterscheidung zwischen Strukturierungshilfen (Ordner) und Instanzen (echte EA-Elemente) verhindert unerwünschte Verbindungen aus reinen Navigationsentscheidungen.

## Typ-spezifische Felder

**Eingaben**:
- Elternentität A: `isStructuralGrouper=false`, `isConnection=false`
- Kindentität B: `isStructuralGrouper=false`, `isConnection=false`
- B.`parentEntityId` wird auf A gesetzt

**Verarbeitung**:
1. Prüfung: Beide Entitäten `isStructuralGrouper=false`? → implizite Verbindung anlegen
2. Prüfung: Mindestens eine Entität `isStructuralGrouper=true`? → keine Verbindung
3. Anlegen: Neue ArchitectureEntity vom Typ `composition` mit `autoCreated=true`, `source=A.id`, `target=B.id`
4. Beim Herauslösen (B.`parentEntityId=null` setzen): autoCreated-Verbindung löschen
5. Manuelle Änderung der Verbindung (z.B. auf Aggregation umstellen) → `autoCreated=false`; überlebt das Herauslösen

**Ausgabe**:
- Neue Verbindung sichtbar unter „Verbindungen" mit Label „[auto] Composition: A → B"
- In der Verbindungs-UI klar als automatisch erstellt gekennzeichnet

## Akzeptanzkriterien

**AC1** — Implizite Verbindung erstellt:
- Gegeben: Entity A und B (`isStructuralGrouper=false`); B.parentEntityId wird auf A gesetzt
- Wenn: Speichern der Änderung
- Dann: Composition-Verbindung (autoCreated=true) zwischen A und B existiert in „Verbindungen"

**AC2** — Keine Verbindung bei Strukturierungshilfe:
- Gegeben: Strukturierungshilfe C (`isStructuralGrouper=true`) und Entity D
- Wenn: D.parentEntityId = C gesetzt
- Dann: Keine Verbindung wird angelegt

**AC3** — Verbindung gelöscht beim Herauslösen:
- Gegeben: AC1-Zustand (autoCreated-Verbindung existiert)
- Wenn: B.parentEntityId auf null gesetzt
- Dann: autoCreated-Verbindung automatisch entfernt

**AC4** — Manuell geänderte Verbindung überlebt:
- Gegeben: autoCreated-Verbindung aus AC1; Nutzer ändert Typ auf Aggregation (`autoCreated=false`)
- Wenn: B.parentEntityId auf null gesetzt
- Dann: Aggregation-Verbindung bleibt erhalten (autoCreated=false)

## Verifikationsmethode

- [ ] Methode: test (automatisiert)
- [ ] Test-Setup: Unit-Tests für Verbindungslogik; Playwright für UI-Sichtbarkeit
- [ ] Bestanden-Kriterium: AC1–AC4 alle grün

## Abhängigkeiten

- **Voraussetzungen**: ADR-021; REQ-141; `entity` BO mit `parentEntityId`, `isStructuralGrouper`, `autoCreated`

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft; abgeleitet aus ADR-021 |
