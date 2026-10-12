---
id: UC-19
title: Technical Reference Model konfigurieren
status: draft
priority: must
target_release: v1.0
complexity: medium
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
primary_actor: SH-03
secondary_actors: []
references:
  business_objects:
    - trm-category
    - solution-building-block
    - entity
    - person
    - role
  adrs:
    - adrs/ADR-002-continuum-repository.md
  concept:
    - concept/10-foundations/04-enterprise-continuum-trm.md
  related_use_cases:
    - UC-17
    - UC-18
    - UC-20
---

# UC-19: Technical Reference Model konfigurieren

## Diagramm

```plantuml
@startuml UC-19-TRM
left to right direction
skinparam actorStyle awesome

actor "Kurt\n(Lead EA)" as Kurt

rectangle "OEA – Technical Reference Model konfigurieren" {
  usecase "TRM-Kategorie anlegen\n(hierarchisch)" as UC_Cat
  usecase "TRM-Kategorie\nbearbeiten" as UC_Edit
  usecase "SBB einer Kategorie\nzuordnen" as UC_Assign
  usecase "SBB-Governance-Status\nsetzen\n(preferred / acceptable /\ndeprecated / prohibited)" as UC_Status
  usecase "TRM-Übersicht\nanzeigen" as UC_View
}

Kurt --> UC_Cat
Kurt --> UC_Edit
Kurt --> UC_Assign
Kurt --> UC_Status
Kurt --> UC_View

UC_Cat ..> UC_Edit : <<extend>>
UC_Assign ..> UC_Status : <<include>>
@enduml
```

## Goal in Context

Das Technical Reference Model (TRM) ist die Technologie-Taxonomie einer Organisation: eine hierarchische Klassifikation aller Technology-Service-Kategorien mit klaren Standards dafür, welche Produkte bevorzugt, akzeptiert, auslaufend oder verboten sind. Ein gepflegtes TRM ermöglicht Technology-Governance: Architekten sehen auf einen Blick, welche Produkte für eine neue Entität zulässig sind, und Abweichungen vom Standard werden automatisch sichtbar.

Kurt muss das TRM initial aufbauen (ggf. auf Basis eines importierten TOGAF-TRM-Pakets), organisationsspezifische Kategorien ergänzen und die SBB-Governance-Zuordnungen pflegen. Dieser UC beschreibt diesen Konfigurationsprozess.

## Persona und Story

**Primärer Akteur**: [Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)

**Story**: Als Lead Enterprise Architekt möchte ich eine klare Technologie-Taxonomie mit verbindlichen Standards (preferred / acceptable / deprecated / prohibited) pro Kategorie pflegen, damit Solution Architekten bei der Komponentenwahl immer wissen, was organisatorisch erlaubt ist — und Abweichungen automatisch erkennbar sind.

## Trigger

1. Ersteinrichtung: importiertes TOGAF-TRM-Paket soll mit organisationsspezifischen SBB-Zuordnungen angereichert werden
2. Ein neues Produkt wurde evaluiert → SBB einer TRM-Kategorie als `approved` oder `acceptable` setzen
3. Ein Produkt ist EOL → SBB von `acceptable` auf `deprecated` oder `prohibited` hochstufen
4. Die Organisation erschliesst ein neues Technologie-Feld → neue TRM-Kategorie unterhalb einer bestehenden anlegen

## Vorbedingungen (Pre-Conditions)

- [ ] Kurt ist eingeloggt (UC-01) und hat die Rolle „EA-Manager"
- [ ] Das TRM enthält mindestens eine Wurzelkategorie (ggf. aus UC-18 importiert oder manuell angelegt)

## Nachbedingungen (Post-Conditions)

### Bei Erfolg

- TRM-Kategorie ist angelegt / bearbeitet / SBB-Zuordnungen aktualisiert
- `preferredStandard`, `acceptedAlternatives`, `deprecatedOptions` je Kategorie sind korrekt gesetzt
- `lastReviewedAt` ist aktualisiert
- Alle Entitäten, deren `instanceOfSBBId` von der Änderung betroffen ist, erhalten in der UI einen aktualisierten Governance-Hinweis

### Bei Misserfolg

- Keine Änderung; Fehlermeldung mit konkretem Hinweis

