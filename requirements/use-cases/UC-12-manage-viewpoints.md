---
id: UC-12
title: Viewpoint anlegen und verwalten
status: draft
priority: must
target_release: v1.0
complexity: medium
version: 0.1.0
created: 2026-06-27
last_updated: 2026-06-27
primary_actor: SH-03
secondary_actors:
  - SH-04
references:
  business_objects:
    - viewpoint
    - metamodel-configuration
    - person
    - role
  requirements:
    - requirements/req/REQ-058-viewpoint-export.md
    - requirements/req/REQ-059-viewpoint-import.md
  user_stories:
    - requirements/user-stories/US-078-viewpoint-exportieren.md
    - requirements/user-stories/US-079-viewpoint-importieren.md
  concept:
    - concept/20-entities/12-domain-sichten.md
  related_use_cases:
    - UC-04
    - UC-05
---

# UC-12: Viewpoint anlegen und verwalten

## Diagramm

```plantuml
@startuml UC-12-Viewpoint
left to right direction
skinparam actorStyle awesome

actor "Kurt\n(Lead EA)" as Kurt
actor "Michael\n(Solution Architekt)" as Michael

rectangle "OEA – Viewpoint verwalten" {
  usecase "User-defined Viewpoint\nanlegen" as UC_Create
  usecase "Viewpoint bearbeiten\n(EntityTypes / Notation)" as UC_Edit
  usecase "Viewpoint exportieren\n(JSON)" as UC_Export
  usecase "Viewpoint importieren\n(JSON)" as UC_Import
  usecase "Viewpoint löschen" as UC_Delete
  usecase "System-Viewpoints\neinsehen (read-only)" as UC_System
}

Kurt --> UC_Create
Kurt --> UC_Edit
Kurt --> UC_Export
Kurt --> UC_Import
Kurt --> UC_Delete
Kurt --> UC_System
Michael --> UC_System

UC_Create ..> UC_Edit : <<extend>>
@enduml
```

## Goal in Context

OEA liefert eine Reihe system-definierter Viewpoints (ArchiMate 3, UML, BPMN 2.0). Jede Organisation hat jedoch spezifische Sichtweisen, die sich nicht in Standard-Viewpoints abbilden lassen — etwa eine „Cloud Security View", eine „Domänen-Übersicht" oder ein kundenspezifisches BPMN-Layout. Der Lead Enterprise Architekt muss solche user-defined Viewpoints anlegen, konfigurieren und bei Bedarf exportieren oder importieren können, ohne das Metamodell zu duplizieren.

Ein Viewpoint legt fest: welche Entitäts- und Verbindungstypen in Diagrammen dieses Typs erscheinen dürfen, welche Notation verwendet wird (ArchiMate 3, UML oder BPMN 2.0) und wie die Elemente standardmässig gerendert werden (Notation-Element, Grösse).

## Persona und Story

**Primärer Akteur**: [Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)

**Weitere Beteiligte**: [Michael – Solution Architekt](../../business-analysis/stakeholders/SH-04-michael-solution-architekt.md) (nutzt Viewpoints in UC-05; kein aktiver Schritt in diesem UC)

**Story**: Als Lead Enterprise Architekt möchte ich Architektursichten definieren, die genau die Entitätstypen und die Notation zeigen, die für unsere Organisation relevant sind — damit Diagramme konsistent erstellt werden und nicht jedes Mal neu konfiguriert werden müssen.

## Trigger

1. Kurt möchte eine neue, organisationsspezifische Architektursicht einführen
2. Ein importierter Viewpoint-Satz soll für die Instanz übernommen werden (Import-Flow)
3. Ein bestehender user-defined Viewpoint muss angepasst werden (neue EntityTypes im Metamodell hinzugekommen)
4. Ein user-defined Viewpoint wird nicht mehr benötigt und soll gelöscht werden

## Vorbedingungen (Pre-Conditions)

