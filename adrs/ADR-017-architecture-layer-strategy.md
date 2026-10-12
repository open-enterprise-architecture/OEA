# ADR-017: Architektur-Layer-Strategie – Fully Open in v1.0

**Status**: accepted
**Datum**: 2026-06-27
**Entscheider**: [Rigobert – Produkt Owner](../business-analysis/stakeholders/SH-09-rigobert-produkt-owner.md) (SH-09)
**Konsultiert**: Business Engineer
**Informiert**: ADR-016 (Persistenz), ADR-008 (GUI-Architektur)
**Supersedes**: –

## Kontext und Problem

OEA modelliert Unternehmensarchitektur als Netz von `ArchitectureEntity`-Instanzen (Elemente + Verbindungen). Jede Instanz gehört einem Metatyp (`EntityTypeDefinition`) an, der im Metamodell konfiguriert wird. Bisher enthält `EntityTypeDefinition` ein Feld `architectureLayerId`, das die Zugehörigkeit zu einer Architektur-Schicht angibt.

Offen war: **Sollen diese Layer vom System vorgegeben und erzwungen werden – oder bestimmt der Betreiber, welche Layer (wenn überhaupt) in seinem Modell existieren?**

Diese Frage entstand im Kontext der Plateau/Go-Live-Modellierung, weil schichtenspezifische Go-Live-Semantik (z.B. "Technology-Layer deployed, Application-Layer noch nicht") nur möglich ist, wenn Layer systemseitig bekannt sind.

## Entscheidungstreiber

- **Organisations-Diversität**: EA-Frameworks unterscheiden sich stark — TOGAF, ArchiMate, Zachman, domänengetriebene Modelle, reine Capability-Maps. Eine fixe Layer-Vorgabe schliesst Organisationen aus, die anders denken.
- **Refactoring-Risiko**: Systemseitig harte Layer-IDs sind ein Versprechen an alle Betreiber. Spätere Änderungen (z.B. 4. Data-Layer nachträglich einführen) brechen bestehende Installationen.
- **Konsistenz mit dem Metamodell-Konzept**: Das Metamodell ist bewusst konfigurierbar (EntityTypes, Domains, Stereotypes, Properties). Layer als einziges hartes System-Konzept wäre ein inkonsistenter Bruch.
- **Template-Ansatz als Alternative**: Opinionierte Starter-Konfigurationen können ArchiMate-3.0-Layer, Standard-EntityTypes und Standardregeln mitliefern — ohne das System zu zwingen.

## Betrachtete Optionen

### Option 1: Fully Open ✓

Layer sind keine System-Konzepte. Der Betreiber definiert im Metamodell, welche Layer (wenn überhaupt) existieren — als freie Kategorisierung, nicht als erzwungenes Konstrukt. OEA liefert Templates, die gängige Layer-Modelle vorbelegen.

- **Pro**: Maximale Flexibilität; keine Migrations-Risiken; konsistent mit dem restlichen Metamodell-Ansatz; kleinere KMU ohne formale EA-Disziplin können OEA ohne Layer-Overhead nutzen
- **Contra**: System kann keine layer-aware Validierungen anbieten (z.B. "Verbindung über Schichtgrenzen warnen"); Plateau-Go-Live kann keine schichtenspezifische Fortschrittsanzeige liefern; Qualität des Modells hängt stärker von Betreiber-Disziplin ab

### Option 2: Soft Layers

Kanonische Layer (`business`, `application`, `technology`) sind systemseitig definiert, aber optional. Betreiber kann sie ignorieren oder ergänzen, aber die IDs sind stabil.

- **Pro**: Stabile Referenz-IDs für Viewpoints und Business Rules; Standard-Viewpoints aus der Box; Plateau kann layer-sensitiv arbeiten
- **Contra**: Implizites Versprechen, dass diese drei Layer immer existieren; organisiationsspezifische Layer-Modelle müssen "on top" aufgesetzt werden; potentielle Verwirrung bei 4-Layer-Organisationen (Data als 4. Layer ist in ArchiMate gängig)

### Option 3: Hard Layers

Drei kanonische Layer sind Pflicht, nicht löschbar; Betreiber kann umbenennen und weitere hinzufügen.

- **Pro**: Höchste Vorhersagbarkeit für toolseitige Features; Impact-Analyse und Plateau-Diff können layer-sensitiv arbeiten
- **Contra**: Grösstes Refactoring-Risiko; zwingt auch KMU-Betreiber zu einem Framework, das sie nicht kennen; widerspricht dem Open-Source-Versprechen maximaler Adaptierbarkeit

