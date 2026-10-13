## 22. Analyzability and Query Architecture

An architecture repository is only as valuable as its analyzability. Two fundamentally different query types must be supported: **graph traversal** (element- and connection-related following) and **analytical aggregation** (repository-wide joins and groupings).

### 22.1 The Two Query Paradigms

**Graph traversal** answers questions such as:

- "Start at application X, follow all interfaces – which business services are transitively affected?"
- "What is the shortest path between capability A and technology B?"
- "Show all entities within 2 hops of interface Y"
- "Which ADRs transitively affect application Z?"

**Analytical aggregation** answers questions such as:

- "Sum of the TCO of all applications per capability per plateau"
- "Number of applications per domain × lifecycle state, filtered by criticality"
- "Average reviews per standard per year"
- "Top 10 capabilities by number of active projects"

Both paradigms must be supported **equally well** – neither may wither into a foreign body.

### 22.2 Graph Traversal – Requirements

```yaml
Traversal capabilities:
  - Start entity + traversal rules (which relations to follow, in which direction)
  - Depth limit / breadth limit
  - Filters on intermediate entities (only certain types, only certain properties)
  - Return as subgraph, path list, or end node list
  - Support for directed and undirected traversal
  - Transitive closure ("all reachable nodes")
  - Path algorithms: shortest path, all paths up to length N
```

### 22.3 Analytical Aggregation – Requirements

```yaml
Analytics capabilities:
  - Multi-entity joins across arbitrary relations
  - Aggregation functions: count, sum, avg, min, max, percentile
  - Grouping by multiple dimensions
  - Pivoting (cross tabulations)
  - Time-related aggregation (per plateau, per year, rolling windows)
  - Calculated fields (e.g., TCO sum formulas)
  - Window functions (running totals, rankings)
  - Subqueries and CTEs
```

### 22.4 Query Interface: Two Languages or One?

**Option A – Two specialized languages**:

- Graph queries in a graph language (Cypher, Gremlin, GQL)
- Analytics queries in SQL or an SQL-like dialect

Advantages: Each language is optimal in its domain, high expressiveness.
Disadvantages: Two learning curves, results hard to combine, two engines in the background.

**Option B – One unified query language**:

- One DSL that covers both paradigms (example: GraphQL with extensions, or a custom EA query language)

Advantages: One learning curve, combinable queries, consistent API.
Disadvantages: Often a compromise in expressiveness, risk of a proprietary DSL.

**Recommendation**: **Offer both languages**, but via a common abstraction layer:

```text
/api/v1/graph/traverse          ← typed graph operations (easy entry)
/api/v1/graph/cypher            ← Cypher for powerful graph queries
/api/v1/analytics/query         ← SQL or SQL-like for analytics
/api/v1/analytics/views/{name}  ← predefined named views
```

Beginners use the typed endpoints, advanced users reach for Cypher/SQL. The stored named views (see [§22.8 (Analyzability)](22-analyzability.md)) cover standard cases without query knowledge.

### 22.5 Reified Relations in Queries

Because relations are first-class objects (see [§2 (Meta-Metamodel)](../00-overview/02-meta-metamodel.md)), they have consequences for the query architecture. Three scenarios:

**Scenario 1 – Traverse the relation as an edge**: the normal case.

```cypher
MATCH (a:Application)-[:uses]->(b:Application)
RETURN a, b
```

The relation is treated like a classic graph edge. Works in any graph DB.

**Scenario 2 – Relation as a node, because it is the target of another relation**:

```cypher
MATCH (adr:ADR)-[:affects]->(r:Relation {type: 'uses'})
MATCH (src)-[r]->(tgt)
RETURN adr, src, tgt
```

The relation appears once as a node (target of `affects`) and once as an edge (between src and tgt). This is the reification case.

**Scenario 3 – Analytically aggregate a relation with properties**:

```sql
SELECT r.protocol, COUNT(*) as count, AVG(r.avg_data_volume_mb) as avg_volume
FROM relations r
WHERE r.type = 'interface_uses'
GROUP BY r.protocol;
```

Relations with properties can be queried in analytics just like entities.

**Implementation notes per persistence option** (cf. [§22.6 (Analyzability)](22-analyzability.md)):

- **Postgres + Apache AGE**: Relations are edges in the AGE graph plus a normal row in a `relations` table (with URN and properties). Queries use SQL or Cypher as needed. Good compromise.
- **Neo4j 5.x**: Relationships natively have properties; reification requires an additional "relation node" linked to the edge by convention. Some extra effort in modeling, but a common pattern.
- **RDF/SPARQL**: Reification is native (RDF-star/RDF 1.2). Semantically cleanest, but the query language is steep.
- **Git+YAML files**: Each relation is a YAML file with its own URN. Entity and relation URNs are treated the same. Simplest mental model, but query performance must be ensured via an index layer.

