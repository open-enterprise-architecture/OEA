# Concept Paper Changelog

This document records how the concept paper itself has evolved. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), versioning follows Semantic Versioning (at concept level: major = fundamentally new concepts, minor = new chapters/sections, patch = corrections).

## [v0.20.1] – Collaboration decisions

### Changed

- §21.10: ADR-047 accepted (server-ordered operations, text CRDT only for documents); version granularity of co-editing set (every operation audited, versions per author closed after 2 minutes idle or 15 minutes at most, named checkpoints); commenting with read access (owner decisions of 2026-10-28)

## [v0.20.0] – Real-time collaboration

### Added

- §21.10 Real-time collaboration: presence and live changes in every view, automatic merge of concurrent edits in diagrams and documents, sticky notes and comment threads in diagrams; collaboration session (WebSocket) next to the event stream (SSE); working state turned into regular versioned and audited repository changes (owner request of 2026-10-28, UC-24, ADR-047 proposed)

### Changed

- §23 issue #26: WebSocket is needed in v1.x for the collaboration session, not only from v2.0; SSE stays the integration backbone

## [v0.19.3] – Nuxt UI instead of PrimeVue

### Fixed

- §24: frontend component library Nuxt UI 4 instead of PrimeVue 4 (ADR-040); PrimeVue 5 is no longer open source

## [v0.19.2] – Java 25 LTS

### Fixed

- §24: backend stack updated to Java 25 LTS (ADR-039); longer vendor support than Java 21

## [v0.19.1] – Spring Boot 4.1

### Fixed

- §24: backend stack updated to Spring Boot 4.1 (ADR-038); Spring Boot 3 is out of open-source support

## [v0.19.0] – Security decisions ADR-033 to ADR-036

### Changed

- §21.8 audit trail: append-only also for administrators (separate owner role), hash chain with checkpoints in all editions, pseudonymous principals, encrypted values of restricted properties, retention floor 1 year (ADR-033)
- §21.4: analytics endpoints annotated (SQL against the reporting schema, `analytics:query`, disabled by default; named views deferred)
- §22: decision status added; PostgreSQL without a graph extension; raw analytics SQL-only against per-principal masked reporting views with hard limits; Cypher and named views deferred; walking skeleton recommendation updated (ADR-030, ADR-034)

### Rationale

Follow-up of the security review 2026-10 (SEC-13, SEC-17): the concept still recommended Apache AGE and Cypher and allowed hashes instead of protection for sensitive audit values.

## [v0.18.2] – §21.8 authorization model aligned with ADR-032

### Changed

- §21.8: authorization roles are separate from business roles of the EA model; RBAC with permission catalog, deny by default and separation of duties (ADR-032); OIDC with PKCE; machine access via OAuth 2.0 client credentials instead of API keys (REQ-176)

### Rationale

The security review (SEC-01) rated the use of business roles as access-control roles as critical: anyone who can edit EA content could grant themselves permissions.

## [v0.18.1] – Markdown formatting

### Fixed

- Markdown lint findings resolved in all chapters: blank lines around lists, headings and code blocks; code block languages; bold pseudo-headings converted to real headings. No content changes.

## [v0.18.0] – §9 BPMN modeling within OEA

### Changed

- §9 (Process Architecture): OEA now includes a BPMN 2.0 editor that covers the Descriptive Process Modeling Conformance sub-class of BPMN 2.0.2; the process landscape (L1–L4) stays abstract and notation-neutral
- §9.1, §9.6: BPMN elements are built-in `bpmn-*` entities; BPMN XML import/export at Descriptive level; reference-only mode for externally maintained models kept
- §9.9: tool scope updated – execution-level BPMN, simulation and workflow execution remain out of scope

### Rationale

§9 excluded BPMN authoring, while REQ-076, REQ-081 and UC-10 already required modeling BPMN processes directly in OEA. The concept now follows the requirements; the Descriptive level is sufficient for documentation and analysis in the EA context.

## [v0.17.3] – Corrections found during translation

### Fixed

- §17: last subsection renumbered from 9.9 to 17.9
- §20: link text to the API chapter corrected (§21, not §22); typo "GPDR" corrected to "GDPR"
- §21.9: "Schwalbach tests" corrected to contract tests against the spec (e.g. Schemathesis)
- §22: reference to stored views corrected (§22.8, not §22.7)

## [v0.17.2] – Concept paper translated to English

### Changed

- All chapters, README, INDEX and this changelog translated from German to English; content and section numbering unchanged
- Chapter file names translated (e.g. `06-kern-entitaetstypen.md` → `06-core-entity-types.md`); all links updated

### Rationale

The project language for documentation and code is English (AGENTS.md). The translation is a pure language change; no concept content was altered.

## [v0.17.1] – §24 aligned with current ADRs and sprint plan

### Changed

