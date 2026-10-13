## 21. API Architecture and Modularity

The tool is designed as a **modular system**: a lean core with a stable, versioned API to which business add-on modules and integrations dock. This chapter details the API layers, the module concept, and the delimitation of core vs. extension.

### 21.1 Architecture Layers

```text
┌──────────────────────────────────────────────────────┐
│  UI / CLI / Pipeline tools / Add-on modules          │  ← Consumers
├──────────────────────────────────────────────────────┤
│                    Public API                         │  ← Contract
│  - REST  - GraphQL  - gRPC (optional)                │
├──────────────────────────────────────────────────────┤
│  Query engine  │  Command engine  │  Event stream    │  ← Application layer
│  (read)        │  (write)         │  (react)          │
├──────────────────────────────────────────────────────┤
│  Meta-metamodel + schema engine + validation         │  ← Domain layer
├──────────────────────────────────────────────────────┤
│  Persistence (interchangeable)                       │  ← Infrastructure
└──────────────────────────────────────────────────────┘
```

The API is the **hard contract to the outside**. Everything above the API line (UI, modules) does not know the layers below.

### 21.2 Core vs. Modules

**The core contains**:

- Meta-metamodel and schema engine
- Entity and relation management (CRUD + validation)
- Plateau and lifecycle management
- Query and traversal engine (see [§22 (Analyzability)](22-analyzability.md))
- Event stream for change notifications
- Authentication/authorization
- Versioning and history

**Modules are business extensions** that communicate via the API:

- **BPMN module** – import/export of BPMN XML, mapping to abstract process entities
- **ITSM connectors** – ServiceNow, BMC Helix, Jira SM (see [§17 (ITSM Integration)](../60-integrations/17-itsm-integration.md))
- **PPM connectors** – Jira, OpenProject, ServiceNow SPM (see [§18 (PPM Integration)](../60-integrations/18-ppm-integration.md))
- **Renderers** – concrete notation(s) still to be decided (see §21.2.1 visualization strategy below)
- **Documentation generators** – Arc42 documents, TOGAF phase outputs, PDF reports
- **Importers/exporters** – Excel, CSV, ArchiMate Open Exchange, common OEA formats
- **Discovery adapters** – cloud discovery (AWS, Azure, GCP), CMDB discovery, API discovery
- **Skill frameworks** – SFIA, EuroCIO
- **Reference frameworks** – APQC-PCF, eTOM, SCOR

Criterion for "module": If it makes no sense without the core but is also not needed by all installations – then it is a module.

#### 21.2.1 Visualization Strategy – Deliberately Open Decision

The question of which diagram notation(s) the tool supports is **deliberately not yet decided**. It is to be decided after the requirements engineering phase is complete, when it is clear:

- Which personas need which views
- How modelers want to think and work in daily practice
- Which trade-off between maintainability, layout quality, and learning curve is acceptable

##### Trade-off: Text-Based vs. Graphical

Both approaches have fundamental strengths and weaknesses that cannot be optimized away by tool choice – only weighed:

| Aspect | Text-based DSL (PlantUML, Mermaid, D2, Graphviz, Structurizr) | Graphical editors (Archi, drawio, Sparx EA) |
|---|---|---|
| **Pipeline suitability** | ✓ ideal (Git-diffable, PR-reviewable, CI-generatable) | ✗ binary/XML files, poorly diffable |
| **Maintainability over time** | ✓ refactoring via find/replace possible | ⚠️ manual per diagram |
| **Layout control** | ⚠️ auto-layout, often suboptimal | ✓ pixel-precise control |
| **Visual quality for presentations** | ⚠️ functional, rarely impressive | ✓ precisely and aesthetically adjustable |
| **Learning curve** | ⚠️ DSL must be learned | ✓ visual drag-and-drop intuitive |
| **Consistency across many diagrams** | ✓ DSL enforces style consistency | ⚠️ easily inconsistent |
| **Generatability from model** | ✓ trivial | ⚠️ laborious (calculate geometry) |
| **Post-editing by stakeholders** | ✗ requires DSL knowledge | ✓ anyone can adjust |
| **Versioning** | ✓ text in Git | ⚠️ XML diff possible, but confusing |
| **Standards conformance (ArchiMate)** | ⚠️ varies by tool | ✓ Archi is the reference implementation |

