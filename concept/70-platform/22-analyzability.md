## 22. Auswertbarkeit und Query-Architektur

Ein Architektur-Repository ist nur so wertvoll wie seine Auswertbarkeit. Zwei fundamental unterschiedliche Query-Typen sind zu unterstützen: **Graph-Traversierung** (element- und connection-bezogenes Folgen) und **analytische Aggregation** (Repository-weite Joins und Gruppierungen).

### 22.1 Die zwei Query-Paradigmen

**Graph-Traversierung** beantwortet Fragen wie:
- "Starte bei Application X, folge allen Interfaces – welche Business Services werden transitiv betroffen?"
- "Welcher kürzeste Pfad existiert zwischen Capability A und Technology B?"
- "Zeige alle Entitäten im Umkreis von 2 Hops um Interface Y"
- "Welche ADRs betreffen transitiv die Application Z?"

**Analytische Aggregation** beantwortet Fragen wie:
- "Summe der TCO aller Applications pro Capability pro Plateau"
- "Anzahl Applications pro Domain × Lifecycle-State, gefiltert auf Kritikalität"
- "Durchschnittliche Reviews pro Standard pro Jahr"
- "Top 10 Capabilities nach Anzahl aktiver Projekte"

Beide Paradigmen müssen **gleich gut** unterstützt werden – keines darf zum Fremdkörper verkümmern.

### 22.2 Graph-Traversierung – Anforderungen

```yaml
Traversierungs-Fähigkeiten:
  - Start-Entity + Traversal-Regeln (welche Relations folgen, in welche Richtung)
  - Tiefen-Limit / Breiten-Limit
  - Filter auf Zwischen-Entitäten (nur bestimmte Typen, nur bestimmte Properties)
  - Rückgabe als Subgraph, Pfad-Liste oder Endknoten-Liste
  - Unterstützung für gerichtete und ungerichtete Traversierung
  - Transitive Closure ("alle erreichbaren Knoten")
  - Pfad-Algorithmen: shortest path, all paths bis Länge N
```

### 22.3 Analytische Aggregation – Anforderungen

```yaml
Analytics-Fähigkeiten:
  - Multi-Entity-Joins über beliebige Relationen
  - Aggregationsfunktionen: count, sum, avg, min, max, percentile
  - Gruppierung nach mehreren Dimensionen
  - Pivotierung (Kreuztabellen)
  - Zeitbezogene Aggregation (pro Plateau, pro Jahr, rolling windows)
  - Berechnete Felder (z.B. TCO-Summen-Formeln)
  - Window-Funktionen (running totals, rankings)
  - Subqueries und CTEs
```

### 22.4 Query-Interface: zwei Sprachen oder eine?

**Option A – Zwei spezialisierte Sprachen**:
- Graph-Queries in einer Graph-Sprache (Cypher, Gremlin, GQL)
- Analytics-Queries in SQL oder einem SQL-ähnlichen Dialekt

Vorteile: Jede Sprache ist in ihrer Domäne optimal, hohe Ausdrucksstärke.
Nachteile: Zwei Lernkurven, Ergebnisse schwer kombinierbar, zwei Engines im Hintergrund.

**Option B – Eine einheitliche Query-Sprache**:
- Eine DSL, die beide Paradigmen abdeckt (Beispiel: GraphQL mit Erweiterungen, oder eine eigene EA-Query-Language)

Vorteile: Eine Lernkurve, kombinierbare Queries, konsistente API.
Nachteile: Oft Kompromiss bei Ausdrucksstärke, Risiko einer proprietären DSL.

**Empfehlung**: **Beide Sprachen anbieten**, aber über eine gemeinsame Abstraktionsebene:

```
/api/v1/graph/traverse          ← typisierte Graph-Operationen (einfacher Einstieg)
/api/v1/graph/cypher            ← Cypher für mächtige Graph-Queries
/api/v1/analytics/query         ← SQL oder SQL-ähnlich für Analytics
/api/v1/analytics/views/{name}  ← vordefinierte benannte Views
```

