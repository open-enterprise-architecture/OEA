## 11. Temporales Modell

Dies ist das konzeptionelle Herzstück des Tools.

### 11.1 Plateau als zeitlicher Anker

Ein **Plateau** (Begriff aus ArchiMate) repräsentiert einen konsistenten Architekturzustand zu einem Zeitpunkt oder für einen Zeitraum.

```
Plateau {
  id: "baseline-2026-q1"
  name: "Ist-Zustand Q1 2026"
  type: baseline | transition | target
  validFrom: 2026-01-01
  validTo: 2026-03-31
  description: "..."
}
```

Typische Plateau-Struktur eines Unternehmens:

- **1 Baseline** ("Ist-Zustand heute")
- **1–n Transition Plateaus** (Zwischenzustände, z.B. "nach Migration Phase 1")
- **1–n Target Plateaus** (Zielbilder, z.B. "Ziel 2028", "Langfrist-Vision 2030")

### 11.2 Entity Lifecycle pro Plateau

Jede Entität hat **pro Plateau** einen Zustand:

| Lifecycle State | Bedeutung |
|---|---|
| `planned` | Geplant, noch nicht umgesetzt |
| `under-construction` | In Umsetzung |
| `active` | Produktiv / im Einsatz |
| `sunset` | Abzulösen, noch aktiv |
| `retired` | Stillgelegt |
| `not-applicable` | In diesem Plateau nicht existent |

Entitäten werden **nicht gelöscht**, wenn sie abgelöst werden – sie bekommen einen Lifecycle-State pro Plateau. Das erhält Historie und ermöglicht Diffs.

### 11.3 Gaps und Work Packages

Ein **Gap** ist die Differenz einer Entität (oder Gruppe) zwischen zwei Plateaus. Gaps sind:

- **berechenbar** aus Lifecycle-States und Properties-Differenzen, oder
- **explizit modelliert**, wenn sie eine eigene fachliche Bedeutung haben

Ein **Work Package** schließt einen oder mehrere Gaps:

```
WorkPackage {
  id: "wp-crm-migration"
  name: "CRM-Ablösung Salesforce → HubSpot"
  closesGaps: [gap-001, gap-002]
  dependsOn: [wp-identity-migration]
  plannedStart: 2026-06-01
  plannedEnd: 2027-03-31
  deliverables: [...]
}
```

### 11.4 Zielbilder – Struktur

Ein Zielbild ist **kein neuer Entitätstyp**, sondern eine **Sicht auf Entitäten mit Zustand im Target-Plateau**, angereichert um Motivations-Entitäten:

- **Goal** (strategisches Ziel)
- **Outcome** (messbares Ergebnis)
- **Principle** (Leitlinie)
- **Driver** (externer/interner Treiber)
- **Assessment** (Bewertung des Ist)

Die Verbindung: Driver → Assessment → Goal → Outcome → realisiert durch → Capability → gestützt durch → ApplicationComponent (im Target-Plateau).

### 11.5 Plateau-Diff: Strukturierter Vergleich zwischen Plateaus

Der Vergleich zwischen zwei Plateaus ist eine der wichtigsten Sichten im Tool – er beantwortet "was ändert sich zwischen Ist und Ziel?". Das Ergebnis ist eine strukturierte Differenz, die als Grundlage für Migrations-Planung, Stakeholder-Kommunikation und Gap-Analyse dient.

**Diff-Dimensionen**: Eine Plateau-Diff betrachtet drei Arten von Änderungen:

```yaml
PlateauDiff:
  - sourcePlateau: Plateau              # z.B. baseline-2026
  - targetPlateau: Plateau              # z.B. target-2028
  - generatedAt: timestamp
  - changes:
      entitiesAdded: Entity[]             # neu in target
      entitiesRetired: Entity[]           # in target nicht mehr aktiv
      entitiesModified: PropertyDiff[]    # gleiche URN, geänderte Properties
      lifecycleTransitions:               # gleiche Entität, anderer Lifecycle-State
        - entity: Entity
          stateInSource: active
          stateInTarget: sunset
      relationsAdded: Relation[]
      relationsRetired: Relation[]
      relationsModified: PropertyDiff[]
```

**Berechnungsregeln**:

- **Hinzugefügt**: Entität existiert nur in `target`, oder in `source` mit Lifecycle `not-applicable` und in `target` mit aktivem State
- **Entfernt**: Entität in `source` aktiv, in `target` `retired` oder `not-applicable`
- **Modifiziert**: Selbe URN, mindestens ein Property-Wert unterschiedlich
- **Lifecycle-Transition**: Selbe URN, beide Plateaus aktiv, unterschiedlicher Lifecycle-State

**Filterung und Aggregation**: Eine vollständige Plateau-Diff über ein großes Repository ist überwältigend. Nutzer können filtern:

- Nach Domain (nur Finance-Änderungen)
- Nach EntityType (nur Application-Änderungen)
- Nach Significance (nur Major-Changes)
- Nach Capability (nur Änderungen, die Capability X betreffen)
- Auf eine Sub-Hierarchie beschränkt (alle Module einer Suite)

**Diff als Basis für Bebauungsplan-Visualisierung**: Die §12-Bebauungsplan-Sichten konsumieren Plateau-Diffs, um Lifecycle-Übergänge farblich darzustellen (z.B. grün=neu, rot=retired, gelb=modified).

**Diff-Persistenz**: Diffs werden nicht persistent gespeichert, sondern bei Bedarf berechnet. Ausnahme: Wenn eine Diff Teil eines RepositoryRelease wird (siehe [§22.11 (Auswertbarkeit & Query-Architektur)](../70-platform/22-auswertbarkeit.md)), wird sie als Snapshot zum Release-Tag konserviert – damit später nachvollziehbar bleibt, was zum Zeitpunkt des Release-Tags der Differenz-Stand war.

**API-Endpunkt**:

```
POST /api/v1/plateaus/{a}/diff/{b}
Body: { filter: { domain: "...", entityTypes: [...] }, aggregateBy: "domain" | "capability" | ... }
Response: PlateauDiff (strukturiert)
```

**Sichten und Reports auf Diffs**:

- **Migrations-Backlog**: alle Lifecycle-Transitionen `active → sunset` mit zugeordneten Work Packages
- **Neuerungen-Report**: alle `entitiesAdded` mit ihrem Goal-/Capability-Bezug
- **Ablöse-Report**: alle `entitiesRetired` mit Begründung (typisch über ADR-Verweis)
- **Cross-Plateau-Konflikte**: Inkonsistenzen, z.B. Entität ist `retired` in Plateau-A, aber Plateau-B referenziert sie noch

---

← [Cross-Cutting-Konzepte](../20-entities/10-cross-cutting.md) · [🏠 Übersicht](../README.md) · [Domain-Konzept & Sichten](12-domain-sichten.md) →
