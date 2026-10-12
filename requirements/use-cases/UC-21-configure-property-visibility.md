---
id: UC-21
title: Property-Sichtbarkeit konfigurieren
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
  - SH-02
references:
  business_objects:
    - metamodel-configuration
    - entity
    - role
    - person
  adrs: []
  concept:
    - concept/06-core-entity-types.md
    - concept/08-organisation-rollen-personen.md
  related_use_cases:
    - UC-04
    - UC-01
    - UC-06
---

# UC-21: Property-Sichtbarkeit konfigurieren

## Diagramm

```plantuml
@startuml UC-21-PropertyVisibility
left to right direction
skinparam actorStyle awesome

actor "Kurt\n(Lead EA)" as Kurt
actor "Michael\n(Domain Architekt)" as Michael
actor "Anna\n(Business Analyst)" as Anna

rectangle "OEA – Property-Sichtbarkeit konfigurieren" {
  usecase "Rollen-Sichtbarkeit\nkonfigurieren\n(role-based)" as UC_Role
  usecase "Connection-Scope\nkonfigurieren\n(domain-scoped)" as UC_Scope
  usecase "Property-Sichtbarkeit\ntesten" as UC_Test
  usecase "Sensible Properties\neinsehen (berechtigt)" as UC_Read
  usecase "Eingeschränkte Properties\nsehen (leer dargestellt)" as UC_Masked
}

Kurt --> UC_Role
Kurt --> UC_Scope
Kurt --> UC_Test
Michael --> UC_Read
Anna --> UC_Masked

UC_Role ..> UC_Test : <<extend>>
UC_Scope ..> UC_Test : <<extend>>
@enduml
```

## Goal in Context

Nicht alle Properties einer Entität dürfen für alle Nutzer sichtbar sein. Investitionskosten, Vertragskonditionen oder interne Bewertungen sind sensible Felder, die nur bestimmten Rollen zugänglich sein sollen. Zusätzlich soll ein Domänen-Architekt sensible Felder nur für Entitäten sehen, die seiner Domäne zugeordnet sind — die Domänen-Zugehörigkeit wird über Connections im Graphen sichergestellt. Die Konfiguration der Sichtbarkeit erfolgt im Metamodell pro `PropertyDefinition`, sodass kein Code-Eingriff nötig ist.

## Persona und Story

**Primärer Akteur**: [Kurt – Lead Enterprise Architekt](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architekt.md)

**Weitere Beteiligte**:
- [Michael – Solution Architekt](../../business-analysis/stakeholders/SH-04-michael-solution-architekt.md) (Domain Architect, erlebt die connection-basierte Einschränkung)
- [Anna – Business Analyst](../../business-analysis/stakeholders/SH-02-anna-business-analyst.md) (sieht eingeschränkte Felder leer)

**Story**: Als Lead Enterprise Architekt möchte ich pro Property im Metamodell festlegen, wer den Wert sehen und editieren darf — entweder rollenbasiert oder abhängig von der Domänen-Zugehörigkeit der Entität — damit sensible Daten wie Kosten nicht organisationsweit sichtbar sind.

## Trigger

1. Ein neues Property `investitionskostenPrognose` (EUR) wird im Metamodell ergänzt → Kurt schränkt die Sichtbarkeit auf Rollen `EA-Manager` und `Budget-Owner` ein
2. Der Domänen-Architekt soll `sicherheitseinstufung` nur für Entitäten seiner Domäne sehen → Kurt konfiguriert `connection-scoped` mit Connection-Typ `DomainAssignment`
3. Ein bisher öffentliches Feld soll nachträglich eingeschränkt werden → Bestehende Werte bleiben erhalten, werden aber für nicht-berechtigte Nutzer leer dargestellt

## Vorbedingungen (Pre-Conditions)

- [ ] Kurt ist eingeloggt (UC-01) und hat die Rolle `EA-Manager` oder eine Rolle mit Metamodell-Schreibberechtigung
- [ ] Das Metamodell ist nicht im `import-only`-Modus (REQ-035)
- [ ] Für `connection-scoped`: der verwendete Connection-Typ muss im Metamodell als `isConnection=true` definiert sein

