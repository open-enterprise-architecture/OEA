# Tasks

## 1. Contract

- [x] 1.1 `api/openapi.yaml`:
  - `getOrganizationModel` (`anyOfPermissions` `entity:read`, `metamodel:write`) and `updateOrganizationModel` (`metamodel:write`, `IfMatchRequiredHeader`, 403 `metamodel-locked`) under `/metamodel/organization-model`;
  - `getPersonLinkMapping` (`anyOfPermissions` `visibility:configure`, `person:manage`) and `updatePersonLinkMapping` (`visibility:configure`) under `/admin/property-visibility/person-link-mapping`, `accountAttribute` enum `email`, `idp-subject`;
  - `DiagramShape.assignments` and the same on `DiagramEntityDetails`, each item with `connectionId`, `connectionVersion`, `entityId` and `name`;
  - in `saveDiagramView`: `sourceShapeId`, `targetEntityId` and `targetNewEntityKey` on `DiagramNewConnection`, the descriptions of new entities and connections without a shape or an edge, `removedConnections` (maxItems 500, items `connectionId` and `expectedVersion`), and the conditional 403 for `entity:delete` (`@ConditionsEnforcedByHandler`);
  - `part` (`org-unit`, `role`, `person`) in `searchDiagramReferenceTargets`, `propertyDefId` no longer required;
  - `mappedEntityId`, `linkSource` and `mappingConflict` on `Person`;
  - the package section `organizationModel` and `organizationModelFilled` in the import result;
  - the issue codes `ORG_UNIT_LIMIT`, `NOT_AN_ASSIGNMENT`, `SOURCE_NOT_ON_DIAGRAM` and `REMOVED_CONNECTION_SHOWN` in the descriptions of the 422 `validation-failed`.

  Verification: the Redocly lint, `check_openapi_authorization.py`, `check_openapi_conventions.py` and their tests.

## 2. Database and package

- [x] 2.1 V53:
  - `organization_model`, one row with a version and six nullable FKs to `metatypes` with `ON DELETE SET NULL`;
  - `person_link_mapping`, one row, `property_def_id` and `account_attribute` with a CHECK;
  - `persons.mapped_entity_id` referencing `entities`, with a partial unique index;
  - grants as for `visibility_settings`.

  Verification: the migration test.
- [x] 2.2 `packages/oea-starter-organization.json` 1.0.0 (D1), with target constraints only, the section `organizationModel` and en/de labels. Register it as a starter package.

  Verification: `OrganizationStarterPackageTests`, on a database of its own:
  - import alone, and a re-import is unchanged;
  - import next to TOGAF and BPMN;
  - a lane and an Application Component as source of "Belongs To" and "Responsible" are accepted;
  - "Responsible" to a role gives 422, to a person is accepted;
  - "Account ID" with 255 characters is accepted, 256 give 422.
- [x] 2.3 The package import of `organizationModel`: fill only unset parts, list them in the result, audit them.

  Verification: an empty setting is filled, a set part is kept, a metatype outside the package or of the wrong kind gives 422 naming the part, and nothing is stored.

## 3. Backend

- [x] 3.1 The organization model store and endpoints (D2), with the audit `metamodel.organization-model-updated`.

  Verification:
  - each part with a valid metatype is accepted, with the wrong kind gives 422 naming the part;
  - a connection part whose targets exclude the element part gives 422, one whose target is a base type or whose target list is empty is accepted;
  - 403 without `metamodel:write`, and read access with `entity:read`;
  - 412 for a stale `If-Match`, 428 without it;
  - 403 `metamodel-locked` in `import-only`;
  - the part becomes null after the metatype is deleted.
- [x] 3.2 The account mapping and the derived link (D5).

  Verification, each with a positive and a negative case:
  - a role-restricted string property of the person metatype is accepted; 422 for a non-string property, for a property not mapped to the person metatype, and for a public or connection-scoped one;
  - 403 without `visibility:configure`, read access with `person:manage`;
  - the visibility change of the mapping property to `public` gives 422 naming `mode`; deleting the property clears the mapping and its links;
  - the link through a batch import, a diagram save and a go-live;
  - a manual link takes precedence, and a manual link to an element held by the mapping gives 409;
  - a conflict with two elements and with two accounts;
  - delete and restore of the element, also by cascade;
  - `email` matched ignoring case, `idp-subject` through an added identity binding;
  - clearing the mapping and changing the person metatype;
  - a service account stays unlinked, an invited account is linked;
  - deactivating, offboarding or unbinding an account keeps the person element and its responsible connections and ends the derived link, and reactivating links again;
  - the own value gives 403 and `authz.denied`, the value of another account is accepted; the own identity binding gives 403; the login stays possible;
  - scope through the derived link, as in the REQ-127 tests;
  - the audit `person.account-linked` with `source: mapping`, and `source: manual` for the manual link.
- [x] 3.3 The assignments in `getDiagramView` and `getDiagramEntityDetails` (D3), by the notation keys `bpmn:Lane`, `bpmn:Pool`, `bpmn:Task` and the task variants.

  Verification:
  - a lane with an org unit and two roles;
  - a user task with a person;
  - a deleted connection and a deleted target are left out, and shown again after a restore;
  - an unset part gives no assignments;
  - a gateway and a shape that is not BPMN get no assignments.