- §24 (Next Steps): tech stack details corrected (Java 21 + Spring Boot 3 + Hibernate instead of NestJS/Drizzle, Flyway instead of Drizzle Kit, OpenAPI spec-first instead of code-first) per ADR-012, ADR-013, ADR-015
- §24: walking skeleton switched to the updated definition (31 SP, S1–S2, entities via batch API); module sprint list replaced by a reference to `docs/sprint-plan.md`; ITSM/GDPR/security marked as deferred beyond v1.0 (§23)

### Rationale

§24 contradicted the accepted ADRs (stack switch to Java in ADR-012) and the sprint plan. Erroneous UC designations ("UC-05 Canvas", "UC-01 Entity Management") have been removed.

## [v0.17] – §23 Open issues fully worked through; §24 oriented toward implementation

### Changed

- §23 (Open Issues): all 47 original items categorized and closed
  - 9 items closed by ADRs or business objects (ADR-001–007, process.md)
  - 14 items decided for v1.0 in the concept (persistence PostgreSQL+AGE, bitemporality, diff semantics, query languages/openCypher, event transport/SSE, module isolation/monolith, audit trail, among others)
  - 24 items deliberately deferred to the integration phase or v2.0 (ITSM/PPM/GRC/GDPR connectors, special taxonomies, advanced governance features)
- §24 (Next Steps): updated to the implementation phase; pending tech ADRs (ADR-012–015) explicitly named; walking skeleton scope and module sprint order documented

### Rationale

The requirements phase is complete (all DoD criteria met, trace check green). §23 was the last open block in the concept in terms of content. The categorization clarifies what v1.0 must contain and what is deliberately deferred to later phases.

## [v0.16] – Local authentication as an alternative to OIDC

### Added

- §21.8 Security and authorization: local authentication mechanisms for self-hosting without an external identity provider (passkey/WebAuthn as standard, username/password with TOTP as second factor, username/password without a second factor as a deliberately risky minimal variant)
- Clarification that OIDC includes SSO via an existing identity provider session

### Rationale

UC-01 (Login) and stakeholder SH-06 (Max, SME operator) explicitly require that OEA does not enforce its own user management without SSO, but also remains self-hosting capable without an external IdP. OAuth 2.0/OIDC alone presupposes an IdP, which is not necessarily present in the single-user SME case (SH-03 Kurt).

## [v0.15] – Visualization strategy anchored as an open decision

### Added

- §21.2.1 Visualization strategy: deliberately open decision on diagram notation
- Trade-off table text-based DSL vs. graphical editors (10 criteria)
- Use-case-specific renderer choice as a sketch (seven diagram types with possible backends)
- Five questions to be clarified before a notation decision
- Interim recommendation for the concept documentation (descriptive instead of DSL-specific)
- Open items 46 (visualization strategy) and 47 (legacy tool migration)

### Changed

- §21.2: renderer entry toned down – concrete notations removed, reference to §21.2.1
- Positioning: diagram notation is an implementation detail, not the concept core

### Rationale

The notation decision should be made after the requirements engineering phase is complete, when personas, use cases, and usage patterns are clear. Until then, all options are open.

## [v0.14] – Progressive disclosure and non-enforcement of the logical layer

### Added

- New guiding principle in the README: "Progressive disclosure instead of enforced depth" – conceptual correctness is supported, not enforced
- §6.1.1 section on cognitive reality ("People think in solutions, not in layers") with three insights:
  - The logical layer is optional, not mandatory
  - Progressive disclosure via three views (inventory, layers, landscape map (Bebauungsplan))
  - Optional auto-generation of logical stubs from physical
- §12.4 Progressive disclosure with concrete view definition (inventory, layers, landscape map) and four maturity levels for step-by-step build-up
- Trade-off explicitly documented: an OSS tool must be low-threshold and must not enforce analytical cleanliness

### Changed

- Constraints on the logical layer consistently marked as `warning` or `hint`, not as `error`

## [v0.13] – Change log concepts elaborated

### Added

- §11.5 Plateau diff as a structured comparison between plateaus (diff dimensions, calculation rules, filtering, API endpoint)
- §22.11 Repository changelog and release notes (RepositoryRelease + ChangeEntry, three creation paths, aggregation views)
- Delimitation of audit log, change log, and entity change history in §21.8
- This CHANGELOG document

### Changed

- §22 subsection numbering (old 22.11–22.13 are now 22.12–22.14)

## [v0.12] – Requirements repository

### Added

- §7.2 Requirement EntityType significantly expanded (lifecycle, trace connections upward/downward, risk connections, hierarchy and relationship types)
- Three stereotypes: ArchitectureRequirement (default), SystemRequirement (with externalReference), QualityRequirement
- §10.7 Requirements repository aspects (linking discipline, views, constraints, anti-patterns)
- Three new open items: versioning trigger, system requirements scope, ReqIF integration

### Changed

- INDEX description of §10 extended by requirements repository

## [v0.11] – Application-vs-technology delimitation refined

### Added