Einsteiger nutzen die typisierten Endpunkte, fortgeschrittene Nutzer greifen zu Cypher/SQL. Die gespeicherten benannten Views (siehe [§22.7 (Auswertbarkeit)](22-auswertbarkeit.md)) decken Standard-Fälle ohne Query-Kenntnisse ab.

### 22.5 Reifizierte Relationen in Queries

Weil Relationen First-Class-Objekte sind (siehe [§2 (Meta-Metamodell)](../00-overview/02-meta-metamodell.md)), haben sie Konsequenzen für die Query-Architektur. Drei Szenarien:

**Szenario 1 – Relation als Kante traversieren**: der Normalfall.
```cypher
MATCH (a:Application)-[:uses]->(b:Application)
RETURN a, b
```
Die Relation wird wie eine klassische Graph-Kante behandelt. Funktioniert in jeder Graph-DB.

**Szenario 2 – Relation als Knoten, weil sie Ziel einer anderen Relation ist**:
```cypher
MATCH (adr:ADR)-[:affects]->(r:Relation {type: 'uses'})
MATCH (src)-[r]->(tgt)
RETURN adr, src, tgt
```
Die Relation tritt einmal als Knoten (Ziel von `affects`) und einmal als Kante (zwischen src und tgt) auf. Das ist der Reifikations-Fall.

**Szenario 3 – Relation mit Eigenschaften analytisch aggregieren**:
```sql
SELECT r.protocol, COUNT(*) as count, AVG(r.avg_data_volume_mb) as avg_volume
FROM relations r
WHERE r.type = 'interface_uses'
GROUP BY r.protocol;
```
Relationen mit Properties sind in Analytics genauso abfragbar wie Entitäten.

**Implementierungs-Hinweise pro Persistenz-Option** (vgl. [§22.6 (Auswertbarkeit)](22-auswertbarkeit.md)):

- **Postgres + Apache AGE**: Relationen sind Kanten in AGE-Graph plus eine normale Zeile in einer `relations`-Tabelle (mit URN und Properties). Queries nutzen je nach Bedarf SQL oder Cypher. Guter Kompromiss.
- **Neo4j 5.x**: Relationships haben Properties nativ; Reifikation erfordert einen zusätzlichen "Relation-Knoten", der per Konvention mit der Kante verknüpft ist. Etwas Mehraufwand beim Modellieren, aber gängiges Muster.
- **RDF/SPARQL**: Reifikation ist nativ (RDF-star/RDF 1.2). Semantisch am saubersten, aber Query-Sprache steil.
- **Git+YAML-Dateien**: Jede Relation ist eine YAML-Datei mit eigener URN. Entity- und Relation-URNs werden gleich behandelt. Einfachste Mental-Model, aber Query-Performance muss über Index-Layer sichergestellt werden.

**Praktische Konsequenz**: Für den Walking Skeleton ist die Wahl pragmatisch. Postgres + AGE unterstützt beide Fälle ohne Bruch: Einfache Relationen bleiben Kanten, reifizierte bekommen zusätzlich eine Tabellenzeile mit Properties. Die API versteckt diese Unterscheidung vor Konsumenten.

### 22.6 Persistenz-Optionen

Die API-Architektur aus [§21 (API-Architektur)](21-api-architektur.md) erlaubt, die Persistenz austauschbar zu halten. Realistische Optionen für dein Tool:

**Option 1 – Postgres + JSONB + Apache AGE**:
- Postgres als solide Basis, JSONB für flexible Properties
- Apache AGE als Graph-Extension (openCypher-Support)
- Eine Datenbank, zwei Query-Paradigmen, transaktional konsistent
- *Empfohlener Start-Stack für den Walking Skeleton*

**Option 2 – Neo4j oder Memgraph + Read-Model**:
- Native Property-Graph-DB als primäre Persistenz
- Für Analytics: CQRS-Read-Model in Postgres oder DuckDB
- Event-Stream aus [§21.5 (API-Architektur)](21-api-architektur.md) hält Read-Model synchron
- Starke Graph-Performance, etwas höhere Komplexität