- [x] 3.4 `saveDiagramView` (D4, D8): new connections without an edge, new entities without a shape, `removedConnections`, and the org unit limit, in the order of D4.

  Verification:
  - `removedConnections` with 500 is accepted, with 501 gives 400;
  - 403 without `entity:delete`, before a 422 of the same request; accepted with it;
  - a new connection without an edge of an assignment part is accepted, of another type gives 422; one with an edge and its own endpoints gives 422;
  - a new entity without a shape named by a new connection is accepted, one named by nothing or of another type gives 422;
  - a removed connection whose source is only on a shape of the stored layout (removed from the diagram) is accepted; one whose source is on neither, that is no assignment, or that an edge of the request shows gives 422; a stale `expectedVersion` gives 409 `entity-version-conflict`;
  - an org unit limit of one is accepted, two give 422 `ORG_UNIT_LIMIT` naming the shape, and replacing the org unit in one save is accepted; a lane with two org units from a batch import does not block a save that adds none; the limit applies to a "Role" entity drawn as `bpmn:Lane`, and not to a task;
  - a new connection without an edge to the entity of a lane shape, also a new one, is accepted (D8);
  - a removed connection can be restored, and is audited as `entity.deleted`;
  - a name of 255 characters is accepted for a quick-created entity, 256 give 400.
- [x] 3.5 `searchDiagramReferenceTargets` with `part`: the active entities of the part's metatype and its subtypes.

  Verification: a set part lists them; an unset part gives 422 naming `part`; `propertyDefId` together with `part`, or neither, gives 422; `q` of 255 characters is accepted, 256 give 400.
- [x] 3.6 Cucumber `bpmn-assignments.feature`, on an instance of its own: AC1 to AC8 of REQ-077 through the API, including the catalog joins of AC6 (with the filter) and AC7 (the empty cell), and a viewpoint mapping "Role" to `bpmn:Lane` and "Organizational Unit" to `bpmn:Pool`: a task saved in a role lane has the performs-role connection and an unchanged parent.

## 4. Frontend

- [x] 4.1 The lane and pool context menu with the search popover and "Create new"; the fields "Required role" and "Responsible person" in the details panel; "×" to remove; the warning "More than one responsible person"; the unsaved-changes state.

  Verification: unit specs of the canvas model for adding, removing and replacing, and the save payload (`removedConnections` with versions, new connections without an edge, new entities without a shape).
- [x] 4.2 The badge, the chips (3 shown without "+N", 4 shown as 3 and "+1") and the avatar with its initials and tooltip; the entries hidden without `entity:write` or `entity:delete`.

  Verification:
  - unit specs for the initials (one word, two words, three words) and the chips at 3 and 4 roles;
  - the stories `LaneAssignments` and `UserTaskPerson`, each with a real-pointer play step;
  - a read-only story.
- [x] 4.3 The section "Organization model" on SCR-030, with the hint while the person differs from the visibility person metatype; the account mapping on SCR-032; "linked by mapping" and the conflict in the person administration.

  Verification: specs and stories.

  Deviation: the repository has no screen of the person administration (persons are managed through the API, see the manual page "Identity providers and persons"), so "linked by mapping" and the conflict are shown by `getPerson` and `listPersons` (`mappedEntityId`, `linkSource`, `mappingConflict`) and documented in the manual, not on a screen.

- [x] 4.4 Assignment lanes and pools (D8): a shape placed or moved into a lane or pool of the org-unit or role part gets the connection instead of the parent, the parent from the nearest other container, no "New parent?" and no hierarchy line for it; moving out or "Remove from diagram" removes the connection; without `entity:delete` these moves are not offered; no "Assign …" for the lane's own part; a lane of another metatype keeps the parent behavior.

  Verification:
  - unit specs of the canvas model: a task dropped into a role lane (performs-role, parent unchanged), into a role lane of an org unit pool (both connections), moved between two role lanes (one removed, one added), moved out without `entity:delete` (refused), into a BPMN "Lane" (parent as before), an existing connection not sent twice;
  - the story `RoleLanes`, with a viewpoint mapping "Role" to `bpmn:Lane` and "Organizational Unit" to `bpmn:Pool`, with a real-pointer play step that drags a task from one role lane to another, and its read-only variant.

## 5. Documentation

- [x] 5.1 The manual pages "Diagrams", "Metamodel packages", "Property visibility" and the person administration, in en and de.
- [x] 5.2 The documents to update:
  - the business objects process, person and metamodel configuration, and `docs/data-model.puml`;
  - the audit catalog (`metamodel.organization-model-updated`, `visibility.person-link-mapping-changed`, `source` of `person.account-linked`);
  - SCREENS.md, for SCR-021, SCR-030 and SCR-032;
  - REQ-077, US-075, US-076 and UC-10, with the status and the realigned connection names (one "Belongs To" for lanes and pools).

  Then run `generate_traceability.py`.

## 6. Checks

- [x] 6.1 All local checks:
  - the backend verify;
  - in the frontend: unit tests, type check and story tests;
  - `openspec validate --specs --strict` and `openspec validate us-075-lane-assignments --strict`;
  - the links, screens, manual and traceability scripts;
  - markdownlint;
  - Redocly.