Neither world is universally superior. A **use-case-specific combination** is conceivable and should be evaluated after completion of the stakeholder and use case analysis.

##### Use-Case-Specific Renderer Choice (Sketch)

Instead of a single "right" notation, it is conceivable that the tool supports **multiple backends** for different diagram types:

| Diagram type | Possible backend | Rationale |
|---|---|---|
| Sequence diagrams | text-based | textual DSL is unbeatable here (PlantUML strong) |
| ADR-embedded sketches | lightweight, inline-renderable | Markdown preview suffices (Mermaid strong) |
| Landscape maps (Bebauungspläne) | text-based generated + graphically post-editable | generatable from model, but presentation visually demanding |
| ArchiMate views | graphical editor with XML export | standards conformance important |
| C4 model diagrams | text-based (Structurizr DSL) | specialized standard |
| Capability heatmaps | graphically rendered in the tool | matrix type, neither DSL nor editor makes sense |
| Data flow diagrams | text-based (Graphviz/D2) | auto-layout works well here |

##### What Needs to Be Clarified

After completion of the requirements phase, the following need to be answered:

1. **Which personas need which views?** (from the use case catalog)
2. **Which notations do the target users already know?** (Archi experience? PlantUML? C4?)
3. **How important is pipeline generation vs. manual maintenance?** (CI-driven documentation vs. workshop diagrams)
4. **How important is ArchiMate standards conformance?** (regulatory pressure? audit requirement?)
5. **Which existing tools should remain usable?** (existing Sparx/Archi models?)

These questions will be addressed as an ADR once the answers from requirements engineering are available.

##### Interim Recommendation for the Concept Work

Until the decision is made, the following applies to the concept documentation:

- Diagrams are **notated in whatever way currently suits the documentation best** (typical: Mermaid for inline Markdown, free text for sketches)
- No hard binding to a concrete notation in the concept – examples remain descriptive ("landscape map (Bebauungsplan) with three time columns" instead of PlantUML code)
- If DSL examples are needed, they are marked as "exemplary, replaceable"

This avoids later notation decisions requiring extensive rework of the concept.

### 21.3 API Conventions

**Versioning**: Semantic versioning at API level. Path versioning (`/api/v1/...`), no header tricks. Major breaking changes only with a deprecation period of at least 12 months and parallel operation.

**Resource model**: REST resources follow the EntityTypes from the metamodel. Relations are their own sub-resources (`/api/v1/entities/{id}/relations`) for navigation.

**Format**: JSON as default. YAML as an alternative (git-friendly). Content negotiation via the `Accept` header.

**Identity**: URNs according to the decision in [§23 (Open Issues)](../90-backlog/23-open-issues.md) in the open issues (`urn:ea:<org>:<type>:<id>`). URNs are first-class in payloads, not numeric DB IDs.

**Pagination, filtering, sorting**: Standard query string conventions (`?page=`, `?filter=`, `?sort=`). Consistent across all resources.

**Idempotency for write operations**: `Idempotency-Key` header for non-GET requests. Prevents duplicate creation on network errors.

### 21.4 API Endpoints Overview

```text
# Entity management
GET    /api/v1/entities?type=Application&...
POST   /api/v1/entities
GET    /api/v1/entities/{urn}
PATCH  /api/v1/entities/{urn}
DELETE /api/v1/entities/{urn}

# Relations
GET    /api/v1/entities/{urn}/relations
POST   /api/v1/relations
GET    /api/v1/relations/{id}

# Graph traversal
POST   /api/v1/graph/traverse              ← see [§22 (Analyzability)](22-analyzability.md)
POST   /api/v1/graph/shortest-path
POST   /api/v1/graph/neighborhood

# Analytics / aggregation (ADR-034: SQL against the reporting schema, analytics:query, disabled by default)
POST   /api/v1/analytics/query             ← see [§22 (Analyzability)](22-analyzability.md)
GET    /api/v1/analytics/schema
GET    /api/v1/analytics/views/{name}      ← deferred

# Schema and metamodel
GET    /api/v1/schema
GET    /api/v1/schema/types
GET    /api/v1/schema/types/{name}

# Plateaus and temporal views
GET    /api/v1/plateaus
GET    /api/v1/entities?asOfPlateau=target-2028
POST   /api/v1/plateaus/{a}/diff/{b}

# Validation and compliance
POST   /api/v1/validate
GET    /api/v1/compliance/deviations
GET    /api/v1/compliance/waivers

# Events
GET    /api/v1/events/stream               ← Server-Sent Events
POST   /api/v1/events/subscriptions        ← Webhooks

# Import/export
POST   /api/v1/import
POST   /api/v1/export
```