**Option 3 – Git als Quelle + DuckDB/Postgres als Index**:
- YAML/JSON-Files in Git als Source of Truth (maximal git-friendly)
- DuckDB oder Postgres als Index, aus Git rebuildbar
- Apache AGE oder externer Graph-Service für Traversierung
- *Ideal für kleine bis mittlere Installationen, schlecht für sehr große*

**Option 4 – Kuzu (embedded Graph-DB)**:
- Embedded Property-Graph-DB, Cypher-Support
- Keine Server-Installation, gut für CLI-Tools
- Analytics-Fähigkeiten begrenzt, aber in Entwicklung
- *Interessant für lokale Entwickler-Installationen*

**Entscheidung vertagen**: Die API-Definition erlaubt, mit Option 1 oder 3 zu starten und später zu migrieren, falls Performance-Anforderungen es verlangen.

### 22.7 Query-Beispiele

**Graph-Traversierung (Cypher-Style)**:

```cypher
// Welche Business Services sind von Application X abhängig?
MATCH (app:Application {urn: 'urn:ea:acme:application:sap-fi'})
MATCH path = (app)-[:realizes|dependsOn|exposedBy*1..4]->(svc:BusinessService)
RETURN svc, length(path) AS hops
ORDER BY hops
```

```cypher
// Welche Entitäten ändern sich zwischen Baseline und Target-Plateau?
MATCH (e)-[r:hasLifecycleState]->(s)
WHERE r.plateau = 'baseline-2026' AND r.state = 'active'
MATCH (e)-[r2:hasLifecycleState]->(s2)
WHERE r2.plateau = 'target-2028' AND r2.state IN ['sunset', 'retired']
RETURN e.urn, e.name
```

**Analytics (SQL-Style)**:

```sql
-- TCO pro Capability pro Plateau
SELECT 
  c.name AS capability,
  p.name AS plateau,
  SUM(a.tco_license + a.tco_operational + a.tco_development) AS total_tco,
  COUNT(DISTINCT a.urn) AS app_count
FROM applications a
JOIN entity_plateau_state eps ON eps.entity = a.urn
JOIN plateaus p ON p.id = eps.plateau
JOIN application_capability ac ON ac.application = a.urn
JOIN capabilities c ON c.urn = ac.capability
WHERE eps.state = 'active'
GROUP BY c.name, p.name
ORDER BY p.name, total_tco DESC;
```

```sql
-- Standards-Compliance pro Domain
SELECT 
  d.name AS domain,
  s.name AS standard,
  COUNT(e.urn) AS affected_entities,
  SUM(CASE WHEN cd.id IS NULL THEN 1 ELSE 0 END) AS compliant,
  SUM(CASE WHEN cd.id IS NOT NULL THEN 1 ELSE 0 END) AS deviating
FROM architecture_standards s
JOIN standard_applies_to sat ON sat.standard = s.urn
JOIN entities e ON e.type = sat.entity_type
JOIN entity_domain ed ON ed.entity = e.urn
JOIN domains d ON d.urn = ed.domain
LEFT JOIN compliance_deviations cd ON cd.affects_entity = e.urn AND cd.deviates_from = s.urn
GROUP BY d.name, s.name
ORDER BY d.name, deviating DESC;
```

### 22.8 Gespeicherte Views als benannte Queries

Standard-Auswertungen werden als **benannte, parametrisierte Views** im Repository gepflegt:

```yaml
View "impact-analysis-application":
  type: graph-traversal
  parameters:
    - applicationUrn: URN
    - maxHops: integer (default: 3)
  query: |
    MATCH (app:Application {urn: $applicationUrn})
    MATCH (app)-[*1..$maxHops]-(related)
    RETURN related, ...

View "tco-by-capability":
  type: analytics
  parameters:
    - plateauId: string (default: 'current')
    - domainFilter: string[] (optional)
  query: |
    SELECT ... (wie oben, parametrisiert)
```