### Option 4: Strict Layers

Hard Layers + systemseitig erzwungene Cross-Layer-Beziehungsregeln.

- **Pro**: Modell-Integrität ohne Konfigurationsaufwand
- **Contra**: OEA würde zu einem prescriptiven ArchiMate-Tool; scheidet für v1.0 aus

## Entscheidung

**Option 1: Fully Open.**

Layer sind in v1.0 kein System-Konzept. `EntityTypeDefinition.architectureLayerId` bleibt als optionales Freitext-Feld (oder Referenz auf betreiberdefinierte Layer-Definitionen im Metamodell), aber das System kennt keine kanonischen Layer-IDs und erzwingt keine Layer-Struktur.

### Konsequenz für das Datenmodell

`EntityTypeDefinition` behält `architectureLayerId` als optionalen Verweis — aber der Verweis zeigt auf betreiberdefinierte Layer-Einträge in der `MetamodelConfiguration`, nicht auf ein System-Enum:

```
MetamodelConfiguration
  └── layerDefinitions[]          ← betreiberdefiniert (z.B. "business", "application", "technology")
        id: string                ← freie ID, kein System-Enum
        name: string
        description: string

EntityTypeDefinition
  └── architectureLayerId: string | null  ← optional; Referenz auf layerDefinitions[].id
```

Entitäten, deren Metatyp kein `architectureLayerId` hat, sind layer-unabhängig. Gültig.

### Template-Strategie

OEA liefert vorkonfigurierte Starter-Templates:

| Template | Layer-Modell | Zielgruppe |
|---|---|---|
| **ArchiMate 3.0** | Business / Application / Technology | Enterprises mit TOGAF/ArchiMate-Governance |
| **Capability Map** | (kein Layer, nur Domains) | Strategische EA, KMU ohne Tech-Detail |
| **Blank** | (keine Voreinstellungen) | Maximale Freiheit |

Templates enthalten: Layer-Definitionen, Standard-EntityTypes, Standard-Stereotypes, optionale Business Rules für Cross-Layer-Constraints.

### Konsequenz für Plateau und Go-Live

Ein Plateau-Übergang (Go-Live) ist **schicht-agnostisch**: Er beschreibt den Übergang der gesamten Architektur (alle Entitäten, alle Layer wie vom Betreiber definiert) von Target zu Baseline. Schichtenspezifische Go-Live-Fortschrittsanzeigen sind **kein v1.0-Feature**; sie könnten in v2.0 als template-spezifische Erweiterung implementiert werden, sofern der Betreiber Layer definiert hat.

## Konsequenzen

### Positive Konsequenzen

- Kein Refactoring-Risiko bei Layer-Modelländerungen in der Zukunft
- KMU können OEA ohne EA-Framework-Kenntnisse einsetzen
- Konsistenz: das gesamte Metamodell ist betreibergesteuert
- Templates schaffen Einstieg ohne Entscheidungsdruck

### Negative Konsequenzen / Trade-offs

- Kein systemseitiger Schutz gegen Layer-Verletzungen (z.B. Technology-Entity direkt mit Business-Entity verbinden ohne Application-Zwischenschicht); muss als optionale Business Rule im Template definiert werden
- Standard-Viewpoints im Template können nicht vom System generiert werden — sie müssen im Template vorkonfiguriert sein
- Plateau-Fortschrittsanzeige per Layer nicht umsetzbar ohne Betreiber-Layer-Konfiguration

### Folgeentscheidungen / Offene Punkte

- **Template-Format**: Wie werden Templates distribuiert und importiert? (eigene ADR, wenn Walking Skeleton startet)
- **Business-Rule-Syntax**: Wie drückt ein Betreiber "Verbindung darf keine Layer-Grenzen überspringen" als Rule aus? (Teil der Metamodell-Konfiguration, noch nicht spezifiziert)
- **`architectureLayerId` in entity.md**: Abschnitt anpassen — Feld ist optionale Referenz auf betreiberdefinierte `layerDefinitions`, kein System-Enum

## Bezüge

**Verwandte ADRs**: [ADR-004](./ADR-004-reifikation-details.md) (Reifikation), [ADR-016](./ADR-016-persistenz-strategie.md) (Persistenz)

**Business Objects**: [architecture.md](../business-objects/architecture.md) (Betriebsmodi), [plateau.md](../business-objects/plateau.md), [metamodel-configuration.md](../business-objects/metamodel-configuration.md)

**Konzept**: §14 Erweiterbarkeit, §23 Offene Punkte
