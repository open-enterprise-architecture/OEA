---
id: REQ-032
title: Custom EntityTypes via GUI konfigurieren
type: functional
priority: must
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
    - SH-03
    - SH-02
    - SH-07
  concept:
    - concept/40-extensibility/14-erweiterbarkeit.md
    - concept/20-entities/06-kern-entitaetstypen.md
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-032: Custom EntityTypes via GUI konfigurieren

## Aussage

Das System MUSS Personen mit Metamodell-Bearbeiter-Berechtigung ermöglichen, Custom EntityTypes (inkl. Properties, Relationen, optionalem Basis-Typ und Stereotypen) über ein grafisches Formular zu erstellen, zu bearbeiten und zu löschen; Built-in EntityTypes MÜSSEN sichtbar, aber NICHT editierbar oder löschbar sein.

## Begründung

Architekturteams müssen ihr Metamodell kontinuierlich an organisations-spezifische Anforderungen anpassen (neue Branchen-Typen, Governance-Anforderungen, technische Plattform-Typen). Eine GUI-basierte Konfiguration ohne Code-Deployment ermöglicht es, das Metamodell direkt im Tool zu pflegen, ohne Entwickler einzubinden. Der Ausschluss der Built-in-Typen (§6) von der Editierbarkeit schützt die konzeptionelle Integrität des TOGAF-basierten Kern-Metamodells.

## Kontext

Die GUI-Konfiguration ist der primäre Pfad für organisations-spezifische Metamodell-Erweiterungen. Alternativ steht Import (REQ-033) zur Verfügung. Beide Wege führen zum gleichen Ergebnis: ein validierter Eintrag in der `MetamodelConfiguration`. Die Konfiguration gilt instanzweit und ist sofort wirksam.

## Typ-spezifische Felder

### Bei type=functional

**Eingaben** (pro CRUD-Operation):

*Create EntityType*:
- Name (string, PascalCase, Pflicht)
- Basis-Typ (Dropdown aus allen EntityTypes, optional)
- Beschreibung (string, optional)
- Properties (0..n): Name, Typ (string/integer/float/boolean/date/enum/string[]), Pflichtfeld, EnumValues (bei type=enum), Beschreibung
- Relationen (0..n): Relationsname, Ziel-Typ (Dropdown), Kardinalität (Format: `0..n`, `1..1` etc.)

*Update EntityType* (nur Custom):
- Alle Felder wie bei Create editierbar
- Properties/Relationen können hinzugefügt, geändert oder entfernt werden

*Delete EntityType* (nur Custom):
- Löschen nur möglich, wenn keine Entitäts-Instanzen dieses Typs im Repository vorhanden (BR-03)

*Create/Update/Delete Stereotype*:
- Stereotyp-Name (PascalCase), Basis-Typ (Dropdown), zusätzliche Properties

**Verarbeitung**:
- Validierung: Name eindeutig (über built-in + custom), Basis-Typ gültig, Kardinalitäts-Format korrekt, Enum-Values nicht leer bei type=enum
- Bei Delete: Prüfung ob Typ noch Instanzen hat (BR-03); falls ja → Fehlermeldung mit Anzahl
- Persistierung als Eintrag in `MetamodelConfiguration`
- Audit-Log-Eintrag nach erfolgreicher Operation

**Ausgaben**:
- Aktualisierte EntityType-Liste mit dem neuen/geänderten/gelöschten Typ
- Audit-Log-Eintrag

**Fehlerfälle**:
- Name-Kollision (BR-01) → Validierungsfehler mit konkreter Meldung
- Versuch, Built-in zu editieren/löschen (BR-02) → Fehlermeldung
- Versuch, belegten Custom-Typ zu löschen (BR-03) → Fehlermeldung mit Anzahl betroffener Entitäten
- Fehlende Berechtigung → 403 Forbidden

## Akzeptanzkriterien