- [ ] Kurt ist eingeloggt (UC-01) und hat die Rolle „EA-Manager" oder eine Rolle mit Metamodell-Berechtigung
- [ ] Die MetamodelConfiguration der Instanz enthält die EntityTypes und ConnectionTypes, die im neuen Viewpoint verwendet werden sollen (UC-04)
- [ ] Für den Import-Flow: eine gültige JSON-Datei mit Viewpoint-Definitionen liegt vor

## Nachbedingungen (Post-Conditions)

### Bei Erfolg (Anlegen)

- Ein neuer `ViewpointDefinition`-Eintrag mit `viewpointType=user-defined` existiert in der MetamodelConfiguration
- Der Viewpoint ist sofort für neue Diagramme auswählbar
- `createdBy` und `createdAt` sind gesetzt

### Bei Misserfolg

- Kein neues Objekt wurde persistiert; bestehende Viewpoints unverändert
- Fehlermeldung mit konkretem Hinweis (Validierungsfehler, Berechtigung, Import-Konflikt)

## Hauptablauf (Basic Flow)

*Standardfall: Kurt legt einen neuen user-defined Viewpoint an*

1. **Kurt**: navigiert zur Metamodell-Konfiguration → Bereich „Viewpoints" (`/settings/metamodel/viewpoints`)
2. **System**: zeigt alle Viewpoints in zwei Gruppen:
   - System-definierte Viewpoints (read-only; mit Icon gekennzeichnet)
   - User-defined Viewpoints (editierbar, löschbar)
3. **Kurt**: klickt „Neuen Viewpoint anlegen"
4. **System**: öffnet Erstellungsformular mit:
   - Name (Pflichtfeld; max. 255 Zeichen; muss innerhalb der Instanz eindeutig sein)
   - Beschreibung (optional; max. 1000 Zeichen)
   - Notation (Pflichtfeld; Auswahl: `ArchiMate 3`, `UML`, `BPMN 2.0`)
5. **Kurt**: füllt Basisfelder aus und klickt „Weiter"
6. **System**: zeigt die Typ-Auswahl-Seite:
   - Liste aller EntityTypes aus der MetamodelConfiguration (built-in + custom), gruppiert nach ArchitectureLayer (sofern konfiguriert)
   - Liste aller Connection-Typen (`isConnection=true`)
   - Kurt wählt per Checkbox, welche in diesem Viewpoint erlaubt sein sollen
7. **Kurt**: wählt EntityTypes und ConnectionTypes aus und klickt „Weiter"
8. **System**: zeigt die Notation-Mapping-Seite für jeden gewählten EntityType:
   - Pro Typ: Dropdown für `notationElement` (gefiltert nach gewählter Notation; zeigt nur passende Präfix-Elemente)
   - Felder für `defaultWidth` und `defaultHeight` (vorausgefüllt mit Empfehlungswerten)
   - Optional: `visualHint` (Farbe oder Icon-Override)
   - System-defined Typen haben bereits Standard-Mappings vorausgefüllt; custom Typen erhalten Fallback-Mapping
9. **Kurt**: prüft und passt Mappings an
10. **Kurt**: klickt „Viewpoint speichern"
11. **System**: validiert (BR-01 bis BR-07); speichert den Viewpoint; zeigt Erfolgsmeldung
12. **System**: neuer Viewpoint erscheint in der Liste der user-defined Viewpoints

## Alternative Abläufe (Alternative Flows)

**A1 – Bestehenden user-defined Viewpoint bearbeiten**

1. **Kurt**: klickt in der Viewpoint-Liste auf einen user-defined Viewpoint → „Bearbeiten"
2. **System**: öffnet Bearbeitungsformular mit vorausgefüllten Werten (identische Struktur wie Anlage-Flow)
3. **Kurt**: passt EntityTypes, ConnectionTypes oder Notation-Mappings an
   - Hinweis: wenn ein EntityType aus `allowedEntityTypes` entfernt wird, der in bestehenden Diagrammen genutzt wird, zeigt das System eine Warnung mit Anzahl betroffener Diagramme (kein Block, nur Information)
4. **Kurt**: speichert; System validiert und persistiert
5. Bestehende Diagramme, die diesen Viewpoint nutzen, werden beim nächsten Öffnen neu validiert (BR-05)