**Practical consequence**: For the walking skeleton, the choice is pragmatic. Postgres + AGE supports both cases without a break: simple relations remain edges, reified ones additionally get a table row with properties. The API hides this distinction from consumers.

### 22.6 Persistence Options

The API architecture from [§21 (API Architecture)](21-api-architecture.md) allows persistence to be kept interchangeable. Realistic options for your tool:

**Option 1 – Postgres + JSONB + Apache AGE**:

- Postgres as a solid base, JSONB for flexible properties
- Apache AGE as a graph extension (openCypher support)
- One database, two query paradigms, transactionally consistent
- *Recommended starting stack for the walking skeleton*

**Option 2 – Neo4j or Memgraph + read model**:

- Native property graph DB as primary persistence
- For analytics: CQRS read model in Postgres or DuckDB
- Event stream from [§21.5 (API Architecture)](21-api-architecture.md) keeps the read model in sync
- Strong graph performance, somewhat higher complexity

**Option 3 – Git as source + DuckDB/Postgres as index**:

- YAML/JSON files in Git as source of truth (maximally git-friendly)
- DuckDB or Postgres as index, rebuildable from Git
- Apache AGE or external graph service for traversal
- *Ideal for small to medium installations, poor for very large ones*

**Option 4 – Kuzu (embedded graph DB)**:

- Embedded property graph DB, Cypher support
- No server installation, good for CLI tools
- Analytics capabilities limited, but under development
- *Interesting for local developer installations*

**Defer the decision**: The API definition allows starting with option 1 or 3 and migrating later if performance requirements demand it.

### 22.7 Query Examples

**Graph traversal (Cypher style)**:

```cypher
// Which business services depend on application X?
MATCH (app:Application {urn: 'urn:ea:acme:application:sap-fi'})
MATCH path = (app)-[:realizes|dependsOn|exposedBy*1..4]->(svc:BusinessService)
RETURN svc, length(path) AS hops
ORDER BY hops
```

```cypher
// Which entities change between baseline and target plateau?
MATCH (e)-[r:hasLifecycleState]->(s)
WHERE r.plateau = 'baseline-2026' AND r.state = 'active'
MATCH (e)-[r2:hasLifecycleState]->(s2)
WHERE r2.plateau = 'target-2028' AND r2.state IN ['sunset', 'retired']
RETURN e.urn, e.name
```

**Analytics (SQL style)**:

```sql
-- TCO per capability per plateau
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
-- Standards compliance per domain
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

### 22.8 Stored Views as Named Queries

Standard analyses are maintained in the repository as **named, parameterized views**:

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
    SELECT ... (as above, parameterized)
```

Advantages:

- Reuse without query knowledge
- Versionable and reviewable in Git
- API endpoint `/api/v1/analytics/views/{name}?plateauId=...` for simple consumption
- Basis for dashboards and reports

### 22.9 Performance Requirements and Caching

```yaml
Performance targets (guideline values):
  - Single entity query: < 50ms (p95)
  - Graph traversal up to 3 hops at 10k entities: < 500ms (p95)
  - Analytics query over the full repo (10k entities): < 2s (p95)
  - Arc42 document generation: < 5s

Caching strategy:
  - Query result cache with TTL for analytics (invalidated by events)
  - Materialized views for expensive aggregations
  - Graph path cache for frequent traversal patterns
  - Schema cache on the client side (thanks to introspection endpoint)
```

The event system from [§21.5 (API Architecture)](21-api-architecture.md) is central to cache invalidation: a change to entity X invalidates all caches that reference X.

### 22.10 Offline and Pipeline Use

Analyzability must also work **without a running server** – for CI pipelines, local analyses, offline work:

**CLI tool** with a local query engine:

- Reads repository snapshots (Git export or API dump)
- Executes queries locally (embedded DuckDB or Kuzu)
- Identical query languages as the server
- Enables pipeline checks: "CI build fails if new applications without capability assignment exist"

**Example CI check**:

```bash
ea-tool query --local \
  "MATCH (a:Application) WHERE NOT (a)-[:realizes]->(:Capability) RETURN a.urn" \
  --expect-empty
```

### 22.11 Repository Changelog and Release Notes

Beyond the forensic audit log (see [§21.8 (API Architecture & Modularity)](21-api-architecture.md)), the tool offers a **curated repository changelog** for stakeholder communication. While the audit log contains every change, the changelog summarizes **important** changes into release units.

