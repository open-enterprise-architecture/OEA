---
id: UC-17
title: Wiederverwendbare Architektur-Bausteine verwalten
status: draft
priority: must
target_release: v1.0
complexity: large
version: 0.1.0
created: 2026-06-28
last_updated: 2026-06-28
primary_actor: SH-03
secondary_actors:
  - SH-04
references:
  business_objects:
    - architecture-building-block
    - solution-building-block
    - architecture-pattern
    - reference-architecture
    - person
    - role
  adrs:
    - adrs/ADR-002-continuum-repository.md
  concept:
    - concept/10-foundations/04-enterprise-continuum-trm.md
  related_use_cases:
    - UC-04
    - UC-18
    - UC-19
    - UC-20
---

# UC-17: Wiederverwendbare Architektur-Bausteine verwalten

## Diagramm

```plantuml
@startuml UC-17-Continuum
left to right direction
skinparam actorStyle awesome

actor "Kurt\n(Lead EA)" as Kurt
actor "Michael\n(Solution Architekt)" as Michael

rectangle "OEA – Continuum-Bausteine verwalten" {
  usecase "Architecture Building Block\n(ABB) anlegen" as UC_ABB
  usecase "Solution Building Block\n(SBB) anlegen & zuordnen" as UC_SBB
  usecase "Architecture Pattern\nanlegen" as UC_Pattern
  usecase "Reference Architecture\nkomponieren" as UC_RefArch
  usecase "SBB-Governance-Status\nsetzen\n(deprecated / prohibited)" as UC_Status
  usecase "Continuum-Bibliothek\nbrowsen" as UC_Browse
}

Kurt --> UC_ABB
Kurt --> UC_SBB
Kurt --> UC_Pattern
Kurt --> UC_RefArch
Kurt --> UC_Status
Michael --> UC_Browse

UC_SBB ..> UC_ABB : <<include>>
UC_RefArch ..> UC_ABB : <<include>>
UC_SBB ..> UC_Status : <<extend>>
@enduml
```

## Goal in Context

Das TOGAF Enterprise Continuum trennt wiederverwendbare Blueprints von konkreten Organisations-Instanzen. Kurt als Lead Enterprise Architekt muss diese Blueprints aktiv aufbauen und pflegen: Architecture Building Blocks (ABBs) spezifizieren, was eine Komponente leisten muss; Solution Building Blocks (SBBs) dokumentieren, welche Produkte diese Anforderungen erfüllen; Architecture Patterns halten erprobte Strukturlösungen fest; Reference Architectures kombinieren Bausteine zu vollständigen Blueprints. Dieser UC deckt die gesamte Lebenszyklusverwaltung der Continuum-Bibliothek ab.

## Persona und Story

**Primärer Akteur**: [Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)

**Weitere Beteiligte**: [Michael – Solution Architekt](../../business-analysis/stakeholders/SH-04-michael-solution-architekt.md) (konsumiert die Bausteine beim Design von Solutions)

**Story**: Als Lead Enterprise Architekt möchte ich unsere organisationsspezifischen Architektur-Bausteine und Muster dokumentieren und pflegen, damit Solution Architekten auf eine kuratierte Bibliothek zugreifen und neue Lösungen auf bewährten Standards aufbauen können.

## Trigger

1. Eine neue Technologie-Kategorie soll standardisiert werden → ABB und evaluierte SBBs anlegen
2. Ein erprobtes Muster soll für die gesamte Organisation dokumentiert werden → Architecture Pattern anlegen
3. Eine branchenübliche Blueprint-Struktur soll als Vorlage für künftige Plateaus dienen → Reference Architecture komponieren
4. Ein SBB-Governance-Status ändert sich (Produkt-EOL, Sicherheitsproblem) → SBB auf `deprecated` oder `prohibited` setzen

## Vorbedingungen (Pre-Conditions)

- [ ] Kurt ist eingeloggt (UC-01) und hat die Rolle „EA-Manager" oder eine Rolle mit Schreibberechtigung auf die Continuum-Bibliothek
- [ ] Für Reference Architectures: die referenzierten ABBs müssen bereits existieren

## Nachbedingungen (Post-Conditions)

### Bei Erfolg

- Der neue Baustein (ABB / SBB / Pattern / Reference Architecture) ist in der Continuum-Bibliothek sichtbar
- Alle referenzierten Objekte (z.B. `refines`-ABB, `implements`-ABBs) sind korrekt verknüpft
- Scope-Schutz ist aktiv: `imported`- und `built-in`-Bausteine bleiben unveränderlich

### Bei Misserfolg

