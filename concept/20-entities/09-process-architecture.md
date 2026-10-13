## 9. Process Architecture

Process modeling in the EA context is **not** process authoring. Specialized BPM tools (Camunda, Signavio, BIC, ARIS) exist for that. This tool models processes **abstractly and notation-neutrally**, with BPMN and other notations as import/export formats.

### 9.1 Modeling Philosophy

**The metamodel knows abstract process concepts**, not BPMN specifics. Advantages:
- Portability to BPMN, EPC, value stream maps, future standards
- No coupling to BPMN version changes
- Focus on architecturally relevant properties (ownership, cross-references, KPIs)
- Detailed process modeling remains in the business BPM tool

**Bidirectional integration with BPM tools**: Process models are imported from external BPM tools, referenced in the repository, and linked with EA entities. Changes to the process flow happen in the BPM tool, architectural links in OEA.

### 9.2 Process Hierarchy (Process Landscape)

The process landscape structures all processes of an organization hierarchically:

```yaml
EntityType: ValueChain
  properties:
    - name, description
    - type: core | supporting | management  # per Porter
    - industry: string                       # e.g. "retail", "banking"
  relations:
    - contains: ProcessGroup[]
    - supports: Capability[]

EntityType: ProcessGroup (L2 / L3)
  properties:
    - name, description
    - level: integer                         # hierarchy level (2–5)
    - pcfReference: string                   # e.g. APQC PCF code
  relations:
    - parentGroup: ProcessGroup
    - contains: ProcessGroup[] | Process[]

EntityType: Process (L4)
  properties:
    - name, description
    - type: core | supporting | management
    - automationLevel: manual | partially-automated | fully-automated
    - triggers: text                         # what starts the process
    - outcomes: text                         # what results at the end
    - frequency: string                      # e.g. "monthly", "on-demand"
    - complexity: low | medium | high
  relations:
    - belongsTo: ProcessGroup
    - decomposedInto: Activity[]
    - ownedBy: Role (Process Owner!)
    - managedBy: Role (Process Manager)
    - realizes: Capability[]
    - usedBy: BusinessFunction[]
    - measuredBy: KPI[]
    - externalDefinition: reference           # link to BPMN in BPM tool

EntityType: Activity (L5)
  properties:
    - name, description
    - type: task | decision | event | subprocess
    - automationPotential: low | medium | high
  relations:
    - belongsTo: Process
    - performedBy: Role[]
    - supportedByApplication: ApplicationService[]
    - readsData: DataEntity[]
    - writesData: DataEntity[]
    - triggers: InformationFlow[]
```

### 9.3 Process Classification Frameworks (PCF)

Process reference models are modeled as continuum artifacts from [§4 (Enterprise Continuum)](../10-foundations/04-enterprise-continuum-trm.md):

```yaml
ReferenceArchitecture "APQC-PCF-v7.4"
  continuumLevel: industry
  contains: ProcessGroup-Blueprints (all APQC categories)

ReferenceArchitecture "eTOM-v23"
  continuumLevel: industry
  industry: telco
  contains: ProcessGroup-Blueprints (telco-specific)
```

Organizations can map their own processes against these reference taxonomies – for benchmarking and structural consistency:

```yaml
Process "Invoicing" gains:
  - mapsToPCF: "APQC 9.2.1 - Invoice Customer"
```

### 9.4 Process Performance (PPIs / KPIs)

Metrics as a separate entity:

```yaml
EntityType: KPI
  properties:
    - name, description
    - formula: text
    - unit: string
    - targetValue, currentValue: number
    - measurementFrequency: string
    - trend: improving | stable | declining
    - direction: higher-better | lower-better | target-best
  relations:
    - measures: Process | Capability | BusinessService | Goal
    - owner: Role
    - dataSource: ApplicationComponent
```

Typical process KPIs:
- Cycle time, lead time
- Error rate, first-time-right rate
- Cost per process instance
- Customer satisfaction (NPS, CES)

### 9.5 Cross-Layer Links (CRUD Matrix)

The classic EA analysis: Which processes do what with which data?

```yaml
Relation: ProcessDataAccess
  - process: Process | Activity
  - dataEntity: DataEntity
  - accessType: create | read | update | delete
  - frequency: string
```

From this, a **CRUD matrix** can be generated automatically: processes × data objects with C/R/U/D marking. It shows, among other things, **data sovereignty** (who creates a DataEntity?), **read-only processes**, and **potential conflicts** (two processes that independently update the same DataEntity).