### 21.5 Event Stream as the Integration Backbone

Modules and external systems must be able to react to repository changes – an event stream exists for this.

```yaml
Event types:
  - EntityCreated, EntityUpdated, EntityDeleted
  - RelationCreated, RelationDeleted
  - PlateauCreated, PlateauActivated
  - ValidationFailed, ComplianceViolationDetected
  - WaiverExpiring (3 days before expiry)
  - SchemaVersionChanged
  - ADRStateChanged
```

Consumers:

- BPMN module reacts to ApplicationComponent changes (potential process implications)
- ITSM connector synchronizes property changes in both directions
- Renderers invalidate cached diagrams for affected entities
- Audit systems log change history

**Transport options**: Server-Sent Events (simple, HTTP), WebSocket (bidirectional), webhooks (pull-based), Kafka/NATS (if available).

### 21.6 Module SDK

To facilitate the development of modules, a **module SDK** is provided:

```yaml
Module SDK offers:
  - Typed API clients (initially: Python, TypeScript)
  - Event subscription helpers
  - Schema introspection
  - Authentication helpers
  - Test harness (mock repository for module tests)
```

The SDK itself is open source and uses only the public API – no privileged calls. What the SDK can do, any other consumer can do too.

### 21.7 BPMN as an Exemplary Add-on Module

The BPMN module serves as a **reference implementation** for module development:

```yaml
BPMN module responsibilities:
  - Import of BPMN 2.0 XML files
  - Mapping to abstract process entities ([§9 (Process Architecture)](../20-entities/09-process-architecture.md))
  - Export of process entities to BPMN 2.0 XML
  - Bidirectional synchronization with external BPM tools (Camunda, Signavio, BIC)
  - Rendering of BPMN diagrams
  - Validation against the BPMN 2.0 schema

BPMN module uses (via API):
  - Entity CRUD for Process, Activity, Flow, Pool, Lane
  - Schema introspection for validation
  - Events for change notification

BPMN module registers:
  - New content types: application/bpmn+xml
  - New viewers/renderers
  - New import/export formats
```

Further process notations (EPC, VSM, DMN for decision logic) can follow as analogous modules without changing the core.

### 21.8 Security and Authorization

```yaml
Authentication:
  - OAuth 2.0 / OIDC with PKCE (standard, incl. SSO via existing identity provider session)
  - OAuth 2.0 client credentials for machine-to-machine (service accounts, no API keys)
  - Optional mutual TLS for highest security requirements
  - Local authentication (optional, for self-hosting without an external IdP):
    - Passkey / WebAuthn (passwordless, standard for local auth)
    - Username/password with TOTP as second factor
    - Username/password without a second factor (minimal variant, not recommended)

Authorization (ADR-032):
  - RBAC with a fixed, versioned permission catalog (e.g. entity:write, metamodel:write, visibility:configure, analytics:query)
  - Roles are configurable sets of permissions; built-in roles cannot be changed
  - Deny by default: every API operation declares its required permission
  - Object-level checks and property-level authorization (mandatory feature)
  - Separation of duties: nobody changes their own access; metamodel and security administration are separate roles
```

**Authorization roles are not business roles.** The roles modeled in the repository (see [§8.2 (Organization & Roles)](../20-entities/08-organization-roles-persons.md), e.g. "Process Owner" or "Enterprise Architect" as an organizational role) are EA content: they describe the organization and never grant permissions in OEA. Access to OEA is controlled only by authorization roles, assigned locally or mapped from IdP groups via immutable identifiers (ADR-032). A person account may be linked to its EA person entity, which is used for connection-scoped property visibility.