- §6.1.1 Delimitation of application vs. technology with four decision principles (business purpose, replaceability, ownership, cataloging)
- Acknowledgment of the inherent difficulty of the delimitation
- Two legitimate organizational principle variants as templates
- Three dual modeling options for platform products (both entities, stereotype, ApplicationService as a bridge)
- Constraint example for enforcing an organization-wide classification decision
- Open item 40 on the default classification choice

### Fixed

- The earlier didactic sharpening "Can a business stakeholder do something with it?" as a TOGAF test was inaccurate; the correct TOGAF definition is structural (functionality vs. infrastructure)

## [v0.10] – GRC, GDPR, and ISMS integration

### Added

- §20 GRC, GDPR, and ISMS integration as a new chapter (GDPR Art. 30, third-country transfers, ISO 27005 asset model, audit trail, property-level authZ, segregation of duties)
- §10.2 ProtectionNeed EntityType (CIA protection need per ISO 27005 / BSI IT-Grundschutz)
- §21.8 Property-level authorization as a mandatory feature, audit trail specification
- Three new guiding principles (EA as asset master data supplier, property-level authZ, complete audit trail)
- Seven new open items on GRC/GDPR/ISMS

### Changed

- Chapter renumbering: old §20 (API) → §21, old §21 (analyzability) → §22, old §22 (open issues) → §23, old §23 (next steps) → §24
- File renames accordingly

## [v0.9] – Reification of relations

### Added

- §2.1 Relations as first-class objects (reification) – RelationTypes can have EntityType OR RelationType as source/target
- Reification depth (default 2, configurable per schema)
- §13 InformationFlow as relation modeling (option B as recommendation)
- §22.5 Reified relations in queries with three scenarios
- ADR.affects, Waiver.grantedFor, ComplianceDeviation.affects now accept Entity[] | Relation[]
- Three new open items on reification (max depth, addressing, UI representation)

### Changed

- §22 subsection numbering shifted (reification section inserted)

## [v0.8] – API architecture and analyzability

### Added

- §20 API architecture and modularity (layers, modules, events, SDK)
- §21 Analyzability and query architecture (two query paradigms, persistence options, stored views)
- BPMN as an exemplary add-on module
- Seven new open items on API/query

### Changed

- Scope description extended by API-centric architecture and modular extension
- Two new guiding principles: API-first and persistence neutrality

## [v0.7] – Motivation, organization, processes, cross-cutting

### Added

- §7 Motivation, stakeholders, and goals (vision, driver, goal, outcome, stakeholder, concern, requirement, glossary)
- §8 Organization, roles, and persons (GDPR-aware, with skills, RACI, GovernanceBody)
- §9 Process architecture (abstract modeling, BPMN as adapter, process landscape, CRUD matrix, value streams)
- §10 Cross-cutting (location, security, sustainability, localization, contract, deliverable)
- Recommendation of option 3 for process modeling (abstract with BPMN/EPC as export formats)

## [v0.6] – Architecture principles, standards, and decisions

### Added

- §5 Architecture principles, standards, and decisions (principle vs. standard vs. guideline)
- ArchitectureWaiver with mandatory expiry date
- ComplianceDeviation as three states (deviation, waiver, violation)
- ADR with superseding and n:m affects relation
- Navigation chain Goal → Principle → Standard → ADR → Entity → Deviation → Waiver → ADR

## [v0.5] – Enterprise continuum and schema evolution

### Added

- §4 Enterprise continuum and technical reference model (ABBs, SBBs, patterns, reference architectures, TRM)
- §15 Schema evolution (subjective scopes, evolution patterns, versioning, data quality scores, tag-based piloting)
- Schema profiles as organizational configuration

## [v0.4] – PPM and agile integration

### Added

- §18 PPM integration (project, program, portfolio, demand, business case, benefit, architecture review board)
- §19 Agile scaling frameworks (SAFe and LeSS with different philosophies, schema profiles)
- TIME model categorization
- Architectural runway as a stereotype

## [v0.3] – Relationship to frameworks refined

### Added

- §3 Relationship to Arc42 and TOGAF (seven friction points with resolutions, common metamodel as a bridge)

## [v0.2] – ITSM integration

### Added

- §17 ITSM integration (CMDB and service catalog mastership, ITIL 4 practices, delimitation of the service term)
- ITSM coexistence as a guiding principle
- External reference mechanism with property-level mastership

## [v0.1] – First version

### Added

- §1 Positioning and objectives with original guiding principles
- §2 Meta-metamodel (M2/M1/M0)
- §6 Core entity types (TOGAF content metamodel, Arc42, BPMN mapping)
- §11 Temporal model (plateaus, lifecycle, gaps, work packages, target states)
- §12 Domain concept and views (n:m classification, landscape maps (Bebauungspläne), stored views)
- §13 Linking the business and technology levels
- §14 Extensibility (custom EntityTypes, stereotypes, constraints)
- §16 Example walkthrough (CRM migration)
- §23 Open issues (initial ADR candidates)
- §24 Next steps
