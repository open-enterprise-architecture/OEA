# US-038: Solution mit Architektur-Vision anlegen

**ID**: US-038
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Solution Architekt möchte ich eine neue Solution mit Name und Architektur-Vision-Text anlegen können, damit die Änderungsinitiative in OEA einen Container hat und das Ziel sowie der Scope für alle Beteiligten klar benannt ist.

## Bezug

**Use Case**: [UC-05: Architektur-Vision einer Änderungsinitiative beschreiben](../use-cases/UC-05-architektur-vision-beschreiben.md)
**Persona**: [SH-04: Michael – Solution Architekt](../../business-analysis/stakeholders/SH-04-michael-solution-architekt.md)
**Requirement**: [REQ-038: Solution anlegen und Architektur-Vision beschreiben](../req/REQ-038-solution-anlegen.md)

## Akzeptanzkriterien

**AC1**:
- Gegeben: Michael ist eingeloggt und hat Solution-Architekt-Berechtigung; keine Plateaus vorhanden (Projekt-Modus)
- Wenn: er den Namen „ERP-Erweiterung Logistics" und eine Beschreibung eingibt und speichert
- Dann: existiert die Solution mit status=`draft`, `ownerId=Michael`, `fromPlateauId=null`, `toPlateauId=null`; sie ist in der Solution-Übersicht sichtbar

**AC2**:
- Gegeben: Eine Solution mit Name „ERP-Erweiterung Logistics" existiert bereits
- Wenn: Michael eine zweite Solution mit demselben Namen anlegen will
- Dann: erscheint ein Validierungsfehler „Name 'ERP-Erweiterung Logistics' ist bereits vergeben"; keine zweite Solution wird angelegt

**AC3** (Plateau-Modus):
- Gegeben: Instanz im Plateau-Modus; Plateau P0 (baseline) und P1 (target) existieren
- Wenn: Michael `fromPlateauId=P0` und `toPlateauId=P1` setzt und speichert
- Dann: Solution angelegt mit beiden Referenzen; beide null oder beide gesetzt (BR-01 erfüllt)

**AC4**:
- Gegeben: Michael gibt `fromPlateauId=P0` ein, lässt `toPlateauId` leer
- Wenn: er speichert
- Dann: Validierungsfehler „Beide Plateau-Referenzen müssen gesetzt oder beide leer sein" (BR-01); kein Objekt angelegt

**AC5**:
- Gegeben: Person ohne Solution-Architekt-Berechtigung
- Wenn: sie das Anlege-Formular aufruft
- Dann: 403 Forbidden; kein Zugriff auf das Formular

## Technische Hinweise

- Betroffene Komponenten: Solution-Übersicht (Liste + „Neue Solution"-Button), Solution-Erstellungsformular, Backend `POST /api/v1/solutions`
- Datenbank-Änderungen: neuer Datensatz in `solutions` (id, name, description, status=draft, owner_id, from_plateau_id nullable, to_plateau_id nullable, created_at, created_by)
- Plateau-IDs nur rendern wenn die Instanz im Plateau-Modus konfiguriert ist (Instanz-Level-Flag, noch TBD; für Projekt-Modus MVP weglassen)
- Name-Eindeutigkeitsprüfung: case-insensitiv, getrimmt

## Definition of Done

- [ ] Akzeptanzkriterien AC1–AC5 erfüllt
- [ ] Code-Review durch zweite Person
- [ ] Tests: Anlegen (Projekt-Modus), Namens-Kollision, BR-01-Verletzung, fehlende Berechtigung
- [ ] Linter und Type-Checks grün
- [ ] In Trace-Matrix eingetragen

## Abhängigkeiten

- Wartet auf: US-001 (Login), Rollen-Modell mit Solution-Architekt-Berechtigung
- Blockiert: US-039 (Ausgangsbasis setzt existierende Solution voraus), US-040–042 (EntityDeltas)

## Notizen

3 SP: einfaches Formular mit zwei Textfeldern und optionalen Plateau-Dropdowns. Hauptkomplexität liegt in der BR-01-Validierung und der Namens-Eindeutigkeitsprüfung. Plateau-Modus (AC3/AC4) kann initial mit Feature-Flag deaktiviert sein und in einer Folge-Story freigeschaltet werden.