Vorteile:
- Wiederverwendung ohne Query-Kenntnisse
- Versionierbar und review-bar in Git
- API-Endpunkt `/api/v1/analytics/views/{name}?plateauId=...` für einfache Konsumption
- Grundlage für Dashboards und Reports

### 22.9 Performance-Anforderungen und Caching

```yaml
Performance-Ziele (Richtwerte):
  - Einzelne Entity-Abfrage: < 50ms (p95)
  - Graph-Traversierung bis 3 Hops bei 10k Entities: < 500ms (p95)
  - Analytics-Query über volles Repo (10k Entities): < 2s (p95)
  - Arc42-Dokument-Generierung: < 5s

Caching-Strategie:
  - Query-Result-Cache mit TTL für Analytics (invalidiert durch Events)
  - Materialized Views für teure Aggregationen
  - Graph-Path-Cache für häufige Traversierungs-Muster
  - Schema-Cache clientseitig (dank Introspection-Endpoint)
```

Das Event-System aus [§21.5 (API-Architektur)](21-api-architektur.md) ist zentral für Cache-Invalidierung: Änderung an Entity X invalidiert alle Caches, die X referenzieren.

### 22.10 Offline- und Pipeline-Nutzung

Die Auswertbarkeit muss auch **ohne laufenden Server** funktionieren – für CI-Pipelines, lokale Analysen, Offline-Arbeit:

**CLI-Tool** mit lokaler Query-Engine:
- Liest Repository-Snapshots (Git-Export oder API-Dump)
- Führt Queries lokal aus (embedded DuckDB oder Kuzu)
- Identische Query-Sprachen wie Server
- Ermöglicht Pipeline-Checks: "CI-Build bricht ab, wenn neue Applications ohne Capability-Zuordnung existieren"

**Beispiel CI-Check**:
```bash
ea-tool query --local \
  "MATCH (a:Application) WHERE NOT (a)-[:realizes]->(:Capability) RETURN a.urn" \
  --expect-empty
```

### 22.11 Repository-Changelog und Release-Notes

Über das forensische Audit-Log hinaus (siehe [§21.8 (API-Architektur & Modularität)](21-api-architektur.md)) bietet das Tool einen **kuratierten Repository-Changelog** für Stakeholder-Kommunikation. Während das Audit-Log jede Änderung enthält, fasst der Changelog **wichtige** Änderungen zu Release-Einheiten zusammen.

**Abgrenzung**:
- Audit-Log: rohe, lückenlose Forensik (für Auditoren)
- Changelog: kuratierte, kommunizierbare Zusammenfassung (für Stakeholder)

**Mechanik**:

```yaml
EntityType: RepositoryRelease
  properties:
    - tag: string                     # z.B. "2027-Q2-Release"
    - releaseDate: date
    - releaseType: snapshot | milestone | quarterly | on-demand
    - description: text               # narrative Zusammenfassung
    - status: draft | published
  relations:
    - includesChanges: ChangeEntry[]
    - taggedBy: Role
    - approvedBy: Role | GovernanceBody

EntityType: ChangeEntry
  properties:
    - changeType: added | modified | retired | reclassified | reorganized
    - significance: major | minor | patch
    - summary: text                   # für Stakeholder, in Klartext
    - rationale: text                 # warum die Änderung
  relations:
    - affectsEntities: Entity[]
    - affectsRelations: Relation[]
    - documentedInADR: ADR
    - addressesRequirement: Requirement
    - relatedWorkPackage: WorkPackage
```

**Drei Wege, Changelog-Einträge zu erzeugen**:

1. **Manuell**: Architekt schreibt einen ChangeEntry beim Commit kritischer Änderungen
2. **Halb-automatisch**: Tool schlägt aus dem Audit-Log automatisch Kandidaten vor (insb. lifecycle-Übergänge, neue Entities, retired Entities), Architekt kuratiert
3. **Automatisch**: Bestimmte strukturelle Änderungen (Plateau-Aktivierung, ADR-Approval) erzeugen automatisch ChangeEntries

