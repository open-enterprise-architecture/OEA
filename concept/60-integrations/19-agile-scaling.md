## 19. Agile Skalierungs-Frameworks (SAFe, LeSS)

Große Organisationen arbeiten heute oft nach skalierten agilen Methoden. Die zwei verbreitetsten – SAFe (Scaled Agile Framework) und LeSS (Large Scale Scrum) – haben **fundamental unterschiedliche Philosophien** und stellen unterschiedliche Anforderungen an die EA-Integration.

### 19.1 SAFe – Framework-reich und EA-freundlich

SAFe ist ein umfangreiches Framework mit expliziten EA-Konzepten. Die Integration mit einem EA-Repository ist vergleichsweise geradlinig, weil SAFe viele Konzepte **benennt und strukturiert**, die in einem OEA ohnehin existieren.

**Relevante SAFe-Konzepte und ihre EA-Entsprechungen:**

| SAFe-Konzept | EA-Entsprechung | Anmerkung |
|---|---|---|
| Strategic Theme | Goal / Outcome | 3–5 Jahre Horizont, portfolio-weit |
| Portfolio Canvas | Portfolio + Capability Map | Strategischer Rahmen |
| Portfolio Epic | Work Package (groß) | Bewegt sich zwischen Portfolio Kanban-Stufen |
| Enabler Epic | Work Package (technisch) | Infrastruktur-, Compliance-, Research-Arbeit |
| Solution | LogicalApplicationComponent (Suite) | System-of-Systems |
| Capability (SAFe) | Eigener Typ! Nicht EA-Capability! | Verwirrend: gleicher Begriff, andere Bedeutung |
| Feature | Feature / große User Story | Umsetzbar in einem PI |
| Architectural Runway | Technical Enabler + Roadmap | "Vorinvestition in Architektur" |
| Intentional Architecture | Gesteuerte Architektur-Entscheidungen | Gegenpol zu "Emergent Design" |
| System Team | OrganizationUnit | Übergreifend für Integration |
| ART (Agile Release Train) | OrganizationUnit + Solution-Zuordnung | 50–125 Personen um eine Solution |

**Die Capability-Falle**: SAFe verwendet "Capability" für etwas anderes als TOGAF. In SAFe ist Capability eine **große Feature-Einheit, mehrere PIs umspannend**. In TOGAF ist Capability eine **langfristige Unternehmensfähigkeit**. Im Metamodell empfehle ich:

```yaml
Stereotype: SAFeCapability
basedOn: WorkPackage
properties:
  - safeState: funnel | analyzing | portfolio-backlog | implementing | done
  - targetPI: string  # z.B. "PI-2026-Q2"
```

Die TOGAF-Capability bleibt der primäre Capability-Begriff, SAFe-Capability wird als Stereotype auf WorkPackage abgebildet. Das vermeidet semantische Kollisionen.

**Portfolio Epic als First-Class-Brücke**: Portfolio Epics sind in SAFe der explizite Ort, wo Strategie auf Umsetzung trifft. Sie haben ein **Lean Business Case**, durchlaufen ein **Portfolio Kanban** und werden in **ARTs** umgesetzt. Das ist bereits eine komplette PPM-Struktur, die sauber mit dem EA-Repository integrierbar ist:

```
Strategic Theme (EA: Goal)
  ↓ realizedBy
Portfolio Epic (EA: Work Package, Stereotype: SAFePortfolioEpic)
  ↓ decomposedInto
Capability (SAFe) / Feature
  ↓ implementedIn
Program Increment (PI) via ART
```

**Architectural Runway** – ein EA-Konzept mit neuem Namen: SAFe bezeichnet die Vorinvestition in Architektur ("Runway"), die erlaubt, zukünftige Features schnell zu liefern. Das ist nichts anderes als das, was klassische EA als "technische Infrastruktur-Zielbilder" kennt. Im Metamodell:

```yaml
Stereotype: ArchitecturalRunway
basedOn: WorkPackage
additionalProperties:
  - enablesCapabilities: Capability[]  (welche zukünftigen Fähigkeiten werden ermöglicht)
  - runwayType: infrastructure | compliance | performance | usability
```

**Intentional Architecture vs. Emergent Design**: SAFe fordert eine Balance. Das EA-Repository repräsentiert die *Intentional Architecture* – die bewusst geplante Struktur. Emergent Design passiert in Teams und sollte **rückwirkend dokumentiert** werden, wenn es signifikant wird. Das Repository erlaubt beides, aber die Unterscheidung kann als Property markiert werden:

```yaml
ArchitectureDecision erhält:
  - origin: intentional | emergent
  - decidedIn: ARB | ART-SystemTeam | Team
```

### 19.2 LeSS – Framework-arm und EA-kritisch

LeSS ist das Gegenteil von SAFe: minimale Struktur, radikaler Fokus auf Produkt statt Projekt, bewusst **skeptisch gegenüber Enterprise-Architektur** als Disziplin.

LeSS-Grundprinzipien mit EA-Relevanz:
- **Ein Produkt, ein Product Backlog, ein Product Owner** – auch bei 8 Teams
- **Keine zusätzlichen Rollen** jenseits von Scrum (kein ARB, kein Enterprise Architect als eigene Rolle)
- **Architektur entsteht aus dem Code** (im Sinne von DDD, Continuous Architecture)
- **Vermeidung von "Architects Without Code"** – Architekten sind idealerweise auch Entwickler