**Local authentication**: OIDC presupposes an existing identity provider. For single-user and SME self-hosting without a dedicated IdP team (see stakeholders SH-03, SH-06), OEA must also be operable without an external IdP. The local mechanisms are deliberately tiered: passkey/WebAuthn as the recommended standard (passwordless, phishing-resistant), username/password with TOTP as second factor for environments without WebAuthn support, and username/password without a second factor as a deliberately risky minimal variant that is flagged accordingly in the audit log. All local mechanisms are optional and do not exclude OIDC – an OEA instance can offer both paths in parallel.

**Property-level authorization**: Beyond entity-level permissions, the API supports granular access control on individual properties. This is a mandatory feature, not optional, because sensitive information (personal data, detailed protection needs, waiver justifications) must not be visible to all users.

Mechanics:

```yaml
propertyAuthorization:
  - entity: DataEntity
    property: personalDataCategory
    read: [DPO, ISO, Architect]
    write: [DPO]
  - entity: ArchitectureWaiver
    property: reason
    read: [ARB-Member, Architect, Auditor]
    write: [ARB-Member]
```

API behavior:

- For GET requests, unauthorized properties are automatically removed from the response (no error message, only filtering)
- For PATCH requests, a write attempt on an unauthorized property results in HTTP 403
- Schema introspection (`/api/v1/schema/types/{name}`) shows only the properties for which the requesting principal has at least read permission
- The audit log records who queried which protected properties and when (see audit trail below)

**Audit trail (mandatory feature)**: All changes, security events and sensitive read operations are logged tamper-evidently (ADR-024, hardened by [ADR-033](../../adrs/ADR-033-audit-trail-hardening.md)):

```yaml
AuditLog entry:
  - timestamp
  - principal: person, service account or system admin account (pseudonymous reference, no name or e-mail)
  - action: create | update | delete | read-sensitive
  - target: URN
  - property: string (for granular changes)
  - oldValue, newValue (for changes; values of restricted properties encrypted, ADR-033)
  - clientContext: UI | Module | Script
  - changeReason: string (optional, for commit messages or change tickets)
```

Properties of the audit trail:

- **Append-only, also for administrators**: the runtime database role may only insert and read; schema ownership and migrations use a separate role (ADR-033)
- **Tamper-evident**: every record is part of a hash chain with periodic checkpoints in all editions; integrity can be verified via the API
- **Separate storage**: Audit data resides in a separate schema or database so that access rights can be configured independently of the main repo
- **Exportable**: JSON Lines for external SIEM integration (Splunk, Elastic)
- **Subject to retention**: configurable with a floor of 1 year and a default of 7 years; records are deleted only by the retention policy (owner decisions Q3/Q10)
- **Itself access-protected**: Reading audit logs requires the permission `audit:read` (role `auditor`, ADR-032)

#### Delimitation: audit log, change log, and entity change history

Three related but different concepts are cleanly separated in the tool:

| Concept | Purpose | Properties | Consumers |
|---|---|---|---|
| **Audit log** | Forensics, regulatory evidence | Gapless, append-only, records every change, can be "noisy" | Auditors, compliance, SIEM |
| **Change log / release notes** | Stakeholder communication | Curated, summarized, "important" vs. "unimportant" changes, with release tags | Architects, business units, management |
| **Entity change history** | History of a concrete object | Per entity, all versions with timestamp and rationale | Architects maintaining individual objects |

They draw from the same source (every change produces an audit entry) but are filtered and aggregated differently:

- The **audit log** is the raw, complete truth
- The **change log** is the curated view for outsiders (see [§22 (Analyzability)](22-analyzability.md))
- The **entity change history** is the per-object view in the UI / API (`GET /entities/{urn}/history`)

Details on the business use of these mechanisms in [§20 (GRC/GDPR/ISMS Integration)](../60-integrations/20-grc-gdpr-isms-integration.md).

### 21.9 Documentation and Discoverability

**OpenAPI specification** for the complete API, generated from code. Contract tests against the spec in CI (e.g. Schemathesis).

**Schema introspection**: The API knows its own metamodel and makes it available. Tools can dynamically discover the currently active EntityTypes and properties.

**Changelog**: Every API version has a machine-readable changelog (e.g., OpenAPI diff) that documents breaking changes, deprecations, and additions.

---

← [GRC, GDPR, and ISMS Integration](../60-integrations/20-grc-gdpr-isms-integration.md) · [🏠 Overview](../README.md) · [Analyzability & Query Architecture](22-analyzability.md) →
