# Proposal

## Why

The architecture review of 2026-11 found smaller inconsistencies in `api/openapi.yaml`, collected in issue [#262](https://github.com/open-enterprise-architecture/OEA/issues/262). API-3, API-5 and API-6 were resolved on 2026-11-16. Two findings are still open:

- **API-7:** the metamodel lives under two path roots. 20 paths are under `/metamodel/*`, and 4 package paths under `/admin/metamodel/*` (export, import, starter packages, starter package import). The review asks for one convention before 1.0.
- **API-8:**
  - 27 implemented operations have no `description` (20 planned ones neither);
  - 14 operations define the UUID path parameter `id` inline instead of `UuidIdParam`;
  - page envelopes name their list `items`, `rows`, `entries` or `versions`;
  - no lint rule keeps new operations from repeating this.

The owner added #262 to S17 on 2026-11-18. This is S17 change 8 ([sprint plan](../../../docs/sprint-plan.md)), the last change before UC-10.

## Traceability

| Artifact | Reference |
|---|---|
| Issue | [#262](https://github.com/open-enterprise-architecture/OEA/issues/262) (API-3, API-5 to API-8) |
| Review | [Architecture review 2026-11](../../../docs/reviews/architecture-2026-11.md), findings API-7 and API-8 |
| ADRs | [ADR-013](../../../adrs/ADR-013-api-style.md) (spec-first contract), [ADR-026](../../../adrs/ADR-026-external-system-integration.md) (package paths) |
| Sprint / Milestone | S17 / Milestone 3 ([sprint plan](../../../docs/sprint-plan.md)) |

## What Changes

**Decisions of this change (technical, open for review):**

- **API-7, one path root `/metamodel`:**
  - the four package operations move to `/metamodel/export`, `/metamodel/import`, `/metamodel/starter-packages` and `/metamodel/starter-packages/{packageId}/import`;
  - their `operationId`, permissions and behavior stay;
  - `/admin` keeps the instance administration (identity, settings, operations), not the metamodel;
  - there is no alias for the old paths: before 1.0 the only client is the one in this repository, which moves with the contract;
  - ADR-026, REQ-032, REQ-033, REQ-035, REQ-058, REQ-059, US-032, US-033, the walking skeleton, the data model and the manual are realigned.
- **API-8, descriptions:** every operation gets a `description` (27 implemented, 20 planned, since the rule cannot exclude planned ones), and the Redocly rule `operation-description` becomes an error in a new `redocly.yaml` that keeps the recommended ruleset.
- **API-8, shared parameter:** the 14 UUID path parameters `id` use `UuidIdParam`. Path parameters with other names (`widgetId`, `deltaId`, `mappingId` and so on) stay inline. A rule rejects an inline path parameter `id`; the 3 planned non-UUID ones are allowlisted.
- **API-8, string limits:** a rule requires `maxLength` on every string property of a request body, unless it has `enum`, `const` or a `format`. The 74 existing gaps are a baseline allowlist: since contract limits are enforced by bean validation before the handler, adding a limit to an existing property would change its error response or introduce a new limit. New properties follow the rule; aligning the existing ones is a follow-up.
- **API-8, envelope names:** the existing envelopes keep their names (`rows`, `entries`, `versions`, `violations`): renaming them changes the client and gains nothing. New page envelopes name their list `items`, and a rule enforces this, with the existing ones as an allowlist.
- **Rule mechanism:** Redocly for descriptions; the other rules in `scripts/check_openapi_conventions.py` (standard library only, on a JSON bundle made by Redocly) with a test, run in the CI job `openapi-lint`. Every allowlist fails on a stale entry, so it only shrinks.

**Not in this change:**

- renaming existing envelopes;
- shared parameters for path parameters other than `id`;
- new limits, `maxLength` on existing request properties, or a changed behavior or error response of any operation;
- closing #262 by hand: the pull request closes it.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None: the paths change, the behavior of every operation stays. `skip_specs` is set.

## Impact

- **Contract:** `api/openapi.yaml`, a new `redocly.yaml`, the convention check `scripts/check_openapi_conventions.py` with its test, and the CI job `openapi-lint`.
- **Backend:**
  - the generated interfaces follow the paths;
  - `RequestSizeConfiguration.IMPORT_PATH` (the only path-based configuration naming these paths);
  - the tests and Cucumber steps that call the old paths.
- **Frontend:** `api/metamodel.ts`, the generated schema, specs and fakes.
- **Documentation:**
  - the manual (pages "Metamodel packages" and "Operations", regenerated API reference) in en and de;
  - ADR-026, REQ-032, REQ-033, REQ-035, REQ-058, REQ-059, US-032, US-033, `docs/walking-skeleton.md`, `docs/data-model.puml`;
  - the review document (API-7 and API-8 resolved);
  - the sprint plan.
