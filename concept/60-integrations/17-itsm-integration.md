## 17. ITSM Integration (CMDB and Service Catalog)

The EA repository does not exist in a vacuum. In every enterprise environment it coexists with ITSM tools (ServiceNow, BMC Helix, Ivanti, Jira Service Management, or similar) that maintain a **CMDB** and a **service catalog**. Clean delimitation is critical to success, because otherwise duplicate maintenance, contradictory data, and loss of trust threaten.

### 17.1 Basic Division of Roles

The EA repository and the CMDB address different questions:

| EA repository answers | CMDB/ITSM answers |
|---|---|
| What should we have? | What do we currently have? |
| Why do we have it? | How is it running right now? |
| When do we change it? (planning) | When do we change it? (execution) |
| Who uses it from a business perspective? | Who supports it operationally? |
| What does 2028 look like? | Which version is running on node-42? |

EA thinks in **plateaus and target states** (months to years), the CMDB in **configuration states** (seconds to weeks).

### 17.2 Entity-Related Mastership

The question "who is master?" is decided **per EntityType and partly per property**:

**EA is master for:**
- All logical entities (LogicalApplication/-Technology/-Data)
- Business architecture entities (Capability, ValueStream, BusinessService, BusinessFunction, Process, OrgUnit, InformationObject, InformationFlow)
- Temporal and strategic entities (Plateau, Goal, Driver, WorkPackage, Gap, ADR, Principle, Standard)
- Planning-relevant properties on physical entities (`lifecycleState` per plateau, `strategicClassification`, `targetArchitecture` membership)

**CMDB/ITSM is master for:**
- Concrete runtime CIs (servers, VMs, containers, network devices, storage)
- Software installations incl. patch level, concrete versions, hostnames, IPs
- Deployment instances (DEV/TEST/PROD)
- Operational properties (status, uptime, metrics, incident/change history, SLA binding)

**Jointly maintained** (with property-level mastership):
- `PhysicalApplicationComponent`: EA master for existence, business assignment, strategic assessment; CMDB master for current version, deployment instances, operational status
- `PhysicalTechnologyComponent`: EA master for standards, target state, lifecycle planning; CMDB master for patch level, configuration, instances
- `Interface`: EA master for business semantics, information flow assignment, planned interfaces; CMDB master for active endpoints, monitoring status

### 17.3 Services – the Special Intersection

"Service" is hopelessly overloaded in EA and ITSM. The clean assignment:

| EA concept | ITSM concept | Mastership |
|---|---|---|
| BusinessService | Business Service (service catalog entry) | EA: business definition, realization, capability assignment. ITSM: ordering path, SLA, pricing, service owner (operational) |
| ApplicationService | Technical Service (if in the catalog) | EA: functional definition, realization chain. ITSM: technical SLA, support level |
| PlatformService | Technical/Infrastructure Service | EA: technology standard, target state. ITSM: operational responsibility, SLA |

**Structural asymmetry**: EA structures services by realization chains (business → application → platform), ITSM by dependency chains (customer-facing → supporting → infrastructure). Both views are legitimate and should be allowed to exist in parallel.

**Governance rule**: If an EA service is intended for external consumers, a service catalog entry *should* exist. Conversely, every service catalog entry with business functionality *should* reference at least one EA service. Not every EA service needs a catalog entry (e.g., internal technical interfaces).

### 17.4 Cross-Reference Mechanism

Stable identifiers are the basis of the integration:

```
EA URN scheme:    urn:ea:<org>:<type>:<id>
                  e.g. urn:ea:acme:application:sap-fi
                       urn:ea:acme:bizservice:invoicing

CMDB reference:   cmdb-ci-id: CI0042-SAP-FI-PROD
```

Both sides carry the respective other ID as a property:
- The EA entity has a property `externalReferences[]` with CMDB CI IDs
- The CMDB CI has a property `eaReference` with the EA URN

This yields bidirectional navigability without hard coupling.

### 17.5 Synchronization Patterns

**CMDB → EA (discovery direction)**: New CIs that appear in the CMDB through automatic discovery are created as stub entities in the EA. Shadow IT becomes visible.

**EA → CMDB (planning direction)**: Newly planned applications/technologies are pushed into the CMDB as "ordered" CIs so that operations and service management can prepare.

**Bidirectional with property mastership**: For jointly maintained entities, the connector synchronizes only the properties whose master is the other side. The own master area remains unchanged.

### 17.6 Extension of the Meta-Metamodel

The meta-metamodel is supplemented with the following concepts:

```yaml
EntityType gains optional properties:
  - externalReferences: ExternalReference[]

ExternalReference:
  - system: enum[servicenow, bmc-helix, ivanti, jira-sm, custom]
  - externalId: string
  - syncDirection: enum[read, write, bidirectional]
  - lastSynced: datetime
  - mastership: enum[ea, external, shared-per-property]

Property (M2 level) gains:
  - masterSystem: enum[ea, external]  # for shared-per-property
```

### 17.7 ITIL 4 Practices and Docking Points

| ITIL 4 practice | Docking point in EA |
|---|---|
| Service Configuration Management | Map CI types to EntityTypes; EA URN as external reference on the CI |
| Service Catalog Management | `publishedAs` relation from EA service to catalog entry |
| Change Enablement | Major changes are checked against EA standards and target states (Architecture Review Board) |
| Service Level Management | EA maintains the strategic criticality class, ITSM derives the SLA from it |
| Incident/Problem Management | Aggregated statistics can flow into EA as assessment input ("technical debt") |

### 17.8 Practical Principles

- **Only what is architecture-relevant goes into EA**. Not every VM, every switch, every patch instance needs to be modeled. Rule of thumb: *Will an architecture decision ever be made about it?*
- **Make property mastership explicit**. If both sides maintain the same property without a defined master, the data drifts.
- **Roles in the metamodel**: Model EA owner, data steward, CI owner as relations – in case of conflicts the system knows whom to notify.
- **Do not enforce retroactive cleanup**: Grown service catalogs are not bent into shape. Design new services jointly, migrate old ones over time.

### 9.9 View Proposals

- **Service Catalog Alignment**: shows EA services without a catalog entry, catalog entries without an EA service, services with reference drift
- **CMDB Coverage**: shows EA applications/technologies without a linked CI and vice versa
- **Mastership conflicts**: properties for which EA and CMDB have different values

---

← [Example Walkthrough](../50-walkthrough/16-example-walkthrough.md) · [🏠 Overview](../README.md) · [PPM Integration](18-ppm-integration.md) →
