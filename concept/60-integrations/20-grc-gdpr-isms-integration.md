## 20. GRC, GDPR, and ISMS Integration

Regulatory governance and information security are two disciplines that rely heavily on EA data – both need current, complete inventories of applications, technologies, data components, interfaces, and processes. Without integrated connectivity, this inevitably leads to parallel shadow inventories that become outdated over time. This chapter describes the interfaces to **GRC tools** (governance, risk, compliance), **GDPR/data protection tools**, and **ISMS tools** (information security management systems).

### 20.1 Delimitation of the Three Disciplines

| Aspect | GRC / Compliance | GDPR / Data protection | ISMS |
|---|---|---|---|
| Primary question | "Which external requirements do we meet?" | "Which personal data do we process and how?" | "Which risks threaten our assets?" |
| Target group | Compliance officer, legal, auditor | Data protection officer (DPO) | Information security officer (ISO/CISO) |
| Core outputs | Evidence packages, audit reports, regulatory reports | Record of processing activities, DPIA, access request responses | Risk register, SoA, action plan |
| Thinks in | Requirements → controls → evidence → findings | Purpose → data category → legal basis → data subjects | Asset → threat → vulnerability → risk → treatment |
| Frame of reference | ISO 27001, NIST CSF, SOX, DORA, NIS2 | GDPR, revDSG, LGPD, CCPA | ISO 27001/27005, BSI IT-Grundschutz, TISAX |
| Tool examples | ServiceNow GRC, SAP GRC, MetricStream, Archer | OneTrust, TrustArc, Smart Global Governance | Verinice, HiScout, ibi iRIS, OpenPages |

The disciplines overlap in business terms but are usually supported by different tools. Some vendors (ServiceNow, Archer, OpenPages) cover all three, but many organizations combine specialized solutions.

### 20.2 Role of the EA Repository

EA is the **structural data supplier** for all three disciplines. What EA delivers:

- Asset inventory (applications, technologies, data components)
- Classifications (protection need, data categories)
- Data flow maps (which data flows where)
- Process-to-system linking
- Service provider relationships (from contracts)
- Lifecycle and version information

What EA **does not take on**:

- Risk register with quantitative assessment and risk appetite
- Control effectiveness tests and evidence collection
- Data subject request workflow (access, erasure, portability)
- Data protection impact assessments (DPIA) as a formal workflow
- Statement of Applicability (SoA) as an ISO 27001 artifact
- Audit preparation and execution

These functions remain in the specialized tools. The OEA provides the asset basis on which they work.

### 20.3 Metamodel Extensions for GDPR

For GDPR Article 30 (record of processing activities), the existing EntityTypes are supplemented.

**DataEntity** optionally gains:

```yaml
DataEntity extended by:
  - isPersonalData: boolean
  - personalDataCategory: enum[standard, special-art-9, criminal-art-10]
  - dataSubjects: enum[employees, customers, prospects, suppliers, minors, patients, other][]
  - processingPurposes: ProcessingPurpose[]
  - retentionPeriod: duration
  - retentionJustification: text
  - legalBasis: enum[consent, contract, legal-obligation, vital-interests, public-task, legitimate-interest]
  - legalBasisDetails: text
```

**ApplicationComponent** and **Interface** gain:

```yaml
ApplicationComponent / Interface extended by:
  - processingLocations: Location[]       # where processing takes place
  - dataTransferOutsideRegion: boolean    # third-country transfer?
  - transferMechanism: enum[adequacy-decision, standard-contractual-clauses, binding-corporate-rules, derogation, none]
  - transferDetails: text
```

**New EntityTypes**:

```yaml
EntityType: ProcessingPurpose
  properties:
    - name
    - description
    - legalBasis: enum[...]
    - retentionPeriod: duration
  relations:
    - appliesTo: DataEntity[]
    - realizedBy: BusinessProcess[]

EntityType: DataProcessor (processor)
  basedOn: Vendor / OrganizationUnit (external)
  properties:
    - dpaSignedDate: date             # data processing agreement
    - dpaExpiresDate: date
    - subprocessorsAllowed: boolean
    - auditRights: text
    - certifications: string[]        # ISO 27001, SOC 2, etc.
  relations:
    - processesDataFor: OrganizationUnit
    - processesData: DataEntity[]
    - governedByContract: Contract

EntityType: DataTransfer
  properties:
    - sourceLocation: Location
    - targetLocation: Location
    - transferMechanism: enum[...]
    - frequency: string
    - dataVolume: string
  relations:
    - transfers: DataEntity[]
    - between: Application | DataProcessor
    - via: Interface
```

### 20.4 Metamodel Extensions for ISMS (ISO 27001/27005)

The ISMS works with an established vocabulary that should be available in OEA as a reference schema:

**Primary vs. supporting assets**: The ISO 27005 terminology distinguishes:

- **Primary assets**: what is to be protected (business processes, information)
- **Supporting assets**: what primary assets depend on (systems, technologies, persons, locations, suppliers)

