# US-136: Strukturierungshilfe als Ordner ohne implizite Verbindung anlegen

**ID**: US-136
**Story Points**: 3
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Enterprise Architekt möchte ich im Browser eine Strukturierungshilfe (Ordner) anlegen können, unter die ich Komponenten einordnen kann, ohne dass dabei automatisch ArchiMate-Verbindungen entstehen — damit ich die Landschaft rein navigatorisch gliedern kann ohne semantischen Lärm im Modell zu erzeugen.

## Bezug

**Use Case**: [UC-13: Navigationsbaum verwalten](../use-cases/UC-13-navigationsbaum-verwalten.md)
**Requirements**: REQ-141, REQ-142
**Persona**: [SH-03: Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)
**ADR**: ADR-021

## Akzeptanzkriterien

**AC1**:
- Gegeben: Nutzer wählt im Browser-Kontext „Neue Strukturierungshilfe"
- Wenn: Name eingegeben und bestätigt
- Dann: Neuer Knoten mit Ordner-Icon (kein ArchiMate-Icon) erscheint unter „Komponenten"; `isStructuralGrouper=true` gesetzt

**AC2**:
- Gegeben: Strukturierungshilfe S und instanziierte Komponente K
- Wenn: K unter S eingeordnet wird
- Dann: K erscheint als Kind von S; unter „Verbindungen" erscheint KEINE neue Verbindung

**AC3**:
- Gegeben: Strukturierungshilfe S mit 3 Kindkomponenten
- Wenn: S gelöscht wird
- Dann: Die 3 Kindkomponenten werden auf Root-Ebene verschoben (kein Kaskadendelete); Warnung erscheint vor Löschung

## Technische Hinweise

- Strukturierungshilfe = ArchitectureEntity mit `isStructuralGrouper=true`; hat einen eigenen (nicht-ArchiMate) EntityType
- Ordner-Icon im Browser klar unterscheidbar von ArchiMate-Entitäts-Icons
- Strukturierungshilfen sind kein TOGAF- oder ArchiMate-Konzept — nur ein UI-Hilfsmittel
