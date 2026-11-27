# Design

## Context

The BPMN package (changes 18 and 18b) brings pools, lanes and tasks, but no organizational units, roles or persons. `persons.entity_id` (V24) links an account manually to an element of the person metatype (`visibility_settings.person_metatype_id`) for connection-scoped visibility; the anchor of the scope is computed when it is read (`PersonStore.anchor`). Connections are entities, and catalogs already join over connections (`catalog/catalog-joins`) and filter on join columns (`catalog/catalog-filters`).

`saveDiagramView` (`DiagramViews.save`) creates entities and connections, but:

- each new entity needs a shape (`NEW_ENTITY_UNUSED`) and a metatype the viewpoint allows;
- each new connection takes its endpoints from its first edge (`NEW_CONNECTION_UNUSED`) and needs a connection type the viewpoint allows;
- nothing deletes a connection.

There is no entity-level read visibility in OEA: a holder of `entity:read` sees every entity that is not deleted; visibility restricts property values only (ADR-032).

## Goals / Non-Goals

**Goals:**

- the starter package `oea-starter-organization` and the organization model setting;
- the account mapping and the derived link;
- assignments of lanes, pools and tasks on the canvas, with their badges, chips and avatars;
- connections without an edge, entities without a shape, and removed connections in the save, all limited to assignments.

**Non-Goals:**

- the portal canvas (REQ-077 AC9), which comes with SCR-054;
- drawing assignments as edges;
- deriving responsibilities from the org unit hierarchy;
- an organization chart;
- the account attributes employee ID and external reference as mapping attributes: no operation writes `persons.employee_id` or `persons.external_ref_id` yet, so they follow with the provisioning that fills them;
- the package export (`exportMetamodelPackage` is still `planned`).

## Decisions

### D1 – A package of its own with source-open connection types

`packages/oea-starter-organization.json` 1.0.0 contains:

- **Element types:**
  - "Organizational Unit" (`OU`); its hierarchy is the entity hierarchy of the instance;
  - "Role" (`RO`);
  - "Person" (`PE`), with the string property "Account ID" (`maxLength` 255).
- **Connection types**, each with target constraints only:
  - "Belongs To", to "Organizational Unit";
  - "Performs Role", to "Role";
  - "Responsible", to "Person".
- **The section `organizationModel`** naming these six types (D2).
- **Labels** in en and de, and no viewpoint.

The package format stores endpoints as `connectionConstraints` entries with `direction` `source` or `target`. A connection type without a `source` entry has an empty source list, which allows every metatype (`connection-metatypes`; checked in `MetatypeRules`, `allowed.isEmpty()`). A package may name only its own or built-in metatypes, so an empty source list is the only way to reach lanes and TOGAF objects without a dependency between packages. No name or abbreviation collides with the TOGAF or the BPMN package.

*Alternative:* put the types into the BPMN package. Rejected by the owner, because the organization model also serves TOGAF objects.

### D2 – The organization model setting

The one-row table `organization_model` (V53) has a `version` and six nullable columns:

| Column | Part (API name) | References |
|---|---|---|
| `org_unit_metatype_id` | `orgUnit` | `metatypes`, an element metatype |
| `role_metatype_id` | `role` | `metatypes`, an element metatype |
| `person_metatype_id` | `person` | `metatypes`, an element metatype |
| `belongs_to_metatype_id` | `belongsTo` | `metatypes`, a connection metatype |
| `performs_role_metatype_id` | `performsRole` | `metatypes`, a connection metatype |
| `responsible_metatype_id` | `responsible` | `metatypes`, a connection metatype |

Every column has `ON DELETE SET NULL`.

**API.** `GET /metamodel/organization-model` needs `entity:read` or `metamodel:write` (`anyOfPermissions`). `PUT` needs `metamodel:write` and `If-Match` with the version of the row (428 without, 412 stale). In `import-only` it answers 403 `metamodel-locked`, as the architecture layers. Each part has its own 422 (`validation-failed`, naming the part):

- an unknown metatype or one of the wrong kind;
- a connection part whose allowed targets reach neither its element part (directly or through a base type) nor are empty, checked whenever both parts are set.

