---
id: REQ-037
title: Solution-spezifische Metamodell-Erweiterung
type: functional
priority: should
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-04
  business_objects:
    - metamodel-configuration
    - solution
    - plateau
  business_rules: []
  stakeholders:
    - SH-03
    - SH-02
    - SH-04
    - SH-06
  concept:
    - concept/40-extensibility/14-erweiterbarkeit.md
    - concept/40-extensibility/15-schema-evolution.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-037: Solution-spezifische Metamodell-Erweiterung

## Aussage

Das System MUSS es ermöglichen, für eine [Solution](../../business-objects/solution.md) eine eigene `MetamodelConfiguration` mit `scope=solution` anzulegen, die das Instanz-Standardmetamodell erbt und um zusätzliche EntityTypes, Stereotypen und Constraint-Regeln erweitert; die Solution-Erweiterung MUSS einen eigenen `editMode` haben, der unabhängig vom `editMode` der Instanz-Konfiguration konfiguriert ist; Solution-eigene Typen DÜRFEN Instanz-Typen weder überschreiben noch entfernen.

## Begründung

Das Instanz-Standardmetamodell soll für Produktiv-Plateaus stabil und kontrolliert sein (Sperrmodus, REQ-035). Gleichzeitig muss ein Solution Architekt neue Typen erproben können, die für eine spezifische Initiative relevant sind, bevor sie als Standard eingeführt werden. Ohne Solution-Level-Scoping müssten Experimente entweder das gesperrte Standardmetamodell aufheben oder auf einer separaten Instanz stattfinden. Der Zwei-Ebenen-Ansatz (Instanz gesperrt, Solution editierbar) erlaubt beides gleichzeitig: Baseline-Stabilität und Innovationsfreiheit in Target-Plateaus.

## Kontext

Eine [Solution](../../business-objects/solution.md) beschreibt den Delta zwischen einem Baseline-[Plateau](../../business-objects/plateau.md) und einem Target-Plateau. Das effektive Metamodell einer Solution = Instanz-Typen (geerbt, read-only in diesem Kontext) ∪ Solution-eigene Typen (editierbar, sofern `editMode=gui-and-import`). Solution-eigene Typen sind ausschliesslich innerhalb dieser Solution sichtbar; sie erscheinen nicht im Instanz-Standardmetamodell und nicht in anderen Solutions. Sobald ein erprobter Typ bewährt ist, kann er per Import in das Instanz-Standardmetamodell übernommen werden (separater Governance-Schritt, nicht in diesem REQ).

## Typ-spezifische Felder

### Bei type=functional

**Eingaben**:
- Solution-ID (Referenz auf eine existierende [Solution](../../business-objects/solution.md))
- `editMode` für die Erweiterungs-Konfiguration: `gui-and-import` | `import-only`
- EntityTypeDefinitions, Stereotypes, ConstraintRules (wie in REQ-032/REQ-033 beschrieben, aber nur für den Solution-Scope)

**Verarbeitung**:

*Anlegen einer Solution-Erweiterungs-Konfiguration*:
1. Prüfen: Solution existiert und hat noch keine `metamodelExtensionId` (oder ersetzt bestehende)
2. Neue `MetamodelConfiguration` anlegen mit `scope=solution`, `solutionId`, `parentId` → Instanz-Konfiguration, `editMode` (frei wählbar)
3. Solution.`metamodelExtensionId` setzen

*Effektives Metamodell berechnen (bei Entitäts-Anlage innerhalb einer Solution)*:
1. Instanz-Konfiguration laden (built-in + Instanz-Custom-Typen)
2. Solution-Erweiterungs-Konfiguration laden (falls vorhanden)
3. Union bilden: alle Instanz-Typen + Solution-eigene Typen
4. Bei Namenskollision (Solution-Typ hat gleichen Namen wie Instanz-Typ) → Fehler beim Anlegen (BR-09: kein Überschreiben)

*Validierung neuer Typen in der Solution-Erweiterung*:
- Namens-Eindeutigkeit gegen built-in Typen + Instanz-Custom-Typen + bereits vorhandene Solution-Typen (BR-09)
- Basis-Typ (`extends`) muss in der effektiven Typliste enthalten sein (Instanz oder Solution)

**Ausgaben**:
- Solution-Erweiterungs-Konfiguration angelegt/aktualisiert
- Effektive Typliste für die Solution (Union aus Instanz + Solution)

**Fehlerfälle**:
- Solution-Typ hat gleichen Namen wie Instanz-Typ → Fehler "Typ 'X' ist im Instanz-Metamodell bereits definiert; Solution-Typen können Instanz-Typen nicht überschreiben"
- Solution-Erweiterungs-Konfiguration hat keinen gültigen `parentId` → Fehler
- `editMode=import-only` der Instanz hat keinen Einfluss auf Solution-Erweiterung → sie bleibt editierbar gemäss ihrem eigenen `editMode`