The OEA delivers both without a separate asset concept of its own. BusinessProcess and InformationObject are primary assets; ApplicationComponent, TechnologyComponent, and Location are supporting assets. The ISO 27005 categories are attached to the EntityTypes as a stereotype or classification property:

```yaml
Entity extended by (optional):
  - isoAssetCategory: primary | supporting
  - iso27005Type: process | information | software | hardware | network | personnel | site | organization | service
```

**Threats and vulnerabilities** remain maintained in the ISMS tool – but OEA can hold references to them:

```yaml
Entity extended by:
  - knownThreats: ThreatReference[]       # reference into the ISMS tool
  - openVulnerabilities: VulnerabilityReference[]
  - riskScore: number                      # aggregated from the ISMS
  - lastRiskAssessment: date
```

### 20.5 Mastership per Concept

| Concept | EA master | ISMS master | GDPR tool master |
|---|---|---|---|
| Asset existence and structure | ✓ | | |
| CIA protection need | ✓ (or shared) | ✓ (or shared) | |
| Data category (personal/non-personal) | ✓ | | |
| Purpose of processing | | | ✓ |
| Legal basis | | | ✓ |
| Retention / deletion periods | ✓ | | |
| Threat catalog | | ✓ | |
| Risk assessment (probability × impact) | | ✓ | |
| Control framework (ISO 27001 Annex A) | as continuum artifact (§4) | ✓ | |
| Control effectiveness and evidence | | ✓ | ✓ (for GDPR controls) |
| Data protection impact assessment (DPIA) | | | ✓ |
| Data subject requests | | | ✓ |
| Statement of Applicability | | ✓ | |
| Data processing agreements | Master data (Contract) | | ✓ (for DPA maintenance) |
| Third-country transfers | Structure (transfer relationship) | | ✓ (for justification) |

**Pragmatic rule**: If information is structural (What exists? How is it connected?), it lives in EA. If it is procedural (What was assessed? What was demonstrated?), it lives in the specialized tool.

### 20.6 Integration Patterns

**EA → GRC/ISMS/GDPR tool (push, regular)**:

- Asset inventory export in a defined format
- Structural changes as an event stream (see API chapter)
- Classification changes as a trigger for reassessment

**GRC/ISMS/GDPR tool → EA (pull or push)**:

- Risk scores as annotated properties
- Open findings as references (not as full data)
- Control framework references (as continuum artifact)
- Regulatory scope assignment (KRITIS, NIS2, DORA)

**Bidirectional synchronization**: For entities maintained in both worlds (e.g., protection need), the property mastership principle from [§17 (ITSM Integration)](../60-integrations/17-itsm-integration.md) applies. Property-level mastership configurable per entity type.

### 20.7 Exchange Formats

The tool should support several formats in order to serve different target systems:

**Generic formats**:
- OSCAL (Open Security Controls Assessment Language, NIST) – modern JSON/YAML format for compliance
- STIX/TAXII – for threat intelligence exchange (more ISMS focus)
- JSON/YAML-based custom formats via adapters

**Tool-specific formats**:
- Verinice: XML-based exchange format, open source – good reference adapter
- ServiceNow GRC: REST API
- OneTrust: REST API with CSV import for asset inventories

**GDPR-specific**:
- Export of the record of processing activities in the format of the respective supervisory authority (varies by country)
- GPDR-compliant data export for data subject access requests (structured)

### 20.8 Adapter Architecture

Analogous to ITSM/PPM integration, GRC/ISMS/GDPR adapters are implemented as **plug-in modules** (see API architecture). An adapter:

- Consumes the EA API for asset retrieval
- Translates into the target system format
- Maintains URN cross-references on both sides
- Reacts to events (new/changed assets → forwarding)
- Implements bidirectional property synchronization where configured

**Recommended reference adapters** (in priority order):

1. **Verinice adapter** – open-source ISMS, ideal as a first port of call for the community
2. **Generic CSV/JSON export** for asset inventories – allows manual integration into arbitrary tools
3. **OSCAL-based export** – future-proof, standardized
4. **ServiceNow GRC/IRM REST adapter** – high enterprise relevance

### 20.9 Property-Level Authorization

This integration forces a refinement of access control: not every EA user may see all attributes of all entities. Personal data categories and detailed protection needs are sensitive.

The tool therefore implements **property-level authorization as a mandatory feature**:

- A read and write permission can be defined per property
- Permissions are based on roles (e.g., "DPO", "ISO", "Architect")
- Properties without explicit restriction are visible to all readers
- API responses automatically filter out unauthorized properties (without rejecting the request)
- The audit log records who queried which protected properties and when

Example configuration:

```yaml
propertyAuthorization:
  - entity: DataEntity
    property: personalDataCategory
    read: [DPO, ISO, Architect]
    write: [DPO]
  - entity: DataEntity
    property: retentionPeriod
    read: [DPO, ISO, Architect, BusinessOwner]
    write: [DPO]
  - entity: ApplicationComponent
    property: confidentialityProtectionNeed
    read: [ISO, Architect, AppOwner]
    write: [ISO]
```

For implementation details see [§22 (API Architecture)](../70-platform/21-api-architecture.md).