**Package section.** A package section `organizationModel` names a metatype of the package per part (422 naming `/organizationModel/<part>` for a metatype outside the package or of the wrong kind). The import fills only the parts that are null, audits them as `metamodel.organization-model-updated` with `cause: package-import`, and lists them as `organizationModelFilled` in the import result. A re-import never overwrites a set part.

*Alternative:* recognize the types by name. Rejected, because names are translated and can be changed.

**The person part is separate from the person metatype of visibility.** Merging them is not possible under SoD-2: the person metatype of visibility drives scope and belongs to `visibility:configure`, while the organization model belongs to `metamodel:write` and must work without a security administrator. SCR-030 shows a hint while both are set and differ; the account mapping (D5) works on the person metatype of visibility.

### D3 – Assignments in the view

`DiagramShape.assignments` carries `{ orgUnits[], roles[], persons[] }`. Each item is `{ connectionId, connectionVersion, entityId, name }`.

**Which shapes get them.** Shapes in an assignment lane or pool (D8), and shapes whose metatype the viewpoint maps to one of these notation keys of `notations/catalog.json`:

- `bpmn:Lane` or `bpmn:Pool`;
- `bpmn:Task`, `bpmn:UserTask`, `bpmn:ServiceTask`, `bpmn:SendTask`, `bpmn:ReceiveTask`, `bpmn:BusinessRuleTask` or `bpmn:ScriptTask`.

Which metatype a lane or pool is, is free in the viewpoint (owner decision 2026-11-27): any element metatype can be mapped to `bpmn:Lane` or `bpmn:Pool`, e.g. "Role" as lane and "Organizational Unit" as pool. The editor offers persons only on `bpmn:UserTask`, and organizational units only on lanes and pools.

**How they are read.** In one query per part, the outgoing connections of the part's connection metatype and its subtypes from the shapes' entities, whose target is of the part's element metatype or a subtype. Deleted connections and deleted targets are left out. A part that is unset gives no assignments. The names are entity names, which every holder of `entity:read` reads.

`getDiagramEntityDetails` returns the same assignments for the details panel.

### D4 – Saving assignments

**Additions to `saveDiagramView`, all limited to assignments:**

- **New connections without an edge.** A new connection that no edge names carries `sourceShapeId` (a shape of the request) and exactly one of `targetEntityId` and `targetNewEntityKey`. A new connection that an edge names carries none of them. Its metatype must be the belongs-to, performs-role or responsible part or a subtype, instead of a type the viewpoint allows (422 `METATYPE_NOT_ALLOWED`). The endpoint checks, the scoping 403 and the audit are those of every new connection.
- **Targets with a shape.** The target of a new connection without an edge may also be an entity, or a new entity, that a shape shows: the entity of an assignment lane (D8).
- **New entities without a shape.** A new entity that no shape shows must be the target of a new connection of the same request (`NEW_ENTITY_UNUSED` otherwise) and of the org unit, role or person part or a subtype, instead of a type the viewpoint allows (422 `METATYPE_NOT_ALLOWED`).
- **`removedConnections[]`**, at most 500 items `{ connectionId, expectedVersion }`. Each must be:
  - a connection that is not deleted (`CONNECTION_NOT_FOUND`);
  - of an assignment part or a subtype (`NOT_AN_ASSIGNMENT`);
  - from an entity of a shape of the request or of the stored layout, so that "Remove from diagram" can remove the assignment of the removed shape (`SOURCE_NOT_ON_DIAGRAM`);
  - not shown by an edge of the request (`REMOVED_CONNECTION_SHOWN`).

  Each is soft-deleted in the save's transaction through `EntityDeletions`, which brings the version check (409 `entity-version-conflict` as for `entityUpdates`), the open-issue block (409 `entity-blocked`), the scoping 403, the audit `entity.deleted` and the restore.

The 422 answers are `validation-failed` with issues `{ field, code }`, as every 422 of the save; the paths are `newConnections[i]`, `newEntities[i]`, `removedConnections[i]` and `shapes[i]`.

**Order of checks and writes:**