## Akzeptanzkriterien

**AC1**:
- Gegeben: Instanz-Metamodell mit `editMode=import-only`; Solution „Cloud-Experiment" ohne Erweiterungs-Konfiguration
- Wenn: Kurt für „Cloud-Experiment" eine neue Erweiterungs-Konfiguration mit `editMode=gui-and-import` anlegt und darin `CloudService` definiert
- Dann: ist `CloudService` im effektiven Metamodell von „Cloud-Experiment" verfügbar; das Instanz-Standardmetamodell bleibt unverändert und gesperrt; `CloudService` erscheint nicht in anderen Solutions

**AC2**:
- Gegeben: Solution „Cloud-Experiment" mit `CloudService` (Solution-Typ) und Instanz-Typ `ApplicationComponent`
- Wenn: ein Architekt eine neue Entität innerhalb „Cloud-Experiment" anlegt
- Dann: stehen beide Typen zur Auswahl: `ApplicationComponent` (aus Instanz) und `CloudService` (Solution-eigen)

**AC3**:
- Gegeben: Solution-Erweiterung mit `editMode=gui-and-import`
- Wenn: Kurt über die GUI einen neuen Typ hinzufügt
- Dann: ist das möglich, obwohl das Instanz-Metamodell `import-only` ist (unabhängige `editMode`-Ebenen)

**AC4**:
- Gegeben: Solution-Erweiterungs-Konfiguration soll einen Typ `ApplicationComponent` definieren (bereits als Instanz-Typ vorhanden)
- Wenn: Kurt speichert
- Dann: erscheint Fehler "Typ 'ApplicationComponent' ist im Instanz-Metamodell bereits definiert"; kein Eintrag angelegt

**AC5**:
- Gegeben: Solution hat keine Erweiterungs-Konfiguration (`metamodelExtensionId=null`)
- Wenn: ein Architekt innerhalb dieser Solution eine Entität anlegt
- Dann: stehen ausschliesslich Instanz-Typen zur Verfügung; kein Fehler

**AC6**:
- Gegeben: Solution-Erweiterung mit `editMode=import-only`
- Wenn: Kurt die Solution-Erweiterung über eine YAML-Datei aktualisiert (REQ-033)
- Dann: ist der Import möglich; GUI-Bearbeitung dieser Erweiterung ist blockiert

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: Instanz `import-only` + Solution `gui-and-import`; Typen anlegen; effektives Metamodell prüfen; Namenskollision; Solution ohne Extension
- [x] Bestanden-Kriterium: AC1–AC6 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-032 (GUI-Konfiguration, auf Solution-Scope anwendbar); REQ-033 (Import, auf Solution-Scope anwendbar); REQ-035 (Sperrmodus ist die Motivation für Solution-Level-Editierbarkeit); Solution BO muss existieren
- **Folgewirkungen**: Entitäts-Anlage-UC (künftig) muss Solution-Scope beachten und effektives Metamodell berechnen; Typ-Auswahl-Dropdown muss Instanz- und Solution-Typen unterscheidbar anzeigen
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne Solution-Level-Scoping können neue Typen nur erprobt werden, indem der Instanz-Sperrmodus vorübergehend deaktiviert wird – widerspricht dem Governance-Ziel von REQ-035
- Risiko 2: Ohne diesen Mechanismus ist Metamodell-Innovation nur per Code-Deployment möglich – hohe Hürde

## Trade-offs

- Solution-Typen können Instanz-Typen nicht überschreiben (BR-09): verhindert, dass eine Solution das Shared-Modell korrumpiert; reduziert aber die Anpassungsfreiheit pro Solution. Bewusste Governance-Entscheidung.
- Effektives Metamodell als Union: einfach zu berechnen, aber kann bei vielen Solutions zu unübersichtlicher Typliste führen. Mitigierung: UI zeigt Herkunft jedes Typs (Instanz vs. Solution) klar an.

## Realisierungs-Hinweise

- DB: `metamodel_configurations`-Tabelle bekommt `scope`, `parent_id`, `solution_id`-Spalten; Instanz-Konfiguration hat `scope=instance`, `parent_id=null`
- Effektives-Metamodell-API: `GET /solutions/{id}/effective-metamodel` → gibt Union zurück; gecacht, invalidiert bei Änderung beider Ebenen
- UI: Typ-Auswahl-Dropdown gruppiert nach „Standard (Instanz)" und „Solution-spezifisch (Solution-Name)"; Solution-eigene Typen mit Badge/Farbe hervorgehoben
- Import für Solution-Scope: `POST /solutions/{id}/metamodel/import` (analog zu Instanz-Import, aber scope=solution)

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