## Nachbedingungen (Post-Conditions)

**Erfolg**:
- `PropertyDefinition.visibilityMode` ist gesetzt; `allowedRoles` bzw. `scopingConnectionType` korrekt befüllt
- Nicht-berechtigte Nutzer sehen das Feld ab sofort leer und können es nicht editieren
- Änderung ist im Audit-Log (REQ-034) festgehalten

**Misserfolg**:
- Konfiguration ungültig (z.B. leere `allowedRoles` bei `role-restricted`) → Fehlermeldung; kein Speichern

## Hauptszenario

1. Kurt öffnet den Metamodell-Editor (UC-04) und navigiert zu einem EntityType (z.B. `ApplicationComponent`)
2. Kurt wählt das Property `investitionskostenPrognose` zur Bearbeitung aus
3. Kurt setzt `visibilityMode = role-restricted` und wählt die Rollen `EA-Manager`, `Budget-Owner` aus der Rollenliste
4. Kurt speichert — das System validiert und persistiert die Konfiguration
5. Ab sofort gibt die Entity-API den Wert dieses Feldes nur noch an Nutzer mit den konfigurierten Rollen zurück; alle anderen erhalten `null` und das Feld ist im UI als leer und nicht editierbar markiert

## Alternativszenario: connection-scoped

3a. Kurt setzt `visibilityMode = connection-scoped` und wählt den Connection-Typ `DomainAssignment` als `scopingConnectionType`
3b. Zur Laufzeit prüft das System: Hat der anfragende Nutzer über seine Domänen-Zugehörigkeit (via `DomainAssignment`-Connection) einen Pfad zur angefragten Entität im Graphen?
3c. Ja → Feld sichtbar; Nein → Feld leer und nicht editierbar

## Ausnahmeszenario

4a. Kurt setzt `visibilityMode = role-restricted`, lässt aber `allowedRoles` leer → System zeigt Validierungsfehler: „Mindestens eine Rolle muss ausgewählt sein"
4b. Admin-Nutzer mit der Systemrolle `system-admin` erhalten **immer** Lesezugriff auf alle Properties, unabhängig vom `visibilityMode` (REQ-128; konfigurierbar)

## Akzeptanzkriterien

- **AC1** (Konfiguration): Kurt kann `visibilityMode` im Metamodell-Editor pro PropertyDefinition setzen; alle drei Modi (`public`, `role-restricted`, `connection-scoped`) sind wählbar
- **AC2** (Enforcement role-restricted): Ein Nutzer ohne konfigurierte Rolle sieht das Feld leer und kann es nicht editieren; kein Wert im API-Response
- **AC3** (Enforcement connection-scoped): Ein Domain Architekt sieht den Wert nur für Entitäten, zu denen er über den konfigurierten Connection-Typ erreichbar ist; für alle anderen bleibt das Feld leer
- **AC4** (Admin-Override): Ein Nutzer mit `system-admin`-Rolle sieht alle Properties unabhängig vom `visibilityMode`; der Override ist konfigurierbar
- **AC5** (Konsistenz Katalog): Im Katalog-View (UC-06) werden eingeschränkte Felder für nicht-berechtigte Nutzer ebenfalls leer angezeigt; Sortierung/Filterung auf eingeschränkten Feldern ist für diese Nutzer deaktiviert

## Anmerkungen

- Der Wert wird serverseitig gefiltert — das Frontend erhält nie den tatsächlichen Wert für nicht-berechtigte Nutzer (`null` statt des echten Werts)
- `connection-scoped` erfordert einen Graph-Traversal zur Laufzeit; dies hat Performance-Implikationen bei tiefer Graphstruktur (Caching empfohlen)
- `visibilityMode` gilt nur für Lese- und Schreibzugriff auf den Wert, nicht für die Existenz des Feldes (das Feld selbst ist für alle sichtbar, aber leer)