**A2 – User-defined Viewpoint löschen**

1. **Kurt**: klickt auf einen user-defined Viewpoint → „Löschen"
2. **System**: prüft, ob der Viewpoint von bestehenden Diagrammen genutzt wird
   - Wenn ja: Warnung „N Diagramme nutzen diesen Viewpoint. Diese Diagramme verlieren ihren Viewpoint-Bezug." (kein automatischer Rollback der Diagramme)
   - Wenn nein: direkte Bestätigung
3. **Kurt**: bestätigt die Löschung
4. **System**: entfernt den Viewpoint aus der MetamodelConfiguration; betroffene Diagramme referenzieren keinen gültigen Viewpoint mehr (UI zeigt Hinweis beim Öffnen dieser Diagramme)

**A3 – Viewpoints exportieren (REQ-058, US-078)**

1. **Kurt**: klickt „Viewpoints exportieren" in der Viewpoint-Übersicht
2. **System**: öffnet Auswahl-Dialog: alle Viewpoints, user-defined vorausgewählt; system-defined optional zuwählbar
3. **Kurt**: wählt zu exportierende Viewpoints und klickt „Exportieren"
4. **System**: generiert JSON-Datei im Viewpoint-Format und startet Download

Exportiertes Format (Beispiel):
```json
{
  "schemaVersion": "1.0",
  "exportedAt": "2026-06-27T10:00:00Z",
  "viewpoints": [
    {
      "id": "cloud-security-view",
      "name": "Cloud Security View",
      "notation": "archimate3",
      "viewpointType": "user-defined",
      "allowedEntityTypes": ["application-component", "security-zone"],
      "allowedConnectionTypes": ["data-flow"],
      "notationMappings": [...]
    }
  ]
}
```

**A4 – Viewpoints importieren (REQ-059, US-079)**

1. **Kurt**: klickt „Viewpoints importieren" und wählt eine JSON-Datei
2. **System**: validiert die Datei (BR-08: alle referenzierten EntityType-IDs müssen in der Instanz existieren)
   - Fehler bei unbekannten IDs: Liste der fehlenden Typen; kein Partial-Import
3. **System**: zeigt Vorschau: neue Viewpoints (werden angelegt), konfligierende Namen (Option: überschreiben oder umbenennen)
4. **Kurt**: bestätigt den Import
5. **System**: legt neue Viewpoints an; überschreibt bei Bestätigung bestehende user-defined Viewpoints gleichen Namens; system-defined Viewpoints werden nie überschrieben

## Ausnahmen / Fehlerfälle (Exception Flows)

**E1 – Name nicht eindeutig**
- Bedingung: Gewählter Name existiert bereits in der MetamodelConfiguration (BR-01)
- Erwartete Reaktion: Inline-Validierungsfehler am Namensfeld; Speichern blockiert
- Wiederaufnahme: Kurt wählt einen anderen Namen

**E2 – EntityType nicht in MetamodelConfiguration vorhanden**
- Bedingung: Kurt versucht einen EntityType zu referenzieren, der in der MetamodelConfiguration nicht (mehr) existiert (kann beim Importieren auftreten, BR-03/BR-08)
- Erwartete Reaktion: Validierungsfehler mit Liste der fehlenden IDs; kein Partial-Import
- Wiederaufnahme: Kurt legt den EntityType via UC-04 an oder passt die Import-Datei an

**E3 – Notation-Element-Präfix passt nicht zur Notation (BR-07)**
- Bedingung: Kurt wählt ein `notationElement` mit falschem Präfix (z.B. `bpmn:Task` in einem ArchiMate-Viewpoint)
- Erwartete Reaktion: Dropdown zeigt nur Elemente der gewählten Notation; Freitext-Eingabe wird beim Speichern mit Validierungsfehler abgelehnt
- Wiederaufnahme: Kurt wählt ein passendes Notation-Element