### 9.6 BPMN Integration

BPMN is integrated via an adapter:

**Import**: BPMN XML files are parsed, elements (Pool, Lane, Task, Flow, DataObject, MessageFlow) are mapped to abstract process entities. The BPMN file is referenced as externalDefinition, not duplicated.

**Export**: BPMN XML can be generated from the abstract process model (bidirectional, with BPMN-specific details such as gateway conditions remaining maintained in the BPM tool).

**Annotation**: BPMN elements are annotated with EA references (see [§6.3 (Core Entity Types)](06-core-entity-types.md) BPMN integration table).

```yaml
EntityType: ProcessModel (separate entity)
  properties:
    - notation: BPMN-2.0 | EPK | VSM | custom
    - format: XML | JSON | proprietary
    - sourceFile: path | url
    - lastImported: datetime
  relations:
    - representsProcess: Process
    - maintainedIn: ExternalTool     # e.g. "Camunda Modeler"
```

### 9.7 Value Stream Mapping

For product-oriented organizations (Lean, SAFe), the **value stream** is often the central view – orthogonal to the process hierarchy:

```yaml
EntityType: ValueStream
  properties:
    - name, description
    - triggerEvent: text             # what triggers the value stream
    - valueProposition: text         # which customer benefit
    - totalLeadTime: duration
    - totalProcessTime: duration
  relations:
    - steps: ValueStreamStep[]       # sequential steps
    - deliversToCustomer: Stakeholder
    - supportedBy: Process[]         # processes that implement parts
    - realizes: Capability[]

EntityType: ValueStreamStep
  properties:
    - name, description
    - sequence: integer
    - processTime, leadTime: duration
    - valueAdding: boolean           # VALUE-ADDING yes/no
  relations:
    - performedIn: Process | OrganizationUnit
    - handsOffTo: ValueStreamStep
```

**Important analysis**: Waste identification – non-value-adding steps (`valueAdding: false`) are optimization candidates. The classic Lean view brought into OEA.

### 9.8 Process Ownership (deepened from [§8 (Organization & Roles)](08-organization-roles-persons.md))

Process ownership is multi-layered:

**Process Owner** (strategic):
- Responsible for the process across its lifecycle
- Decides on process design and changes
- Typical: line management

**Process Manager** (operational):
- Steers ongoing process execution
- Responsible for achieving KPIs
- Typical: team leader

**Process Architect** (methodological):
- Standardization, cross-process consistency
- Part of the EA/BPM function

**Process Participant**:
- Performs activities
- Internal or external (customer, partner)

Model all four as relations on the process, with their own roles:

```yaml
Process gains:
  - processOwner: Role
  - processManager: Role
  - processArchitect: Role
  - participants: Role[]
```

### 9.9 Recommended Tool Scope for Process Modeling

The tool should add value at the following level:

**What the tool should do**:
- Maintain the process hierarchy (L1–L5)
- Cross-layer links (Process ↔ Capability, Application, Data, Role)
- Generate the CRUD matrix
- KPI assignment and tracking
- PCF mapping for benchmarking
- BPMN import/export via adapter
- Process ownership and RACI

**What the tool should NOT do**:
- BPMN diagram editor with swimlanes, gateways, event subprocesses
- Process simulation / execution engine
- Form generation from processes
- Workflow execution

Those who need full process authoring use Camunda/Signavio in parallel and link.

### 9.10 Constraints

```yaml
constraint: process-has-owner
  appliesTo: Process
  rule: "entity.processOwner != null"
  severity: error
  message: "A process without a process owner is not governable"

constraint: process-has-kpi
  appliesTo: Process
  when: "entity.type == 'core'"
  rule: "entity.measuredBy.length >= 1"
  severity: warning
  message: "Core process without KPIs is not measurable"

constraint: activity-has-performer
  appliesTo: Activity
  rule: "entity.performedBy.length >= 1 OR entity.supportedByApplication.length >= 1"
  severity: warning

constraint: data-write-has-owner
  appliesTo: ProcessDataAccess
  when: "entity.accessType == 'create' OR entity.accessType == 'update'"
  rule: "process.processOwner has dataOwnership on dataEntity OR explicit delegation"
  severity: warning
  message: "Process writes data without clear data sovereignty"
```

---

← [Organization, Roles & Persons](08-organization-roles-persons.md) · [🏠 Overview](../README.md) · [Cross-Cutting](10-cross-cutting.md) →
