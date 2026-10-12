## 12. Domain-Konzept und Sichten

### 12.1 Domain als n:m-Klassifikation

```
Domain {
  id: "domain-finance"
  name: "Finance"
  parent: "domain-corporate" (optional, hierarchisch)
  owner: OrganizationUnit
}
```

Entitäten werden Domains **zugeordnet**, nicht in sie eingebettet:

```
belongsTo-Relation {
  source: ApplicationComponent "SAP S/4HANA"
  target: Domain "Finance"
  primary: true
}
belongsTo-Relation {
  source: ApplicationComponent "SAP S/4HANA"
  target: Domain "Controlling"
  primary: false
}
```

Eine primäre Zuordnung + beliebig viele sekundäre. Das löst das Cross-Domain-Problem sauber.

### 12.2 Bebauungsplan – Struktur und Varianten

Ein Bebauungsplan ist eine **Sicht**, definiert durch:

- Scope: Domain(s) / Layer / Capability-Teilbaum
- Zeithorizont: Liste von Plateaus (z.B. Ist → T+1 → T+2 → Ziel)
- Dimension: Y-Achse (Capability / Domain / OrgUnit), X-Achse (Zeit)
- Darzustellende Entitäten: ApplicationComponent, Interface, Technology etc.

Typische Bebauungsplan-Varianten:

1. **Domain-Bebauungsplan** – z.B. "Finance-Landschaft 2026–2030"
2. **Capability-Bebauungsplan** – Anwendungen pro Capability über Zeit
3. **Layer-Bebauungsplan** – z.B. nur Application-Layer unternehmensweit
4. **Programm-Bebauungsplan** – gefiltert auf Work Packages eines Programms
5. **Konsolidierter Gesamtplan** – für Management-Reporting

Alle sind **definierte und gespeicherte Sichten**, nicht separate Datenbestände.

### 12.3 Sichten-Definition

```
View {
  id: "view-finance-roadmap"
  type: roadmap | landscape | heatmap | matrix | diagram
  scope: {
    domains: [finance, controlling]
    layers: [application]
    entityTypes: [ApplicationComponent, Interface]
  }
  timeline: [baseline-2026-q1, transition-2027, target-2028]
  groupBy: capability
  render: [plantuml, mermaid, archimate-xml]
}
```

Eine View ist deklarativ beschrieben und wird bei Bedarf gerendert – das ist die Grundlage für die Pipeline-Integration.

### 12.4 Progressive Disclosure – drei Sichten für unterschiedliche Reife

Die in [§6.1.1 (Kern-Entitätstypen)](../20-entities/06-kern-entitaetstypen.md) beschriebene kognitive Realität – dass Menschen in konkreten Lösungen denken, nicht in Schichten – findet ihre konkrete Umsetzung in drei vordefinierten Sichten, die je nach Reifegrad der Modellierung und Stakeholder-Bedarf eingesetzt werden.

#### Sicht: Inventar (Default für Einsteiger und Fachbereiche)

```yaml
View "inventory":
  type: list
  scope:
    entityTypes: [PhysicalApplicationComponent, PhysicalTechnologyComponent]
  groupBy: none           # flache Liste, keine Schichten-Strukturierung
  display:
    visualMarkers:        # farbliche Differenzierung
      PhysicalApplicationComponent: blue
      PhysicalTechnologyComponent: grey
    columns: [name, type, owner, status, domain]
  filters: [search, owner, domain, status]
  detailView: schichten-sicht        # Klick öffnet Sicht 2
```

Die Inventar-Sicht ist die **Einstiegssicht** ins Tool: flach, durchsuchbar, ohne konzeptionelle Vorkenntnisse nutzbar. Application und Technology stehen nebeneinander, visuell unterscheidbar, aber gleichwertig auffindbar. Ein Fachbereichsvertreter findet hier alle Systeme, die er kennt, ohne lernen zu müssen, was "Logical Application Component" bedeutet.

**Wichtig**: Diese Sicht funktioniert auch dann, wenn keine Logical-Schicht gepflegt ist – sie zeigt nur, was tatsächlich existiert.

#### Sicht: Schichten (für Architekten)

```yaml
View "layered-detail":
  type: structured
  scope:
    entityTypes: [Logical*, Physical*, ApplicationService, PlatformService]
  groupBy: realization-chain    # Logical realized by Physical, runs on Technology
  display:
    showLayers: true
    expandableHierarchy: true
  detailView: bebauungsplan-sicht
```

Hier wird die strukturelle Einbettung sichtbar: Welche Logical Application steht hinter einer Physical Application, welche Technology Components laufen darunter, welche Services werden exponiert. Diese Sicht wird gefordert, wenn analytische Tiefe gebraucht wird.

Wenn die Logical-Schicht nicht gepflegt ist, zeigt die Sicht das transparent an: "Diese Physical Application hat keinen Logical-Bezug. [Logical generieren?]" – mit der Option zur automatischen Stub-Generierung.

#### Sicht: Bebauungsplan (für strategische Entscheidungen)

Die in [§12.2 (Domain & Sichten)](12-domain-sichten.md) beschriebenen Bebauungspläne nutzen die Logical/Physical-Trennung gezielt: Logical bleibt über Plateaus stabil, Physical wechselt. Diese Sicht erfordert eine ausgereifte Modellierung mit gepflegter Logical-Schicht.

#### Progressiver Aufbau im Repository

Die drei Sichten sind nicht alternativlos hierarchisch – sie unterstützen einen **schrittweisen Aufbau** des Modells:

| Reifestufe | Was modelliert ist | Welche Sichten nutzbar |
|---|---|---|
| 1 – Initial | Nur Physical Applications und Technologies | Inventar ✓, Schichten ⚠️ (mit Lücken), Bebauungsplan ✗ |
| 2 – Strukturiert | + Logical Applications, ApplicationServices | Inventar ✓, Schichten ✓, Bebauungsplan ⚠️ (ohne Plateaus) |
| 3 – Temporal | + Plateaus, Lifecycle-Zuordnung pro Plateau | Alle Sichten ✓ |
| 4 – Vollständig | + Capabilities, Goals, Bebauungsplan-Varianten | Alle Sichten ✓ mit voller Mächtigkeit |

Eine Organisation kann auf jeder Stufe stehenbleiben und sinnvoll mit dem Tool arbeiten. Der Aufstieg zu höheren Stufen passiert, wenn der konkrete Bedarf entsteht – nicht weil das Tool es erzwingt.

#### Implementierung: Sichten als gespeicherte Queries

Alle drei Sichten sind als gespeicherte Sichten (siehe [§22.8 (Auswertbarkeit & Query-Architektur)](../70-platform/22-auswertbarkeit.md)) realisiert und werden mit dem Tool ausgeliefert. Organisationen können sie übernehmen, anpassen oder eigene Varianten definieren.

---

← [Temporales Modell](11-temporales-modell.md) · [🏠 Übersicht](../README.md) · [Fach-Technik-Verlinkung](13-fach-technik-verlinkung.md) →