**E4 – Löschen eines system-defined Viewpoints versucht**
- Bedingung: Kurt versucht, einen Viewpoint mit `viewpointType=system-defined` zu löschen (BR-02)
- Erwartete Reaktion: Löschen-Button nicht sichtbar / deaktiviert für system-defined Viewpoints
- Wiederaufnahme: nicht nötig; UI verhindert die Aktion

**E5 – Fehlende Berechtigung**
- Bedingung: Eingeloggte Person hat keine Metamodell-Berechtigung
- Erwartete Reaktion: 403 Forbidden; Anlegen/Bearbeiten/Löschen nicht zugänglich
- Wiederaufnahme: Person wendet sich an Admin (UC-02)

## Datenfluss

| Schritt | Daten | Richtung | Bemerkung |
|---|---|---|---|
| 2 | Viewpoint-Liste (id, name, notation, viewpointType) | System → Kurt | Zwei Gruppen: system-defined, user-defined |
| 4–5 | Name, Beschreibung, Notation | Kurt → System | Basisfelder |
| 6–7 | allowedEntityTypes[], allowedConnectionTypes[] | Kurt → System | Checkbox-Auswahl aus MetamodelConfiguration |
| 8–9 | notationMappings[] (entityTypeId, notationElement, defaultWidth, defaultHeight) | Kurt → System | Pro gewähltem EntityType |
| 11 | ViewpointDefinition (vollständig) | System intern | Persistiert in MetamodelConfiguration |
| A3 | JSON-Export (Viewpoint-Definitionen) | System → Datei | Download |
| A4 | JSON-Import + Validierungsbericht | Datei → System | Kein Partial-Import bei Validierungsfehler |

## Beteiligte Business Objects

| Business Object | Operation | Notiz |
|---|---|---|
| [viewpoint](../../business-objects/viewpoint.md) | create, update, delete | Kern-Objekt; nur user-defined editierbar |
| [metamodel-configuration](../../business-objects/metamodel-configuration.md) | read, update | Viewpoints sind Teil der MetamodelConfiguration |
| [person](../../business-objects/person.md) | read | `createdBy`; Berechtigungsprüfung |
| [role](../../business-objects/role.md) | read | EA-Manager-Rolle erforderlich |

## Akzeptanzkriterien

- [ ] User-defined Viewpoint mit Name, Notation, allowedEntityTypes, allowedConnectionTypes anlegbar
- [ ] Notation-Mappings pro EntityType konfigurierbar (notationElement, defaultWidth, defaultHeight)
- [ ] Dropdown für notationElement zeigt nur Elemente der gewählten Notation (BR-07)
- [ ] E1: Doppelter Name wird mit Inline-Fehler abgelehnt (BR-01)
- [ ] System-defined Viewpoints sind nicht bearbeitbar und nicht löschbar (BR-02); Schaltflächen deaktiviert
- [ ] A1: Bestehender user-defined Viewpoint bearbeitbar; Warnung bei Entfernen eines genutzten EntityTypes
- [ ] A2: Löschen mit Warnung bei genutzten Viewpoints; Bestätigung erforderlich
- [ ] A3: Export erzeugt gültige JSON-Datei mit gewählten Viewpoints
- [ ] A4: Import validiert alle EntityType-IDs gegen MetamodelConfiguration (BR-08); kein Partial-Import bei Fehler; Vorschau vor Übernahme
- [ ] E5: Ohne Metamodell-Berechtigung kein Zugriff auf Anlegen/Bearbeiten/Löschen (403)

## Nicht im Scope

- **Diagramm-Instanzen anlegen/bearbeiten**: Diagramme sind Instanzen eines Viewpoints; ihre Verwaltung ist ein eigener UC (noch nicht angelegt)
- **System-defined Viewpoints anpassen**: bewusst nicht vorgesehen (BR-02); Betreiber legt user-defined Viewpoints als Ersatz/Ergänzung an
- **Viewpoint-Vererbung**: ein Viewpoint kann nicht von einem anderen erben; Typen werden immer explizit gewählt
- **Notation-Elemente erweitern**: der Notation-Element-Katalog (ArchiMate 3, UML, BPMN 2.0) ist in v1.0 fix; Erweiterung ist v2.0-Thema
