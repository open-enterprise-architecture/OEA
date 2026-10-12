---
id: UC-18
title: Continuum-Paket importieren
status: draft
priority: should
target_release: v1.0
complexity: medium
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
primary_actor: SH-03
secondary_actors:
  - SH-06
references:
  business_objects:
    - architecture-building-block
    - solution-building-block
    - architecture-pattern
    - reference-architecture
    - trm-category
    - person
    - role
  adrs:
    - adrs/ADR-002-continuum-repository.md
  concept:
    - concept/10-foundations/04-enterprise-continuum-trm.md
  related_use_cases:
    - UC-04
    - UC-17
    - UC-19
---

# UC-18: Continuum-Paket importieren

## Diagramm

```plantuml
@startuml UC-18-ContinuumImport
left to right direction
skinparam actorStyle awesome

actor "Kurt\n(Lead EA)" as Kurt
actor "Max\n(Operator)" as Max

rectangle "OEA – Continuum-Paket importieren" {
  usecase "Paket hochladen\n(JSON / YAML)" as UC_Upload
  usecase "Paket validieren\n& Vorschau anzeigen" as UC_Preview
  usecase "Import bestätigen" as UC_Confirm
  usecase "Built-in-Paket\naktivieren (z.B. TOGAF TRM)" as UC_Builtin
  usecase "Audit-Log schreiben" as UC_Audit
}

Kurt --> UC_Upload
Kurt --> UC_Builtin
Max --> UC_Upload

UC_Upload ..> UC_Preview : <<include>>
UC_Preview ..> UC_Confirm : <<include>>
UC_Confirm ..> UC_Audit : <<include>>
UC_Builtin ..> UC_Preview : <<include>>
@enduml
```

## Goal in Context

OEA-Instanzen müssen nicht bei null anfangen: das TOGAF Technical Reference Model, TM Forum Frameworx, branchenübliche Cloud-Design-Patterns (Azure, AWS) oder interne Unternehmensstandards können als vorgefertigte Continuum-Pakete importiert werden. Diese importierten Bausteine sind unveränderlich (`scope=imported`) und bilden die stabile Grundlage, auf der organisationsspezifische Erweiterungen aufbauen.

ADR-002 hat entschieden: ein Repository, scope-basiert. Continuum-Pakete sind JSON/YAML-Dateien mit ABBs, SBBs, Patterns, Reference Architectures und TRM-Kategorien, die über die API eingespielt werden.

## Persona und Story

**Primärer Akteur**: [Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)

**Weitere Beteiligte**: [Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-kmu.md) (führt den Import ggf. technisch durch; Kurt entscheidet inhaltlich)

**Story**: Als Lead Enterprise Architekt möchte ich TOGAF- und branchenspezifische Architektur-Bausteine als Paket in unsere OEA-Instanz laden, damit ich auf dieser validierten Grundlage unsere eigenen Standards aufbauen kann — ohne jeden Baustein manuell anlegen zu müssen.

## Trigger

1. Ersteinrichtung einer neuen OEA-Instanz: TOGAF TRM als Basispaket laden
2. Neues branchenspezifisches Framework wird eingeführt (z.B. TM Forum): Import des entsprechenden Pakets
3. OEA-internes Standard-Paket (built-in) wird mit einem OEA-Update ausgeliefert und soll aktiviert werden

## Vorbedingungen (Pre-Conditions)

- [ ] Kurt ist eingeloggt (UC-01) und hat Admin-Berechtigung (gleichwertig UC-02)
- [ ] Das Paket liegt als JSON/YAML-Datei vor oder ist als built-in Paket in OEA verfügbar
- [ ] Das Paket folgt dem OEA-Continuum-Paket-Schema (validierbare Struktur)

## Nachbedingungen (Post-Conditions)

### Bei Erfolg

- Alle Bausteine des Pakets sind in der Continuum-Bibliothek sichtbar; `scope=imported`, `sourcePackage=<Paketname>`
- Importierte Bausteine sind read-only (kein Bearbeiten, kein Löschen)
- Bereits existierende Bausteine mit identischer ID wurden nicht überschrieben (Konflikt-Handling, siehe A1)
- Import-Protokoll mit Anzahl neu importierter, übersprungener und fehlerhafter Einträge

### Bei Misserfolg

- Kein Baustein wurde importiert (atomarer Import); Fehlermeldung mit Zeilenangabe und Fehlerursache

## Hauptablauf (Basic Flow)

*Standardfall: Kurt importiert das TOGAF 10 TRM-Paket*

1. **Kurt**: öffnet die Continuum-Bibliothek, navigiert zu „Paket-Import"
2. **System**: zeigt zwei Optionen: „Eingebaute Pakete" (OEA-Standard, sofort aktivierbar) und „Datei hochladen" (eigenes oder externes Paket)
3. **Kurt**: wählt unter „Eingebaute Pakete" den Eintrag „TOGAF 10 – Technical Reference Model" und klickt „Vorschau"
4. **System**: zeigt eine Vorschau: Anzahl ABBs (47), SBBs (0 — TRM enthält nur Kategorien), Patterns (12), Reference Architectures (3), TRM-Kategorien (38); darunter eine Liste der Hauptkategorien
5. **Kurt**: klickt „Importieren"
6. **System**: führt den Import als atomare Transaktion durch; alle Bausteine erhalten `scope=imported`, `sourcePackage="TOGAF 10 TRM"`
7. **System**: zeigt Import-Protokoll: „47 ABBs importiert, 12 Patterns importiert, 38 TRM-Kategorien importiert — 0 Fehler"
8. **Kurt**: prüft die importierten ABBs in der Bibliothek; sie erscheinen mit Badge „Importiert – TOGAF 10 TRM"