## Hauptablauf (Basic Flow)

*Standardfall: Kurt reichert eine importierte TRM-Kategorie mit SBB-Standards an*

1. **Kurt**: öffnet die TRM-Ansicht im Navigationsbereich „Enterprise Continuum → TRM"
2. **System**: zeigt den TRM als aufklappbaren Baum; importierte Knoten mit Badge „TOGAF 10"; eigene Erweiterungen ohne Badge; jede Kategorie zeigt: `preferredStandard` (oder „—"), Anzahl `acceptedAlternatives`, Anzahl klassifizierter Entitäten
3. **Kurt**: navigiert zu „Infrastructure Services → Data Management Services → Database Management"
4. **System**: zeigt die Kategorie-Detailansicht mit aktuellen Feldern: kein `preferredStandard` gesetzt, `acceptedAlternatives` leer, `deprecatedOptions` leer, `evaluationCriteria` leer, `lastReviewedAt` leer
5. **Kurt**: klickt „Bearbeiten"
6. **System**: öffnet das Bearbeitungsformular mit Dropdowns für `preferredStandard` (Einzelauswahl, nur SBBs aus dem System), `acceptedAlternatives` (Multi-Select), `deprecatedOptions` (Multi-Select), Freitextfeld `evaluationCriteria`
7. **Kurt**: wählt `preferredStandard = SBB „PostgreSQL 17"`, fügt `acceptedAlternatives = [SBB „MySQL 8.x"]`, `deprecatedOptions = [SBB „Oracle Database 11g"]` hinzu
8. **Kurt**: trägt Evaluations-Kriterien ein: „Open-Source bevorzugt; Lizenzkosten < 10k/Jahr; PostgreSQL-Kompatibilität erforderlich"
9. **Kurt**: klickt „Speichern" — `lastReviewedAt` wird automatisch auf heute gesetzt
10. **System**: speichert; aktualisiert Governance-Hinweise für alle Entitäten mit `instanceOfSBBId = Oracle-11g-UUID` (deprecated) und alle ohne SBB-Klassifizierung in dieser Kategorie (Warnung „kein SBB gesetzt")

## Alternative Abläufe (Alternative Flows)

**A1 – Neue TRM-Kategorie unterhalb einer bestehenden anlegen**

1. **Kurt**: öffnet eine bestehende Kategorie (z.B. „Security Services") und klickt „Unterkategorie hinzufügen"
2. **System**: öffnet das Kategorie-Formular: Name (Pflicht), Description, `evaluationCriteria`; `level` wird automatisch = parent.level + 1 gesetzt
3. **Kurt**: gibt Namen ein: „Zero Trust Network Access", klickt „Speichern"
4. **System**: fügt den neuen Knoten in die Hierarchie ein; `scope=organization`, kein `sourcePackage`

**A2 – TRM-Baumstruktur durch Drag & Drop reorganisieren**

1. **Kurt**: zieht eine eigene (`scope=organization`) Kategorie per Drag & Drop unter einen anderen Knoten
2. **System**: prüft: Ziel darf kein Nachkomme der zu verschiebenden Kategorie sein (BR-02); `level` wird automatisch angepasst
3. **System**: verschiebt den Knoten; `level`-Feld aller betroffenen Nachkommen wird aktualisiert

**A3 – Überprüfungs-Datum aktuell halten**

1. **Kurt**: öffnet die TRM-Übersicht und aktiviert den Filter „Zuletzt überprüft vor > 12 Monaten"
2. **System**: zeigt alle TRM-Kategorien, deren `lastReviewedAt` mehr als 12 Monate zurückliegt oder null ist
3. **Kurt**: öffnet jede Kategorie, prüft die SBB-Zuordnungen und setzt `lastReviewedAt` auf heute (auch ohne inhaltliche Änderung möglich)

## Ausnahmen / Fehlerfälle (Exception Flows)

**E1 – Zyklus beim Verschieben einer Kategorie**
- Bedingung: Kurt versucht eine Kategorie unter einen ihrer eigenen Nachkommen zu verschieben
- Erwartete Reaktion: Drop-Target visuell als ungültig markiert; API-Versuch → HTTP 422
- Wiederaufnahme: Kurt wählt ein anderes Ziel

**E2 – Bearbeiten einer `imported`-Kategorie**
- Bedingung: Kurt öffnet eine TRM-Kategorie mit `scope=imported`
- Erwartete Reaktion: Name und Hierarchie read-only; nur SBB-Zuordnungen (`preferredStandard`, `acceptedAlternatives`, `deprecatedOptions`) und `evaluationCriteria` sind editierbar (da dies organisationsspezifische Entscheidungen sind)
- Wiederaufnahme: Kurt kann die SBB-Zuordnungen trotz `imported`-Scope bearbeiten

**E3 – Löschen einer Kategorie mit zugeordneten Entitäten**
- Bedingung: Kurt versucht eine Kategorie zu löschen, der Entitäten zugeordnet sind (`classifiedEntities` nicht leer)
- Erwartete Reaktion: Warnung „N Entitäten sind dieser Kategorie zugeordnet"; kein automatischer Block
- Wiederaufnahme: Kurt bereinigt die Zuordnungen oder löscht trotz Warnung

## Datenfluss

| Schritt | Daten | Richtung |
|---|---|---|
| 2 | TRM-Baum (Kategorien, SBB-Standard-Status, Entitäten-Zählungen) | System → Kurt |
| 6 | Bearbeitungsformular (SBB-Dropdowns, evaluationCriteria) | System → Kurt |
| 7–9 | Ausgefülltes Formular | Kurt → System |
| 10 | Aktualisierte Governance-Hinweise für betroffene Entitäten | System intern |

## Beteiligte Business Objects

| Business Object | Operation | Notiz |
|---|---|---|
| [trm-category](../../business-objects/trm-category.md) | create, read, update, delete | Kern-Objekt; SBB-Zuordnungen und `lastReviewedAt` |
| [solution-building-block](../../business-objects/solution-building-block.md) | read | Als Auswahl-Optionen in den SBB-Dropdowns |
| [entity](../../business-objects/entity.md) | read | `trmClassificationIds` auf Entitäten; Governance-Hinweise |
| [person](../../business-objects/person.md) | read | `createdBy` |
| [role](../../business-objects/role.md) | read | Schreibberechtigung prüfen |

## Business Rules

| Rule-ID | Aussage | Auslöser |
|---|---|---|
| BR-01 | Kein Zyklus im `parent`-Graphen der TRM-Kategorien | onTRMCreate, onTRMUpdate |
| BR-02 | `level = parent.level + 1` muss konsistent sein | onTRMCreate, onTRMUpdate |
| BR-03 | Ein SBB darf in derselben Kategorie nicht gleichzeitig `preferredStandard` UND in `acceptedAlternatives` UND in `deprecatedOptions` sein | onTRMUpdate |
| BR-04 | `imported`-Kategorien: Hierarchie (Name, Parent) ist read-only; SBB-Zuordnungen und `evaluationCriteria` sind editierbar | onTRMUpdate |
| BR-05 | `lastReviewedAt` wird automatisch auf `today` gesetzt, wenn Formular mit Speichern abgeschlossen wird | onTRMUpdate |

## Akzeptanzkriterien

- [ ] TRM-Baum als aufklappbare Hierarchie; importierte Knoten mit Badge erkennbar
- [ ] Kategorie-Detailansicht: `preferredStandard`, `acceptedAlternatives`, `deprecatedOptions`, `evaluationCriteria`, `lastReviewedAt`
- [ ] `imported`-Kategorien: Name/Hierarchie read-only; SBB-Zuordnungen editierbar
- [ ] A1: Unterkategorie anlegen; `level` automatisch gesetzt
- [ ] A2: Drag & Drop; Zyklus-Erkennung visuell und per 422
- [ ] A3: Filter „Zuletzt überprüft vor > 12 Monaten" verfügbar
- [ ] E1: Zyklus beim Verschieben → Drop-Target ungültig; kein Save möglich
- [ ] Änderung an SBB-Governance-Zuordnung aktualisiert Hinweise auf betroffene Entitäten

## Nicht im Scope

- **Bausteine (ABBs, SBBs) anlegen**: UC-17
- **Paket-Import**: UC-18
- **Technology-Conformance-Analyse**: UC-20