- Keine Änderung; Fehlermeldung mit konkretem Hinweis

## Hauptablauf (Basic Flow)

*Standardfall: Kurt legt einen neuen organisation-spezifischen ABB und den dazugehörigen SBB an*

1. **Kurt**: öffnet die Continuum-Bibliothek (Navigations-Bereich „Enterprise Continuum")
2. **System**: zeigt die Bibliothek gegliedert in vier Bereiche: ABBs, SBBs, Patterns, Reference Architectures; jeweils mit Filter nach `continuumLevel`, `maturityLevel`, `governanceStatus`
3. **Kurt**: klickt im ABB-Bereich auf „Neuer ABB"
4. **System**: öffnet das ABB-Formular mit Feldern: Name, Description, `continuumLevel`, `maturityLevel`, `governanceStatus`, Specifications (Markdown), optional `refines` (Dropdown bestehender ABBs), optional `industry`
5. **Kurt**: füllt das Formular aus: Name = „Unternehmens-IAM", `continuumLevel = organization`, `maturityLevel = established`, verfeinert `common-systems`-ABB „Identity Provider"
6. **System**: speichert den ABB; zeigt den ABB in der Verfeinerungs-Hierarchie unter „Identity Provider"
7. **Kurt**: navigiert zu „Neuer SBB"
8. **System**: öffnet das SBB-Formular: Name, Vendor, Version, Description, `continuumLevel`, `maturityLevel`, `governanceStatus`, `evaluationNotes`, Felder für `implements`-ABBs (Multi-Select)
9. **Kurt**: füllt aus: Name = „Keycloak", Vendor = „Red Hat", Version = „23.x", `governanceStatus = approved`, wählt implementierte ABBs: „Unternehmens-IAM" und „Identity Provider"
10. **System**: speichert den SBB; zeigt ihn in der ABB-Detailansicht unter „implementedBy"

## Alternative Abläufe (Alternative Flows)

**A1 – Architecture Pattern anlegen**

1. **Kurt**: wählt „Neues Pattern" im Pattern-Bereich
2. **System**: öffnet das Formular mit Feldern: Name, Problem (Markdown), Solution (Markdown), Applicability, Consequences, `maturityLevel`, Tags
3. **Kurt**: füllt das Pattern aus (z.B. „Strangler Fig" für Legacy-Ablösung)
4. **Kurt**: verknüpft das Pattern mit bestehenden Patterns über „Related Patterns" (Multi-Select)
5. **System**: speichert; Pattern ist für alle Nutzer in der Bibliothek sichtbar

**A2 – Reference Architecture aus ABBs und Patterns komponieren**

1. **Kurt**: wählt „Neue Reference Architecture"
2. **System**: öffnet das Formular; danach Multi-Select für `composedOf` (ABBs) und `basedOnPatterns` (Patterns)
3. **Kurt**: wählt 5 ABBs und 2 Patterns; setzt `continuumLevel = organization`, `governanceStatus = approved`
4. **System**: speichert; Reference Architecture erscheint als Blueprint für Plateau-Anlage (UC-11)

**A3 – SBB-Governance-Status ändern (Produkt-EOL)**

1. **Kurt**: sucht den SBB „Oracle Database 11g" in der Bibliothek
2. **Kurt**: öffnet den SBB und setzt `governanceStatus = prohibited`, trägt `evaluationNotes` ein (EOL Oktober 2023, keine Sicherheits-Updates mehr)
3. **System**: speichert; alle Entitäten mit `instanceOfSBBId = Oracle-11g-UUID` erhalten in der UI einen Hinweis „Verwendetes Produkt ist verboten; Migration erforderlich"

**A4 – ABB-Verfeinerungs-Hierarchie bearbeiten**

1. **Kurt**: bearbeitet einen bestehenden ABB
2. **Kurt**: ändert das `refines`-Feld (Dropdown: nur ABBs ohne Zyklusrisiko auswählbar)
3. **System**: prüft Zyklus-Freiheit (BR-03); bei Zyklus → HTTP 422 mit Hinweis

**A5 – `imported`-Baustein öffnen (read-only)**

1. **Kurt**: öffnet einen aus einem Continuum-Paket importierten ABB (erkennbar am Badge „Importiert")
2. **System**: zeigt alle Felder read-only; Bearbeiten- und Löschen-Buttons nicht sichtbar
3. **Kurt**: kann den Baustein lesen und als `refines`-Basis für eigene ABBs nutzen

## Ausnahmen / Fehlerfälle (Exception Flows)

**E1 – Zyklus in ABB-Verfeinerung**
- Bedingung: Kurt setzt `refines` auf einen ABB, der selbst (direkt oder indirekt) diesen ABB verfeinert
- Erwartete Reaktion: HTTP 422 „Verfeinerungs-Zyklus erkannt: [ABB-A] → [ABB-B] → [ABB-A]"; keine Änderung
- Wiederaufnahme: Kurt wählt einen anderen Basis-ABB

**E2 – Löschen eines ABBs mit abhängigen SBBs oder Entitäten**
- Bedingung: Kurt versucht, einen ABB zu löschen, der von SBBs referenziert oder von Entitäten als `conformsToABBId` gesetzt ist
- Erwartete Reaktion: Warnung mit Anzahl abhängiger SBBs und Entitäten; keine automatische Blockierung — Kurt entscheidet
- Wiederaufnahme: Kurt bereinigt die Referenzen vorher oder löscht trotz Warnung

**E3 – SBB ohne ABB-Referenz**
- Bedingung: Kurt speichert einen SBB ohne `implements`-ABBs
- Erwartete Reaktion: Governance-Warnung „SBB ohne ABB-Referenz ist nur ein Produktkatalog-Eintrag"; kein Block
- Wiederaufnahme: Kurt kann die ABB-Referenz später ergänzen

## Datenfluss

| Schritt | Daten | Richtung |
|---|---|---|
| 4 | ABB-Formular (Name, continuumLevel, maturityLevel, governanceStatus, specifications, refines) | System → Kurt |
| 5–6 | Ausgefülltes ABB-Formular | Kurt → System |
| 8 | SBB-Formular (Name, vendor, version, governanceStatus, implements[]) | System → Kurt |
| 9–10 | Ausgefülltes SBB-Formular | Kurt → System |

## Beteiligte Business Objects

| Business Object | Operation | Notiz |
|---|---|---|
| [architecture-building-block](../../business-objects/architecture-building-block.md) | create, read, update, delete | Kern-Objekt; Verfeinerungs-Hierarchie wird geprüft |
| [solution-building-block](../../business-objects/solution-building-block.md) | create, read, update, delete | Governance-Status steuert Warn-Verhalten bei Entitätsanlage |
| [architecture-pattern](../../business-objects/architecture-pattern.md) | create, read, update, delete | Problem/Lösung-Format |
| [reference-architecture](../../business-objects/reference-architecture.md) | create, read, update, delete | Komposition aus ABBs + Patterns |
| [person](../../business-objects/person.md) | read | `createdBy`-Referenz |
| [role](../../business-objects/role.md) | read | Schreibberechtigung prüfen |

## Business Rules

| Rule-ID | Aussage | Auslöser |
|---|---|---|
| BR-01 | `imported`- und `built-in`-Bausteine aller Typen sind read-only; kein Bearbeiten oder Löschen | onUpdate, onDelete |
| BR-02 | ABB-Verfeinerungs-Graph ist azyklisch (kein direkter oder indirekter Zyklus) | onABBCreate, onABBUpdate |
| BR-03 | SBB `governanceStatus=prohibited` erzeugt eine Blockier-Warnung bei Entitäts-Neuanlage (kein harter Block; Entscheidung liegt beim Architekten) | onEntityCreate |
| BR-04 | Reference Architecture `composedOf`-Liste darf nicht leer sein (BR-03 aus reference-architecture.md) | onCreate |

## Akzeptanzkriterien

- [ ] ABB anlegen: Formular mit continuumLevel, maturityLevel, governanceStatus, Verfeinerungs-Dropdown
- [ ] ABB-Verfeinerung: Zyklus-Erkennung und 422-Rückmeldung
- [ ] SBB anlegen: Multi-Select für implementierte ABBs; Governance-Status auswählbar
- [ ] SBB-Governance-Status auf `prohibited` setzen: betroffene Entitäten erhalten Hinweis
- [ ] Pattern anlegen: Problem/Lösung-Format; Related-Patterns verknüpfen
- [ ] Reference Architecture: aus ABBs und Patterns komponieren; als Blueprint für Plateau-Anlage sichtbar
- [ ] A5: `imported`-Bausteine als read-only mit Badge erkennbar
- [ ] E2: Löschen mit abhängigen Referenzen erzeugt Warnung, blockiert aber nicht

## Nicht im Scope

- **Continuum-Paket-Import**: UC-18
- **TRM-Konfiguration**: UC-19
- **Conformance-Analyse**: UC-20
- **Zuweisung von ABBs/SBBs zu Instanz-Entitäten**: Erweiterung der Entitäts-Bearbeitungsmaske (Erweiterung von UC-05/UC-06)