**AC1**:
- Gegeben: Kurt hat Metamodell-Bearbeiter-Berechtigung
- Wenn: er einen neuen EntityType `SecurityZone` mit Property `trustLevel` (enum, pflicht) und Relation `contains → TechnologyComponent (0..n)` über das Formular anlegt
- Dann: erscheint `SecurityZone` in der Typ-Liste; beim Anlegen einer neuen Entität ist `SecurityZone` auswählbar

**AC2**:
- Gegeben: Kurt versucht, einen EntityType mit dem Namen `ApplicationComponent` (built-in) anzulegen
- Wenn: er speichert
- Dann: erscheint eine Fehlermeldung „Name 'ApplicationComponent' bereits vergeben (built-in)"; kein Eintrag wird angelegt

**AC3**:
- Gegeben: Custom EntityType `SecurityZone` hat 5 Entitäts-Instanzen im Repository
- Wenn: Kurt versucht, `SecurityZone` zu löschen
- Dann: erscheint eine Fehlermeldung mit „5 Entitäten vom Typ SecurityZone müssen zuerst gelöscht oder migriert werden"; kein Löschen

**AC4**:
- Gegeben: Kurt bearbeitet einen Custom EntityType (fügt ein optionales Property hinzu)
- Wenn: er speichert
- Dann: ist das neue Property sichtbar; bestehende Entitäten dieses Typs werden nicht invalide (optionales Property, kein Breaking Change)

**AC5**:
- Gegeben: Built-in EntityType `ApplicationComponent` in der Liste
- Wenn: Kurt darauf klickt
- Dann: wird er als schreibgeschützt dargestellt; keine Edit- oder Delete-Buttons

## Verifikationsmethode

- [x] Methode: test (automatisiert) + exploration (UI-Test)
- [x] Test-Setup: EntityType anlegen, bearbeiten, löschen (mit und ohne Instanzen); Built-in-Editierversuch; Name-Kollision
- [x] Mess-Werkzeug: Test-Suite des Metamodell-Moduls; E2E-Test für UI-Flows
- [x] Bestanden-Kriterium: AC1–AC5 grün
- [ ] In CI integriert: ja, sobald Implementation existiert

## Abhängigkeiten

- **Voraussetzungen**: Rollen-Modell (Person mit Metamodell-Bearbeiter-Berechtigung muss existieren); MetamodelConfiguration BO
- **Folgewirkungen**: Newly defined EntityTypes müssen im Entitäts-Anlege-Dialog (künftiger UC-05/UC-06) auswählbar sein
- **Konflikte**: keine bekannt

## Risiken bei Nichterfüllung

- Risiko 1: Ohne GUI-Konfiguration muss das Metamodell per Code-Deployment angepasst werden – hohe Hürde für Architekturteams ohne Entwicklerzugang, schwerwiegend
- Risiko 2: Editierbarkeit von Built-in-Typen würde die Kern-Integrität gefährden und potentiell Instanzen korrupieren, schwerwiegend (durch BR-02 mitigiert)

## Trade-offs

- Optimistic Locking für gleichzeitige Bearbeitung: einfach zu implementieren, aber bei Kollision verliert einer der Architekten seine Änderungen. Für Metamodell-Änderungen (selten, im Team abgesprochen) vertretbar.

## Realisierungs-Hinweise

- Backend: `POST/PUT/DELETE /admin/metamodel/entity-types` (admin-only, RBAC)
- Frontend: Liste mit integrierten Built-in-Typen (read-only Badge) + Custom-Typen (Edit/Delete-Icons); Formular-Komponente für Properties und Relationen (dynamisch hinzufügbar/entfernbar)
- Validierung: clientseitig für UX-Feedback, serverseitig für Sicherheit

## Realisierung

- ADR(s): –
- Implementiert durch: –
- Tests: –
- Status der Verifikation: not-verified

## Änderungshistorie

| Version | Datum | Autor | Änderung |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
