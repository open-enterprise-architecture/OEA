---
id: REQ-133
title: Verschachtelung von DocumentItems (hierarchische Kapitelstruktur)
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
author: requirements-engineer
references:
  use_cases:
    - UC-09
  business_objects:
    - document-collection
  stakeholders:
    - SH-03
    - SH-04
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-133: Verschachtelung von DocumentItems (hierarchische Kapitelstruktur)

## Aussage

Das System MUSS die hierarchische Verschachtelung von DocumentItems innerhalb einer DocumentCollection ermöglichen. Ein DocumentItem KANN über das Feld `parentId` auf ein übergeordnetes DocumentItem derselben Collection verweisen (`parentId=null` = Wurzel-Kapitel).

**Regeln:**
- Maximale Tiefe: **5 Ebenen** (Ebene 0 = Wurzel, `parentId=null`)
- Zyklen sind verboten (BR-11); das System MUSS Zyklus-Prüfung bei jeder `parentId`-Änderung durchführen
- `sortOrder` gilt relativ zu Geschwister-Items desselben `parentId`
- Löschen eines Eltern-Items: Kinder-Items werden **mitgelöscht** (Cascade-Delete) — mit Warnhinweis vor Ausführung
- Verschieben: `parentId` und `sortOrder` eines Items können via Drag & Drop in der Kapitelnavigation geändert werden

## Begründung

Ohne Verschachtelung sind komplexe Dokumentationen (z.B. Arc42 §5 Bausteinsicht mit Teilsichten) als flache Liste kaum strukturierbar. Hierarchische Kapitel ermöglichen eine natürliche Gliederung analog zu Buchkapiteln (§1 → §1.1 → §1.1.1).

## Akzeptanzkriterien

**AC1** (Unterelement anlegen):
- Wenn: Nutzer in der Kapitelnavigation „Unterkapitel anlegen" wählt
- Dann: Neues DocumentItem mit `parentId` des ausgewählten Items wird erstellt; erscheint eingerückt in der Navigation

**AC2** (Tiefenbegrenzung):
- Wenn: Nutzer versucht, ein Unterkapitel auf Ebene 6 anzulegen
- Dann: Aktion wird verweigert mit Hinweis „Maximale Tiefe (5) erreicht"

**AC3** (Zyklus-Schutz):
- Wenn: `parentId` eines Items so gesetzt würde, dass ein Zyklus entsteht (Item A → B → A)
- Dann: Speichern verweigert; Fehlerhinweis

**AC4** (Cascade-Delete-Warnung):
- Wenn: Nutzer ein Item mit Kindern löscht
- Dann: Warnhinweis „Dieses Kapitel hat N Unterkapitel, die ebenfalls gelöscht werden"; Bestätigung erforderlich

**AC5** (Drag & Drop):
- Wenn: Nutzer ein Item in der Kapitelnavigation per Drag & Drop verschiebt
- Dann: `parentId` und `sortOrder` werden aktualisiert; Navigation zeigt neue Struktur sofort

## Abhängigkeiten

- **Voraussetzungen**: REQ-132 (DocumentItem-Grundstruktur)
- **Folgewirkungen**: –

## Realisierungs-Hinweise

- Zyklus-Check: iterativer Ancestor-Walk von `parentId` bis Wurzel; bei Fund der eigenen ID → Zyklus; O(Tiefe) ≤ O(5) — kein Graph-Problem
- Kapitelnavigation: Tree-Komponente mit Drag & Drop (z.B. vue-draggable-next); Ebenen-Einrückung visuell unterscheidbar

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
