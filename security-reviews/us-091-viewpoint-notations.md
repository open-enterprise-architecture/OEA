# Security Review: us-091-viewpoint-notations

| Field | Value |
|---|---|
| Review type | Step 2 of the change workflow: security aspects added to the OpenSpec change before implementation |
| Reviewer | Security Engineer (agent) |
| Date | 2026-11-17 |
| Branch / commit | `feature/s15-navigation-tree-and-viewpoint-notations` @ `534f1629` |
| Scope | `openspec/changes/us-091-viewpoint-notations/` (proposal, design, tasks, delta specs `metamodel/viewpoints` and `metamodel/package-import`), main specs `metamodel/viewpoints` and `metamodel/package-import`, `Viewpoints.java`, `PackageValidator.java`, the viewpoint and package schemas of `api/openapi.yaml`, `docs/audit-event-catalog.md`, ADR-032 |
| Method | Threat analysis of the notation mapping, the system-defined viewpoints and the package section `viewpoints` against the existing code; no code was run |
| Result | Additions made to the change; Q-1 answered by the owner (option a); the step-3 decisions of the architect keep every finding covered. Sign-off of the specification granted |

## Threats Considered

| ID | Threat | Severity without the addition | Addition |
|---|---|---|---|
| VN-01 | Package content is not checked by the contract limits of `ViewpointWrite`: `PackageViewpoint` has no lengths or counts, so a package stores names of any length and very large mapping lists | Medium | Package-import requirement "Limits and checks of package viewpoints" with both boundaries; design D8; task 2.5 |
| VN-02 | A package viewpoint carries the ID of an organization viewpoint or of another package's viewpoint, and an upsert by ID takes it over and makes it read-only | Medium | Requirement "Package viewpoints never take over foreign viewpoints" (409); design D8; task 2.5 |
| VN-03 | A forged API body with `viewpointType: system-defined` and a `sourcePackage` creates a viewpoint nobody can edit or delete | Medium | Viewpoints requirement "Origin of a viewpoint set only by the import"; design D8 (schema check of V41 as backstop); task 2.5 |
| VN-04 | The same metatype mapped twice hits the primary key of `viewpoint_mappings` and answers 500 | Low | Requirement "Limits of the notation mapping" (422 naming `mappings`); task 2.5 |
| VN-05 | Two package viewpoints whose names differ only in case overwrite each other within one import | Low | Package-import requirement "Limits and checks of package viewpoints"; task 2.5 |
| VN-06 | A visual hint from a package injects CSS or SVG when the diagrams render it later | Low | Hint pattern applied to packages; scenario "Invalid visual hint in a package"; task 2.5 |
| VN-07 | A re-import silently deletes viewpoints of the package; the audit trail only holds a count | Low | `viewpointsDeleted` with the IDs in `metamodel.package-imported`; requirement "Package viewpoints never take over foreign viewpoints"; design D8; tasks 2.5, 4.1 |
| VN-08 | Changes of notation and mappings are not visible in `viewpoint.updated` | Low | Viewpoints requirement "Audit of notation and mappings"; task 2.5 |

Checked without an addition:

- `listNotationElements` returns static data of the release; `anyOfPermissions` `entity:read`, `viewpoint:manage` is sufficient.
- A package viewpoint may only reference metatypes of the instance configuration or of the package. The existing `kinds(…, INSTANCE_CONFIGURATION)` check is reused, so metatypes of solution extensions are excluded (task 2.5 tests it).
- A package with the `packageId` of the starter package replaces the starter's viewpoints. This is the existing trust model of the package import for metatypes (ADR-002, REQ-114) and is not changed here.

## Points for the Solution Architect (consistency)

- `ViewpointWrite.mappings` was limited to 500 (design D5), while a viewpoint may allow 500 metatypes and 500 connection types. Resolved in step 3: the limit is 1000, with the boundary values 1000 and 1001 in the spec and the tasks.
- `notationElement` `maxLength: 100` is new; the longest key of the catalog must fit.
- `listNotationElements` at `GET /admin/viewpoints/notations` sits next to `GET /admin/viewpoints/{id}`; the literal path must win over the template in routing and in the authorization interceptor. Resolved in step 3 (see below).

## Check of the Step-3 Decisions (2026-11-17)

The architect's decisions were checked against the findings above. None of them weakens a finding.

| Decision | Effect on the findings |
|---|---|
| `mappings` limited to 1000 (500 element and 500 connection types) in the API and in packages | Neutral. The limit stays bounded by the number of allowed types (one mapping per metatype), and the 10 MB package limit bounds the total. VN-01 and VN-04 stay covered with the boundary values 1000 and 1001 |
| Invalid visual hint answers 400 from the contract pattern (API); packages keep 422 from `PackageValidator` | Neutral. The pattern is enforced on both paths, which is what VN-06 needs; only the status code differs |
| Read-only rejection as 422 with the problem type `urn:oea:problem:read-only-viewpoint` | Neutral. The detail is fixed and discloses nothing beyond what a reader of the viewpoint already sees (`viewpointType`); VN-03 stays covered |
| Package viewpoint identity by a stable, required `id`, with a conflict check on ID or name against organization viewpoints and those of other packages (409) | Covers VN-02. Matching by ID within `source_package_id` and the 409 on a foreign ID or name prevent the takeover of a foreign row. The re-import deletes only rows of the same package. Identity by ID is the same model as for metatypes |
| Routing of `/admin/viewpoints/notations` resolved by Spring MVC ranking; the authorization rule follows the handler method | Closes the routing point above; the controller test of task 2.3 pins it |

## Business Questions for the Owner

- **Q-1 – Who may install system-defined viewpoints?** The package import needs only `metamodel:write`. With it, a metamodel administrator who does not hold `viewpoint:manage` installs viewpoints that the persons responsible for viewpoints cannot change or delete. These viewpoints also block their names for organization viewpoints. Only a re-import of the package without them removes them. Options:
  - (a) accept it: `metamodel:write` is enough, as for metatypes;
  - (b) a package with a `viewpoints` section also needs `viewpoint:manage`.

  In the built-in roles (ADR-032) `metamodel:write` belongs only to `metamodel-admin`, and `viewpoint:manage` only to `enterprise-architect`. Under (a) the metamodel administrator decides on viewpoints that the enterprise architects must accept. Under (b) a person holding only `metamodel-admin` can no longer import a package with viewpoints, the starter package 1.2.0 included, without also holding `enterprise-architect`.

  **Closed (owner decision of 2026-11-17): option (a).** `metamodel:write` alone installs the system-defined viewpoints of a package, the same model as for metatypes. The architect recorded it in design D8 and the proposal. The residual risk is accepted by the owner: a metamodel administrator can install read-only viewpoints that occupy their names, and every such import is audited (`metamodel.package-imported` with the viewpoint counts and deleted IDs).

## Sign-off

**Granted** for the specification. Q-1 is closed and the step-3 decisions keep every finding covered. The code review before the merge checks task 2.5.
