# Proposal

## Why

BPMN process diagrams (changes 18 and 18b) show the flow of a process, but not who performs it. US-075 and US-076 assign lanes, pools and user tasks to organizational units, roles and persons (REQ-077). No entity type for these exists yet, neither in the TOGAF nor in the BPMN starter package. This is S17 change 19 ([sprint plan](../../../docs/sprint-plan.md)).

## Traceability

| Artifact | Reference |
|---|---|
| User stories | [US-075](../../../requirements/user-stories/US-075-assign-lane-orgunit-and-role.md), [US-076](../../../requirements/user-stories/US-076-assign-task-to-person.md) |
| Requirement | [REQ-077](../../../requirements/req/REQ-077-role-orgunit-person-process-assignment.md) AC1–AC8; [REQ-127](../../../requirements/req/REQ-127-property-visibility-enforcement-connection-scoped.md) (person link) |
| Use case | UC-10 |
| ADRs | [ADR-007](../../../adrs/ADR-007-canvas-library.md), [ADR-013](../../../adrs/ADR-013-api-style.md), [ADR-032](../../../adrs/ADR-032-authorization-model.md) |
| Screen | SCR-021 "Diagram editor", SCR-030 "Metamodel", SCR-032 "Property visibility", the person administration |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Owner decisions (2026-11-27):**

- **An organization model of its own.** A starter package `oea-starter-organization` brings the entity types "Organizational Unit", "Role" and "Person". It is used in BPMN (lanes as roles, for example) and to name who is responsible for a TOGAF object.
- **Responsibility for objects names persons only.** Any object, a TOGAF object for example, can name responsible persons, not roles or organizational units.
- **Responsibilities are established through entities of the metamodel**, never through accounts, so they stay when an employee is deactivated in the IdP.
- **A person is an entity** of the repository. It can be linked to an OEA account, also through a configurable mapping, for example an IdP ID that arrives with an import.
- **One change.** The package, the account mapping and the assignments on the canvas come together.
- **The portal comes later.** The badges in the read-only portal canvas (REQ-077 AC9) follow with the portal diagram view (SCR-054).

**Decisions and assumptions of this change (open for review):**

- **Three connection types, open on the source side.** The package brings:
  - "Belongs To", from any element to an "Organizational Unit";
  - "Performs Role", from any element to a "Role";
  - "Responsible", from any element to a "Person".

  An empty source list allows every metatype, so the package needs neither the BPMN nor the TOGAF package.
- **The organization model setting.** The instance keeps which metatypes and connection types play the parts organizational unit, role, person, belongs-to, performs-role and responsible.
  - The package fills the unset parts on import.
  - A metamodel administrator can change them, so that an instance can use its own types.
  - The canvas offers assignments only for the parts that are set.
- **Assignments on the canvas:**
  - A lane and a pool have the context menu entries "Assign organizational unit" and "Assign role"; a pool's entry is labelled "Set participant".
  - A task has the fields "Required role" in the details panel, and a user task also "Responsible person".
  - An assignment is a connection without an edge. It is saved with "Save" in `saveDiagramView`, and removing it deletes the connection softly.
  - Lanes, pools and tasks are the shapes whose metatype the viewpoint maps to `bpmn:Lane`, `bpmn:Pool`, `bpmn:Task` or a task variant; any element metatype can be a lane or a pool (owner decision 2026-11-27).
  - **Lane = assignment** (owner decision 2026-11-27): a shape in a lane or pool whose entity is an organizational unit or a role gets a belongs-to or performs-role connection to it instead of it as parent; moving it out removes the connection and needs `entity:delete`.
- **Limits:**
  - A lane or a pool belongs to one organizational unit at most (BR-03); a diagram save that adds a second answers 422 `ORG_UNIT_LIMIT`; batch writes do not check it (owner decision 2026-11-27).
  - Several responsible persons on a user task only warn (BR-05).
- **Quick create.** The assignment search offers "Create new", which creates an organizational unit, role or person in the same save, without a shape.
- **Account mapping.** A security administrator sets, on SCR-032 next to the person metatype, a string property of the person metatype and an account attribute: email or IdP subject. Employee ID and external reference follow with the provisioning that fills them.
  - Accounts in status `invited` or `active` take part (owner decision 2026-11-27).
  - An account whose attribute equals the property value of exactly one active person element is linked to it.
  - A manual link takes precedence.
  - A value that matches several elements or several accounts gives no link; this is shown as a conflict.
  - The mapping property must be role-restricted, and a request that would link the acting principal's own account is rejected with 403 (SoD-1).

**Not in this change:**

- the portal canvas (AC9, with SCR-054);
- drawing assignments as edges;
- deriving responsibilities from the org unit hierarchy.

## Capabilities

### New Capabilities

- `metamodel/organization-model`: the organization model setting and the package section `organizationModel`.
- `diagram/bpmn-assignments`: assignments of lanes, pools and tasks on the canvas, with their badges.

### Modified Capabilities

- `metamodel/starter-packages`: the shipped starter package `oea-starter-organization`.
- `identity/person-entity-link`: the configurable account mapping next to the manual link.
- `diagram/bpmn-process-diagrams`: no entity parent from an assignment lane or pool.
- `diagram/diagram-connections`: assignment connections without an edge, entities without a shape and removed connections in the save.

## Impact

- **Contract:**
  - `getOrganizationModel` and `updateOrganizationModel` under `/metamodel/organization-model`;
  - `getPersonLinkMapping` and `updatePersonLinkMapping` under `/admin/property-visibility/person-link-mapping`;
  - `assignments` on `DiagramShape` and in `getDiagramEntityDetails`;
  - new connections without an edge, new entities without a shape and `removedConnections` in `saveDiagramView`;
  - `part` in `searchDiagramReferenceTargets`;
  - the package section `organizationModel` and `organizationModelFilled` in the import result.
- **Database:** V53 – the table `organization_model`, the columns of the account mapping and the derived link.
- **Backend:** the package section `organizationModel`, the derived account link with its recomputation and the SoD-1 check, the assignments of the view and the checks of the save.
- **Frontend:**
  - the context menu of lanes and pools, with the search and "Create new";
  - the badges, chips and avatars;
  - the fields of the details panel;
  - the setting on SCR-030, the mapping on SCR-032, and "linked by mapping" and the conflict in the person administration.
- **Documentation:**
  - the manual pages "Diagrams", "Metamodel packages" and "Persons" (en/de);
  - SCREENS.md;
  - the business objects process, person and metamodel configuration, and `docs/data-model.puml`;
  - the audit catalog;
  - REQ-077, US-075, US-076 and UC-10.