### 20.10 Audit Trail Requirements

GRC, GDPR, and ISMS bring a hard compliance requirement: a **complete audit trail of data changes** is no longer nice-to-have but mandatory.

The EA repository logs for all changes:

- Who (user/role/API key)
- When (timestamp)
- What (entity/relation/property, old and new value)
- Why (optional: commit message, change reference)
- How (UI/API/sync)

The audit trail is:

- **Immutable** (append-only)
- **Exportable** in standardized formats (JSON, CSV)
- **Subject to retention** according to regulatory requirements (GDPR at least the storage duration of the processing, ISO 27001 typically 3+ years)
- **Itself access-protected** – audit logs contain sensitive data and must not be viewable by everyone

Technically: the event stream from the API chapter is supplemented with an audit flag, persisted permanently, and can be streamed to external SIEM systems (Splunk, Elastic).

### 20.11 Segregation of Duties

GRC principle: whoever designs controls must not approve them themselves. Whoever assesses risks must not accept them themselves. This affects EA workflows:

- **Standards draft vs. approval**: Architect proposes a standard, ARB approves (roles separated)
- **Waiver request vs. grant**: Application owner requests an exception, ISO or ARB grants it
- **Protection need collection vs. validation**: BusinessOwner maintains the protection need, ISO validates by sampling
- **Change vs. review for critical properties**: Four-eyes principle for changes to personal data classifications

The metamodel supports this through:

```yaml
Constraint: segregation-of-duties
  appliesTo: ArchitectureWaiver
  rule: "entity.requestedBy != entity.grantedBy"
  severity: error

Constraint: four-eyes-personal-data-changes
  appliesTo: DataEntity (property change: personalDataCategory)
  rule: "change requires approval by Role != author"
  severity: error
```

### 20.12 New Views

- **Record of processing activities (Art. 30 GDPR)**: automatically generated from DataEntity with isPersonalData=true, grouped by ProcessingPurpose, with all mandatory fields
- **Third-country transfer map**: visualizes DataTransfer relations across jurisdiction boundaries, color-coded by transfer mechanism
- **Protection need heatmap**: matrix of applications × CIA values, color-coded
- **Processor register**: all DataProcessor entities with DPA status, expiring contracts highlighted
- **Asset compliance coverage**: per application/technology, which regulatory scopes apply (KRITIS, NIS2, DORA, PCI-DSS) and whether they are formally captured in the ISMS/GRC
- **Risk hotspots**: applications with high risk scores from the ISMS, combined with high business criticality
- **Open findings drill-through**: findings from the GRC tool linked to the EA entity, shows remediation progress

### 20.13 Constraints

```yaml
constraint: personal-data-has-legal-basis
  appliesTo: DataEntity
  when: "entity.isPersonalData == true"
  rule: "entity.legalBasis != null AND entity.processingPurposes.length >= 1"
  severity: error
  message: "Personal data without a legal basis or purpose is not permitted"

constraint: personal-data-has-retention
  appliesTo: DataEntity
  when: "entity.isPersonalData == true"
  rule: "entity.retentionPeriod != null"
  severity: error
  message: "Personal data requires a defined retention period"

constraint: third-country-transfer-justified
  appliesTo: DataTransfer
  when: "entity.sourceLocation.region != entity.targetLocation.region AND entity.targetLocation.jurisdictionalRegion NOT IN adequacyDecisionRegions"
  rule: "entity.transferMechanism != 'none'"
  severity: error
  message: "Third-country transfer without a justification mechanism"

constraint: data-processor-has-dpa
  appliesTo: DataProcessor
  when: "entity.processesData.length >= 1"
  rule: "entity.dpaSignedDate != null AND entity.dpaExpiresDate > today()"
  severity: error
  message: "Processor without a valid DPA"

constraint: protection-need-reviewed-annually
  appliesTo: ProtectionNeed
  rule: "entity.nextReview >= today() - 1 year"
  severity: warning
  message: "Protection need should be reviewed annually"

constraint: high-protection-application-has-iso-controls
  appliesTo: ApplicationComponent
  when: "entity.protectionNeed.confidentiality in [high, very-high]"
  rule: "entity.assignedControls references ISO-27001-Annex-A-Controls"
  severity: warning
```

### 20.14 Practical Principles

- **EA delivers structural data, not assessments**. Risk assessments, control effectiveness, DPIA remain in the specialized tools.
- **No duplicate maintenance**. If a protection need is maintained in the ISMS, it is maintained there and synchronized into EA – or vice versa, but not both in parallel.
- **Audit trail from the start**. Retrofitting later is laborious and problematic from a compliance perspective.
- **Property-level authorization is a foundation**, not an additional feature. Personal data and protection needs are sensitive and must not be visible to all users.
- **Segregation of duties is not a workflow but a constraint**. Check at every critical action, not as an optional process.
- **Verinice as a reference adapter**. Open source, well documented, a valuable starting point for the community.

---

← [Agile Scaling](19-agile-scaling.md) · [🏠 Overview](../README.md) · [API Architecture & Modularity](../70-platform/21-api-architecture.md) →