**Release-Aggregation**: Mehrere ChangeEntries werden zu einem RepositoryRelease zusammengefasst. Typische Trigger:

- Plateau-Wechsel (z.B. "Plateau-2027-Q2 ist neuer Ist-Zustand")
- Quartalsweise als Reporting-Rhythmus
- Bei größeren Architektur-Programmen als Meilenstein

**Sichten**:

- **Was hat sich seit Release X geändert?** – Diff zwischen zwei Releases
- **Changelog für Domain Y im Q2** – gefilterte Änderungen pro Bereich
- **Kommunikations-Report** – PDF/HTML-Export für Stakeholder-Mailings, generiert aus aktiven RepositoryRelease-Einträgen
- **Trend-Sicht** – wie viele Änderungen pro Monat/Quartal, gruppiert nach significance

**API-Endpunkte**:

```
GET    /api/v1/releases                    # alle Releases
GET    /api/v1/releases/{tag}              # spezifischer Release
GET    /api/v1/releases/{a}/diff/{b}       # Diff zwischen zwei Releases
POST   /api/v1/changelog/suggest           # Vorschläge aus Audit-Log generieren
POST   /api/v1/releases                    # neuen Release-Tag setzen
GET    /api/v1/entities/{urn}/history      # Entity Change History
```

**Constraints**:

```yaml
constraint: published-release-has-description
  appliesTo: RepositoryRelease
  when: "entity.status == 'published'"
  rule: "entity.description != null AND entity.description.length > 50"
  severity: error
  message: "Veröffentlichter Release muss aussagekräftige Beschreibung haben"

constraint: major-change-has-rationale
  appliesTo: ChangeEntry
  when: "entity.significance == 'major'"
  rule: "entity.rationale != null AND (entity.documentedInADR != null OR entity.addressesRequirement != null)"
  severity: warning
  message: "Major-Change sollte ADR oder Requirement-Bezug haben"
```

### 22.12 Auditierbarkeit und Query-Logging

Für Enterprise-Nutzung wichtig: Wer hat was wann abgefragt?

```yaml
Query-Audit-Log:
  - user: Role | Person | ApiKey
  - timestamp
  - endpoint, parameters, query-hash
  - result-metadata (nicht Inhalt – Datenschutz)
  - execution-time
  - client (UI, module, script)
```

Audit-Log ist separater Event-Stream, kann in SIEM (Splunk, Elastic) integriert werden.

### 22.13 Constraints und Governance

```yaml
constraint: query-complexity-limit
  appliesTo: ApiQuery
  rule: "query.estimated-cost < threshold"
  severity: error
  message: "Query zu komplex – bitte Filter hinzufügen oder Pagination nutzen"

constraint: named-view-has-owner
  appliesTo: View
  rule: "entity.owner != null"
  severity: warning

constraint: view-query-tested
  appliesTo: View (published)
  rule: "entity.hasTestCases == true"
  severity: warning
  message: "Veröffentlichte View sollte Test-Cases haben"
```

### 22.14 Empfehlung für Walking Skeleton

Für den initialen Walking Skeleton:

1. **Persistenz**: Postgres + JSONB + Apache AGE (einfachster produktionsreifer Stack)
2. **Query-API**: typisierte Graph-Endpunkte + SQL-Analytics, Cypher als Stretch-Ziel
3. **Eine gespeicherte View**: "Impact-Analyse" als vorgefertigte Graph-Traversierung
4. **CLI-Query-Tool**: ab v0.1 verfügbar, weil Pipeline-Integration von Anfang an Anforderung ist
5. **OpenAPI-Spec**: generiert aus Code, in CI geprüft

Das ergibt einen kleinen, aber vollständigen Durchstich, der Graph und Analytics beides abdeckt.

---

← [API-Architektur & Modularität](21-api-architektur.md) · [🏠 Übersicht](../README.md) · [Offene Punkte](../90-backlog/23-offene-punkte.md) →