1. 400 for limits and decimals.
2. 403 for `entity:delete` when `removedConnections` is not empty, together with the 403 of `visibility-scope:assign`, before the version check and every 422 (`@ConditionsEnforcedByHandler`).
3. The existing order: 412/428 of the diagram, the 422 of the layout with the new checks above, the 409 of conflicts.
4. The hierarchy lock is taken first when `removedConnections` is not empty, as `EntityDeletions` takes it.
5. Writes: entity updates, new entities, parents, then the removals, then the new connections.
6. The org unit limit, then the derived links (D5), then the layout.

**Org unit limit** (owner decision 2026-11-27: tied to the shape drawn as lane or pool, checked in the diagram save only). For each lane or pool shape that is the source of a new belongs-to connection in this save, its entity must have at most one active belongs-to connection (of the part or a subtype) after the writes, else 422 with the code `ORG_UNIT_LIMIT` on `shapes[i]`, and everything is rolled back. Lanes are known only by the notation of a viewpoint, so the limit holds in the diagram save; a batch or entity write does not check it, and an existing second org unit blocks only a further one.

**Several persons.** BR-05 is a warning in the editor only.

### D5 – Account mapping and derived link

The table `person_link_mapping` (V53) is one row with `property_def_id` (nullable, references `property_definitions`) and `account_attribute` (`email` or `idp-subject`).

**Operations** under `/admin/property-visibility/person-link-mapping`, next to the person metatype on SCR-032 and like it without `If-Match`:

- **`GET`** needs `visibility:configure` or `person:manage`.
- **`PUT`** needs `visibility:configure`; a null property clears the mapping. 422 `validation-failed` naming `propertyDefId` when the property:
  - is unknown or not of data type `string`;
  - is not mapped to the person metatype of visibility or one of its subtypes;
  - does not have the visibility mode `role-restricted` (the write restriction).
- The audit is `visibility.person-link-mapping-changed` (category security).

**Keeping the restriction.** While a property is the mapping property, a visibility change that leaves `role-restricted` is rejected with 422 naming `mode`. Deleting the property definition, or removing its mapping from the person metatype, clears the mapping and its derived links in the same transaction, audited as above with `cause`.

**The derived link** is `persons.mapped_entity_id`, with a partial unique index like `entity_id`. Only human accounts in status `invited` or `active` take part (owner decision 2026-11-27). An account gets the derived link when, without a manual link:

- exactly one active element of the person metatype or a subtype, not linked manually to another account, has the mapping property value equal to the account's attribute (ignoring case for `email`; any of the account's identity bindings for `idp-subject`);
- and no other taking-part account has the same attribute value.

**When it is recomputed**, in the same transaction and under one transaction-scoped advisory lock of the mapping:

- every write that sets, changes or removes a value of the mapping property (entity and batch writes, diagram saves, solution go-live);
- the delete and restore of an element that carries a value, also by cascade;
- the creation or removal of an identity binding, and the status change of an account;
- the change of the mapping or of the person metatype.

Each changed derived link is audited as `person.account-linked` with `source: mapping` (the manual link gets `source: manual`).

**Responsibilities live in the metamodel** (owner decision 2026-11-27). Responsible, belongs-to and performs-role connections always point at entities, never at accounts. Deactivating, offboarding or unbinding an account never touches a person element or its connections; it only ends the derived link. The account is linked again when it becomes active.

**The effective link** is `coalesce(entity_id, mapped_entity_id)`. `PersonStore.anchor`, the scope evaluation of REQ-127, reads the effective link with its existing filter on active elements of the person metatype.

**Conflicts.** When the value matches several elements or several accounts, `mapped_entity_id` stays null. The person view shows `mappingConflict` with the element IDs and the number of accounts.

**Manual link.** A manual link takes precedence for its account. A manual link to an element that another account holds through the mapping is rejected with 409, as for any linked element.

**SoD-1.** A request (other than a login) whose recomputation gives the acting principal's own account a new or another derived link is rejected with 403, audited as `authz.denied` with reason `self-administration`, and nothing changes. This covers a write of the own value, a restore, a go-live and an identity binding added to the own account. Together with the write restriction, a writer cannot link their own account to an element that carries scope.

*Alternative:* match at query time, without storing the link. Rejected, because the conflict rule, the uniqueness of a link and the audit of each change need a stored state.

### D6 – Editor

**Lanes and pools.** A lane offers "Assign organizational unit" and "Assign role" in its context menu, and a pool "Set participant". Both open a search popover:

- It uses the existing `searchDiagramReferenceTargets`, extended with `part` (`org-unit`, `role` or `person`) as an alternative to `propertyDefId`: exactly one of both, else 422 naming `part`; an unset part gives 422 naming `part`.
- It offers "Create new" with the type choice of the part's metatype and its subtypes.

**What the canvas shows:**

- a badge with the org unit name in the label band of the lane or the pool header;
- role chips under the lane name, at most three and then "+N";
- a person avatar on a user task with the initials of the first two words and the full name as tooltip.

**Details panel.** A task has the field "Required role", and a user task also "Responsible person". Both allow several entries, and each has "×".

**Unsaved changes.** Assignments count as unsaved changes; replacing the org unit of a lane removes the old connection and adds the new one in one save. Without `entity:delete`, "×" and the replacement are not offered, and without `entity:write` no assignment entry is shown.

**Settings screens.** SCR-030 gets the section "Organization model" with the hint of D2. SCR-032 gets the account mapping next to the person metatype. The person administration shows "linked by mapping" and the conflict.

### D8 – Assignment lanes and pools

Owner decision 2026-11-27: a lane = an assignment. A shape drawn as `bpmn:Lane` or `bpmn:Pool` whose entity is of the org-unit or role part or a subtype is an **assignment lane** (or pool). For it, the editor replaces the entity parent by an assignment:

- **Into it.** A shape placed or moved into an assignment lane does not take the lane's entity as parent. The editor sends a new connection without an edge from the shape's entity to the lane's entity: belongs-to for an org unit, performs-role for a role, unless the view already lists it. The parent is the entity of the nearest enclosing container that is no assignment lane, for a new element, and stays unchanged for a moved one; "New parent?" and hierarchy lines do not apply to the assignment lane itself.
- **Nested.** A shape in a role lane of an org unit pool gets both connections; a role lane in an org unit pool gets the belongs-to connection itself.
- **Out of it.** Moving the shape out, or "Remove from diagram", sends the connection in `removedConnections`. Without `entity:delete` the editor does not offer these moves, and the save answers 403. An assignment made by containment has no "×"; it is removed by moving the shape out.
- **Own part.** An assignment lane does not offer "Assign …" for its own part; a role lane still offers "Assign organizational unit".
- **A lane of another metatype** (e.g. the BPMN "Lane") behaves as before: it becomes the parent.
- **Only on placing and moving.** Shapes that already lie in a lane when it becomes an assignment lane (a change of the viewpoint or the organization model) get no connection until they are moved.

The server checks the connections and removals as in D4; it does not derive them from the nesting, because the connection is a repository fact that other diagrams share.

### D7 – Security

| Operation | Permission | Audit |
|---|---|---|
| organization model | `metamodel:write`, not in `import-only` (read: `entity:read` or `metamodel:write`) | `metamodel.organization-model-updated` with the changed parts |
| account mapping | `visibility:configure` (read: also `person:manage`) | `visibility.person-link-mapping-changed` |
| derived link | – (SoD-1 403 for the own account) | `person.account-linked` with `source: mapping` |
| assignments | `entity:write`; a scoping type also `visibility-scope:assign` | as connections |
| removals, also by moving out of an assignment lane | `entity:delete` | `entity.deleted` |

**Input limits:** `removedConnections` 500, `newConnections` and `newEntities` 500 as before, names 255 and `q` 255 characters (400 above), the "Account ID" value 255 characters.

## Risks / Trade-offs

- **Recomputing the derived link** touches identity and entity writes. The recomputation is local to the changed values, elements or accounts, serialized by one advisory lock, and an integration test covers each trigger.
- **The person part and the visibility person metatype can differ.** This is required by SoD-2, and SCR-030 shows a hint.
- **Write roles of the mapping property** decide who can link accounts to elements. A service account with such a role writes values for others; SoD-1 cannot see the human behind its token. The security administrator chooses the write roles.
- **The org unit limit** holds only in the diagram save; a batch import can create a second org unit of a lane.

## Migration Plan

V53 is additive. The org model row starts empty, and the mapping is unset. Existing manual links are unchanged.

## Open Questions

- none
