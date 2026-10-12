---
id: REQ-035
title: Metamodell-Sperrmodus (Import-Only)
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
  business_rules: []
  stakeholders:
    - SH-06
    - SH-03
  concept:
    - concept/40-extensibility/14-erweiterbarkeit.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-035: Metamodell-Sperrmodus (Import-Only)

## Aussage

Der Betreiber MUSS die `MetamodelConfiguration` einer Instanz in den Modus `import-only` versetzen können; in diesem Modus DÜRFEN Create-, Update- und Delete-Operationen auf EntityTypeDefinitions, Stereotypes und ConstraintRules über die GUI-API NICHT ausgeführt werden – unabhängig von der Berechtigung der anfragenden Person; Änderungen sind ausschliesslich über den Import-Pfad (REQ-033) möglich.

## Begründung

Organisationen mit strengen Governance-Anforderungen oder GitOps-Prozessen wollen das Metamodell als versionierten Code in einem Repository verwalten. Ein manuell über die GUI editiertes Metamodell wäre in diesem Fall eine unerwünschte Drift-Quelle ausserhalb des kontrollierten Change-Prozesses. Der Sperrmodus stellt sicher, dass das YAML-File die einzige Quelle der Wahrheit bleibt und alle Metamodell-Änderungen denselben Review-Prozess durchlaufen wie Code. Adressiert SH-06s Anforderung an kontrollierte, nachvollziehbare Infrastrukturveränderungen.

## Kontext

Der `editMode` ist eine Betreiber-Einstellung (nicht durch Architekten änderbar) in der `MetamodelConfiguration`. Der Import-Pfad (REQ-033) bleibt im Sperrmodus vollständig aktiv – er ist der einzige Weg zur Änderung. Die GUI zeigt im Sperrmodus alle EntityTypes weiterhin an (read-only), aber Create/Edit/Delete-Buttons sind deaktiviert oder ausgeblendet. Der `editMode` des Sperrmodus selbst (Wechsel zwischen `gui-and-import` und `import-only`) ist ausschliesslich über die Betreiber-Konfiguration änderbar (Admin-UI oder Config-Datei), nicht über dieselbe GUI, die der Sperrmodus blockiert.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben** (Betreiber-Konfiguration):

| Feld | Typ | Default | Beschreibung |
|---|---|---|---|
| `editMode` | enum | `gui-and-import` | `gui-and-import` = GUI und Import erlaubt; `import-only` = nur Import erlaubt |

**Verarbeitung bei `editMode=import-only`**:
- Eingehende API-Requests an `POST/PUT/DELETE /admin/metamodel/entity-types`, `.../stereotypes`, `.../constraint-rules` → 403 Forbidden mit Meldung: "Metamodell-Bearbeitung via GUI ist deaktiviert (import-only-Modus). Änderungen nur über Import möglich."
- Import-Endpunkt (`POST /admin/metamodel/import`) bleibt vollständig aktiv
- GUI: Create/Edit/Delete-Aktionen für EntityTypes, Stereotypes und Constraints werden ausgeblendet oder deaktiviert; Hinweistext "Metamodell im Sperrmodus – Änderungen nur per Import" erscheint
- Lesende Operationen (GET) sind uneingeschränkt weiterhin möglich
- `editMode`-Wechsel: ausschliesslich über `PUT /admin/config/metamodel-edit-mode` (Betreiber-Endpunkt, eigene RBAC-Ebene)

**Fehlerfälle**:
- Versuch, GUI-Änderung im Sperrmodus durchzuführen → 403 mit klarer Meldung (kein 404 oder 500)
- Versuch, `editMode` über die reguläre Metamodell-API zu ändern → 400 Bad Request

## Akzeptanzkriterien

**AC1**:
- Gegeben: `editMode=import-only`
- Wenn: Kurt versucht, über die GUI einen neuen EntityType anzulegen (REQ-032)
- Dann: wird die Anfrage mit 403 und Hinweis auf den Import-Pfad abgelehnt; die MetamodelConfiguration bleibt unverändert

**AC2**:
- Gegeben: `editMode=import-only`
- Wenn: Kurt eine YAML-Datei via Import (REQ-033) hochlädt und bestätigt
- Dann: wird der Import normal verarbeitet; neue EntityTypes sind in der Konfiguration

**AC3**:
- Gegeben: `editMode=import-only`
- Wenn: Kurt die MetamodelConfiguration-Übersicht aufruft
- Dann: sieht er alle EntityTypes (read-only); Create/Edit/Delete-Buttons fehlen oder sind deaktiviert; ein Hinweistext erklärt den Sperrmodus

**AC4**:
- Gegeben: `editMode=gui-and-import` (Default)
- Wenn: keine explizite Konfiguration vorgenommen wurde
- Dann: sind GUI- und Import-Bearbeitung beide aktiv (kein Unterschied zu REQ-032/REQ-033-Verhalten)

**AC5**:
- Gegeben: Betreiber ändert `editMode` von `import-only` auf `gui-and-import`
- Wenn: Kurt danach einen EntityType über die GUI anlegt
- Dann: ist das wieder möglich; kein Neustart nötig (Konfiguration wird zur Laufzeit ausgewertet)

## Verifikationsmethode

- [x] Methode: test (automatisiert)
- [x] Test-Setup: `editMode` setzen; GUI-Operationen versuchen (erwarte 403); Import-Operation (erwarte Erfolg); Umschalten auf `gui-and-import` (erwarte GUI-Operationen wieder möglich)
- [x] Bestanden-Kriterium: AC1–AC5 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: REQ-032 (GUI-API, die blockiert wird); REQ-033 (Import-Pfad, der aktiv bleibt)
- **Folgewirkungen**: UC-04 A5 (kollaborative Bearbeitung): bei `import-only` werden alle manuellen Änderungen verhindert
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne Sperrmodus können Architekten das per CI/CD verwaltete Metamodell manuell überschreiben – Drift vom versionierten YAML, mittlerer bis schwerwiegender Schweregrad in GitOps-Setups

## Trade-offs

- `import-only` erhöht die Hürde für schnelle Korrekturen (kein schnelles GUI-Fix mehr möglich) – bewusster Governance-Trade-off; der Betreiber entscheidet, ob der Kontrollgewinn die reduzierte Agilität rechtfertigt.

## Realisierungs-Hinweise

- Middleware/Guard: bei jeder schreibenden Metamodell-API-Anfrage `editMode` aus `MetamodelConfiguration` lesen; bei `import-only` → 403; kein Durchreichen an Service-Layer
- `editMode`-Wechsel: eigener Admin-Endpunkt mit eigener RBAC-Prüfung (Betreiber-Rolle, nicht Metamodell-Bearbeiter-Rolle)
- Frontend: `editMode` als globale UI-State-Variable; Komponenten rendern conditional auf Basis dieses Flags

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