**Delimitation**:

- Audit log: raw, gapless forensics (for auditors)
- Changelog: curated, communicable summary (for stakeholders)

**Mechanics**:

```yaml
EntityType: RepositoryRelease
  properties:
    - tag: string                     # e.g. "2027-Q2-Release"
    - releaseDate: date
    - releaseType: snapshot | milestone | quarterly | on-demand
    - description: text               # narrative summary
    - status: draft | published
  relations:
    - includesChanges: ChangeEntry[]
    - taggedBy: Role
    - approvedBy: Role | GovernanceBody

EntityType: ChangeEntry
  properties:
    - changeType: added | modified | retired | reclassified | reorganized
    - significance: major | minor | patch
    - summary: text                   # for stakeholders, in plain language
    - rationale: text                 # why the change
  relations:
    - affectsEntities: Entity[]
    - affectsRelations: Relation[]
    - documentedInADR: ADR
    - addressesRequirement: Requirement
    - relatedWorkPackage: WorkPackage
```

**Three ways to create changelog entries**:

1. **Manually**: Architect writes a ChangeEntry when committing critical changes
2. **Semi-automatically**: Tool automatically suggests candidates from the audit log (esp. lifecycle transitions, new entities, retired entities), architect curates
3. **Automatically**: Certain structural changes (plateau activation, ADR approval) automatically create ChangeEntries

**Release aggregation**: Several ChangeEntries are combined into a RepositoryRelease. Typical triggers:

- Plateau change (e.g., "Plateau-2027-Q2 is the new current state")
- Quarterly as a reporting rhythm
- For larger architecture programs as a milestone

**Views**:

- **What has changed since release X?** – diff between two releases
- **Changelog for domain Y in Q2** – filtered changes per area
- **Communication report** – PDF/HTML export for stakeholder mailings, generated from active RepositoryRelease entries
- **Trend view** – how many changes per month/quarter, grouped by significance

**API endpoints**:

```text
GET    /api/v1/releases                    # all releases
GET    /api/v1/releases/{tag}              # specific release
GET    /api/v1/releases/{a}/diff/{b}       # diff between two releases
POST   /api/v1/changelog/suggest           # generate suggestions from audit log
POST   /api/v1/releases                    # set a new release tag
GET    /api/v1/entities/{urn}/history      # entity change history
```

**Constraints**:

```yaml
constraint: published-release-has-description
  appliesTo: RepositoryRelease
  when: "entity.status == 'published'"
  rule: "entity.description != null AND entity.description.length > 50"
  severity: error
  message: "A published release must have a meaningful description"

constraint: major-change-has-rationale
  appliesTo: ChangeEntry
  when: "entity.significance == 'major'"
  rule: "entity.rationale != null AND (entity.documentedInADR != null OR entity.addressesRequirement != null)"
  severity: warning
  message: "A major change should have an ADR or requirement reference"
```

### 22.12 Auditability and Query Logging

Important for enterprise use: Who queried what and when?

```yaml
Query audit log:
  - user: Role | Person | ApiKey
  - timestamp
  - endpoint, parameters, query-hash
  - result-metadata (not content – data protection)
  - execution-time
  - client (UI, module, script)
```

The audit log is a separate event stream and can be integrated into SIEM (Splunk, Elastic).

### 22.13 Constraints and Governance

```yaml
constraint: query-complexity-limit
  appliesTo: ApiQuery
  rule: "query.estimated-cost < threshold"
  severity: error
  message: "Query too complex – please add filters or use pagination"

constraint: named-view-has-owner
  appliesTo: View
  rule: "entity.owner != null"
  severity: warning

constraint: view-query-tested
  appliesTo: View (published)
  rule: "entity.hasTestCases == true"
  severity: warning
  message: "A published view should have test cases"
```

### 22.14 Recommendation for the Walking Skeleton

For the initial walking skeleton:

1. **Persistence**: Postgres + JSONB + Apache AGE (simplest production-ready stack)
2. **Query API**: typed graph endpoints + SQL analytics, Cypher as a stretch goal
3. **One stored view**: "Impact analysis" as a prebuilt graph traversal
4. **CLI query tool**: available from v0.1, because pipeline integration is a requirement from the start
5. **OpenAPI spec**: generated from code, verified in CI

This yields a small but complete vertical slice that covers both graph and analytics.

---

← [API Architecture & Modularity](21-api-architecture.md) · [🏠 Overview](../README.md) · [Open Issues](../90-backlog/23-open-issues.md) →
