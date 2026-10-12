# US-135: Komponente im Browser hierarchisch unter eine andere einordnen

**ID**: US-135
**Story Points**: 5
**Status**: backlog
**Sprint**: nicht-zugeordnet

## Story

Als Solution Architekt möchte ich eine Komponente im Browser per Drag-and-Drop oder über die Properties unter eine andere Komponente einordnen können, damit die hierarchische Zusammensetzung von Systemen im Modell sichtbar ist und die Composition-Beziehung automatisch angelegt wird.

## Bezug

**Use Case**: [UC-13: Navigationsbaum verwalten](../use-cases/UC-13-navigationsbaum-verwalten.md)
**Requirements**: REQ-141, REQ-142
**Persona**: [SH-04: Michael – Solution Architekt](../../business-analysis/stakeholders/SH-04-michael-solution-architekt.md)
**ADR**: ADR-021

## Akzeptanzkriterien

**AC1**:
- Gegeben: Komponente B (instanziiert, `isStructuralGrouper=false`) und Komponente A
- Wenn: B per Drag-and-Drop unter A eingeordnet wird
- Dann: B erscheint als Kindknoten von A im Browser; automatische Composition-Verbindung (autoCreated=true) erscheint unter „Verbindungen"

**AC2**:
- Gegeben: Strukturierungshilfe (Ordner) C und Komponente D
- Wenn: D unter C eingeordnet wird
- Dann: D erscheint als Kindknoten von C; KEINE Verbindung wird angelegt

**AC3**:
- Gegeben: Komponente B ist Kind von A (mit autoCreated-Verbindung)
- Wenn: B aus der Hierarchie herausgelöst wird (per Drag auf Root-Ebene)
- Dann: B erscheint wieder auf Root-Ebene; autoCreated-Verbindung verschwindet

## Technische Hinweise

- `parentEntityId` auf Entity B gesetzt/gelöscht
- Drag-and-Drop als primäre Interaktion; Eltern-Zuweisung über Properties als Alternative
- Keine Rekursion: A kann nicht unter eines seiner Kinder eingeordnet werden (Zyklus-Validierung)
