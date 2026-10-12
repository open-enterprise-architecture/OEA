---
id: REQ-141
title: Komponenten können im Browser hierarchisch verschachtelt werden
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
    - ADR-020
    - ADR-021
supersedes: []
superseded_by: []
---

# REQ-141: Komponenten können im Browser hierarchisch verschachtelt werden

## Aussage

Das System MUSS es ermöglichen, ArchitectureEntity-Elemente (`isConnection=false`) im Browser hierarchisch untereinander einzuordnen, indem ein Element einem anderen als Elternelement zugewiesen wird (`parentEntityId`). Die Hierarchie MUSS im Browser-Panel als verschachtelter Baum dargestellt werden. Strukturierungshilfen (`isStructuralGrouper=true`) KÖNNEN ebenfalls als Elternknoten fungieren, erzeugen aber keine implizite Verbindung (→ REQ-142).

## Begründung

Reale Unternehmensarchitekturen sind hierarchisch: Ein „ERP-System" enthält Module wie „Finanzmodul" und „Logistikmodul". Diese Hierarchie im Modell abzubilden und direkt im Navigator sichtbar zu machen, ist zentraler Mehrwert gegenüber flachen Listen.

## Typ-spezifische Felder

**Eingaben**:
- ArchitectureEntity mit `parentEntityId` gesetzt oder null
- `isStructuralGrouper: boolean`

**Verarbeitung**:
1. System zeigt unter „Komponenten" einer Solution alle Elemente mit `parentEntityId=null` als erste Ebene
2. Elemente mit `parentEntityId` werden als Kinder ihres Elternelements eingerückt
3. Strukturierungshilfen (`isStructuralGrouper=true`) erscheinen als Knoten ohne ArchiMate-Icon (Ordner-Icon stattdessen)
4. Instanziierte Elemente (`isStructuralGrouper=false`) erscheinen mit ArchiMate-Typ-Icon (klein)
5. Maximale Verschachtelungstiefe: 10 Ebenen
6. Zyklische Eltern-Kind-Referenzen sind verboten (BR-Validierung)

**Ausgabe**:
```
▼ Komponenten (6)
  ▼ [AC] SAP S/4HANA            ← instanziiert
      [AC] Finanzmodul           ← Kind, implizite Composition (REQ-142)
      [AC] Logistikmodul         ← Kind, implizite Composition
  ▼ [Ordner] Backend-Systeme     ← Strukturierungshilfe, kein Icon, keine Verbindung
      [AC] Auth-Service
  [AC] Portal-Frontend
```

## Akzeptanzkriterien

**AC1** — Verschachtelung dargestellt:
- Gegeben: Entity A hat `parentEntityId = Entity B`
- Wenn: „Komponenten"-Knoten aufgeklappt
- Dann: Entity A erscheint eingerückt unter Entity B

**AC2** — Strukturierungshilfen ohne ArchiMate-Icon:
- Gegeben: Entity C mit `isStructuralGrouper=true`
- Wenn: Browser angezeigt wird
- Dann: Entity C erscheint mit Ordner-Icon, kein ArchiMate-Icon

**AC3** — Flache Elemente auf oberster Ebene:
- Gegeben: Entity D mit `parentEntityId=null`
- Wenn: „Komponenten" aufgeklappt
- Dann: Entity D erscheint auf der ersten Einrückebene

## Verifikationsmethode

- [ ] Methode: test (automatisiert) + demonstration
- [ ] Test-Setup: Entities mit und ohne parentEntityId; verschachtelte Strukturierungshilfen
- [ ] Bestanden-Kriterium: AC1–AC3 alle grün

## Abhängigkeiten

- **Voraussetzungen**: ADR-021 (implizite Verbindung); `entity.md` Attribut `parentEntityId` und `isStructuralGrouper`
- **Folgewirkungen**: REQ-142 (Verbindungslogik für Instanzen)

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