## Alternative Abläufe (Alternative Flows)

**A1 – Konflikte bei Paket-Update (neuere Version)**

1. **Kurt**: importiert eine aktualisierte Version eines bereits teilweise vorhandenen Pakets
2. **System**: erkennt ID-Konflikte (Bausteine mit identischer ID bereits vorhanden) und zeigt eine Konflikt-Liste: „12 Einträge bereits vorhanden — Überschreiben oder überspringen?"
3. **Kurt**: wählt: Überspringen (default) oder Aktualisieren (nur für nicht-organisationsspezifisch veränderte Bausteine)
4. **System**: importiert nur neue Bausteine; bei „Aktualisieren" werden die konfliktierenden Einträge mit `scope=imported` neu gesetzt; organisationsspezifische Erweiterungen bleiben erhalten

**A2 – Datei-Upload (eigenes Paket)**

1. **Kurt**: wählt „Datei hochladen" und lädt eine `.json` oder `.yaml`-Datei hoch
2. **System**: validiert das Paket-Schema sofort nach Upload; bei Schema-Fehler: HTTP 422 mit Zeilenangabe
3. **Kurt**: sieht Vorschau und bestätigt den Import
4. **System**: importiert; `sourcePackage` = Dateiname oder im Paket angegebener Name

**A3 – Paket-Deaktivierung (Bausteine verbergen)**

1. **Kurt**: möchte ein importiertes Paket ausblenden (ohne Löschen — Referenzen bleiben gültig)
2. **System**: setzt alle Bausteine des Pakets auf „archiviert" (nicht sichtbar in der Bibliothek, aber über IDs noch referenzierbar)
3. **System**: zeigt Warnung: „N Entitäten referenzieren Bausteine dieses Pakets; die Referenzen bleiben gültig"

## Ausnahmen / Fehlerfälle (Exception Flows)

**E1 – Schema-Validierungsfehler**
- Bedingung: Das hochgeladene Paket verletzt das OEA-Continuum-Paket-Schema (fehlendes Pflichtfeld, falscher Enum-Wert)
- Erwartete Reaktion: HTTP 422 mit Fehler-Liste (Zeile, Feld, Fehlerbeschreibung); kein Baustein importiert
- Wiederaufnahme: Kurt korrigiert die Datei und lädt erneut hoch

**E2 – Zyklus in importierter ABB-Verfeinerungs-Hierarchie**
- Bedingung: Das Paket enthält einen Zyklus im ABB-`refines`-Graphen
- Erwartete Reaktion: Import abgebrochen; Fehlermeldung mit Zyklus-Beschreibung
- Wiederaufnahme: Paket-Anbieter kontaktieren oder Paket manuell korrigieren

## Datenfluss

| Schritt | Daten | Richtung |
|---|---|---|
| 3–4 | Paket-Metadaten, Vorschau (Zählungen, Kategorien) | System → Kurt |
| 5 | Bestätigung | Kurt → System |
| 6–7 | Import-Protokoll (importiert / übersprungen / fehlerhaft) | System → Kurt |

## Beteiligte Business Objects

| Business Object | Operation | Notiz |
|---|---|---|
| [architecture-building-block](../../business-objects/architecture-building-block.md) | create (bulk) | scope=imported; read-only nach Import |
| [solution-building-block](../../business-objects/solution-building-block.md) | create (bulk) | scope=imported |
| [architecture-pattern](../../business-objects/architecture-pattern.md) | create (bulk) | scope=imported |
| [reference-architecture](../../business-objects/reference-architecture.md) | create (bulk) | scope=imported |
| [trm-category](../../business-objects/trm-category.md) | create (bulk) | scope=imported; Hierarchie-Validierung |
| [person](../../business-objects/person.md) | read | `importedBy` im Protokoll |

## Business Rules

| Rule-ID | Aussage | Auslöser |
|---|---|---|
| BR-01 | Import ist atomar: entweder werden alle validen Bausteine importiert oder kein einziger | onImport |
| BR-02 | Importierte Bausteine sind unmittelbar nach Import read-only (`scope=imported`); keine nachträgliche Änderung des `scope`-Felds möglich | onImport |
| BR-03 | Zyklus-Freiheit im ABB-`refines`-Graphen wird vor dem Import geprüft; Paket mit Zyklus wird vollständig abgelehnt | onImport |
| BR-04 | Konflikte (ID bereits vorhanden) werden nicht automatisch überschrieben; Kurt entscheidet explizit (A1) | onImport |

## Akzeptanzkriterien

- [ ] Eingebaute Pakete (OEA-Standard) sind ohne Datei-Upload aktivierbar
- [ ] Vorschau zeigt Anzahl und Art der zu importierenden Bausteine vor Bestätigung
- [ ] Atomarer Import: bei Schema-Fehler kein Baustein importiert; Fehlerliste mit Zeilenangaben
- [ ] Importierte Bausteine haben `scope=imported` und sind read-only (kein Bearbeiten/Löschen in der UI)
- [ ] A1: Konflikt-Handling zeigt Liste der Konflikte; default = überspringen
- [ ] A2: Datei-Upload mit Schema-Validierung vor Vorschau
- [ ] Import-Protokoll nach Abschluss (importiert / übersprungen / fehlerhaft)

## Nicht im Scope

- **Bausteine manuell anlegen**: UC-17
- **TRM-SBB-Zuordnungen nach Import anpassen**: UC-19
- **Automatischer Update-Check auf neue Paketversionen**: deferred v2.0