**Was heißt das für dein OEA?**

Die philosophische Spannung: LeSS sagt nicht "EA ist falsch", sondern "EA als separate Disziplin mit separaten Rollen schadet". Stattdessen:
- Architektur ist Ergebnis kontinuierlicher Entscheidungen im Team
- Cross-Team-Koordination erfolgt in Design-Workshops, nicht in ARBs
- Dokumentation ist so schlank wie möglich (LeSS fordert "whisper"-Dokumentation)

**Wo andockt das trotzdem an EA?**

Auch LeSS-Organisationen haben:
- **Multi-Team-Produkte** mit gemeinsamer technischer Infrastruktur
- **Systemübergreifende Interfaces** zu anderen Produkten / SaaS / Legacy
- **Compliance-Anforderungen**, die dokumentiert werden müssen
- **Portfolio-Ebene**, auf der Produkte finanziert und abgegrenzt werden

Für ein LeSS-kompatibles Tool-Design bedeutet das:

**Primäre Entität ist "Product", nicht "Project"**:

```yaml
EntityType: Product
  properties:
    - vision, productGoal
    - lifecycleState: explore | exploit | sunset
  relations:
    - backlog: Epic[] | Feature[]
    - ownedBy: Role (Product Owner)
    - supportsCapability: Capability
    - realizedBy: LogicalApplicationComponent[]
```

Ein Product ist langlebiger als ein Project – es existiert, solange das Produkt existiert. Projects werden in LeSS-Kontexten nicht benötigt, oder nur als temporäre Cost-Container.

**Schlanke Documentation-Policy**: Das Tool sollte nicht jede Entität zur Pflicht machen. Ein **Minimal Schema Set** für LeSS-Kontexte wäre hilfreich:

```yaml
minimalSchema: less-core
includes:
  - Product
  - ApplicationComponent (hierarchisch)
  - Interface
  - Capability (nur auf Product-Level)
  - ADR
excludes:
  - Program (gibt es nicht)
  - Portfolio-Epic (gibt es nicht)
  - ARB (gibt es nicht)
```

**Team-zentrische Sichten**: Während SAFe-Sichten oft ART-zentriert sind, sind LeSS-Sichten team-zentrisch:
- Welches Team pflegt welche Components? (Ownership)
- Welche Cross-Team-Dependencies existieren?
- Welche Interfaces sind "Contracts" zwischen Teams?

### 19.3 Die praktische Konsequenz für dein Tool

Ein Tool, das *beide* Welten bedienen will (und das sollte es als Open-Source-Standard), muss:

**1. Entitäten für SAFe UND Produkt-zentrische Arbeit anbieten:**
- Project, Program, Portfolio → für SAFe/klassische PPM
- Product → für LeSS/produktzentrierte Orgs
- Beide können koexistieren, je nach Schema-Konfiguration

**2. Unterschiedliche Schema-Profile erlauben:**

```yaml
profiles:
  - enterprise-safe:
      includes: [togaf-core, safe-portfolio, ppm-full, itsm-integration]
  - enterprise-classical:
      includes: [togaf-core, ppm-classical, itsm-integration]
  - product-less:
      includes: [togaf-core-slim, less-core, ddd-extensions]
  - solo-architect:
      includes: [togaf-core-slim, arc42, adr-only]
```

Organisationen wählen beim Setup ihr Profil. Das Meta-Metamodell bleibt gleich, aber die Schema-Aktivierung variiert.

**3. Vokabular neutral halten**: Das Tool sollte nicht in seiner UI permanent "Project" sagen, wenn eine Organisation auf Products arbeitet. Konfigurierbare Labels pro Entity-Type helfen, die jeweilige Org-Sprache zu sprechen.

**4. Governance-Flexibilität**: Ein ARB ist in SAFe optional, in LeSS bewusst abgelehnt. Das Tool sollte Governance-Prozesse *unterstützen*, wenn sie existieren, aber nicht *erzwingen*. Approval-Workflows als Plugin, nicht als Core.

### 19.4 Zusammenfassung: Metamodell-Erweiterungen für agile Skalierung

**Neue EntityTypes:**
- `Product` (für produktzentrierte Organisationen)
- `ProgramIncrement` (SAFe-PI als Zeitraum-Entität)
- `AgileReleaseTrain` (SAFe-ART als OrganizationUnit-Stereotype)

**Neue Stereotypes:**
- `SAFeCapability` auf WorkPackage
- `SAFePortfolioEpic` auf WorkPackage
- `ArchitecturalRunway` auf WorkPackage
- `EnablerEpic` auf WorkPackage

**Neue Properties an bestehenden Typen:**
- `ArchitectureDecision.origin: intentional | emergent`
- `Project.methodology: waterfall | agile-scaled | agile-product | hybrid`
- `WorkPackage.safeState` (optional, nur bei SAFe-Profil)

**Schema-Profile** als organisatorische Konfiguration, wie in [§19.3 (Agile Skalierung)](19-agile-skalierung.md) skizziert.

---

← [PPM-Integration](18-ppm-integration.md) · [🏠 Übersicht](../README.md) · [GRC-, DSGVO- und ISMS-Integration](20-grc-dsgvo-isms-integration.md) →
