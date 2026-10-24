# Proposal

## Why

The walking-skeleton retrospective left four actions for the CI and the contract: about 88 of 126 contract operations are unimplemented sketches that look binding (F1, A1); the Storybook stories with their accessibility checks are built but never run (F4, A4), and the OpenSpec specs are not validated in CI; every Dependabot update of a distributed dependency fails the third-party license check until someone regenerates `THIRD-PARTY-LICENSES.md` by hand (F5, A5, e.g. PR #51); and the local prerequisites and workarounds of the development environment are not written down (F9, A9). Package S2b resolves them before S3 adds further operations.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | – (technical package, no user goal) |
| User Stories | – |
| Requirements | [REQ-187: HTTP security baseline](../../../requirements/req/REQ-187-http-security-baseline.md) (generic problem responses), [REQ-158: Deny-by-default authorization](../../../requirements/req/REQ-158-deny-by-default-authorization.md) (planned operations stay authorized), [REQ-192: Least privilege for CI/CD workflows](../../../requirements/req/REQ-192-ci-least-privilege.md) (license commit job), [REQ-191: Supply chain integrity](../../../requirements/req/REQ-191-supply-chain-integrity.md) (Dependabot updates) |
| ADRs | [ADR-013](../../../adrs/ADR-013-api-style.md) (spec-first contract), [ADR-031](../../../adrs/ADR-031-ui-prototyping-storybook.md) (Storybook stories, accessibility), [ADR-032](../../../adrs/ADR-032-authorization-model.md) (authorization in the contract), [ADR-043](../../../adrs/ADR-043-third-party-license-policy.md) (license policy) |
| Findings | [Retrospective](../../../docs/retrospectives/2026-10-walking-skeleton.md) A1 (F1), A4 (F4), A5 (F5), A9 (F9) |
| Sprint / Milestone | S2b / Milestone 1 ([Sprint plan](../../../docs/sprint-plan.md)) |

The change name follows the package (`s2b-`) instead of a user story, like `s2b-backend-hardening`, because it implements retrospective actions, not a story.

## What Changes

- **Planned contract operations** (A1): every operation of `api/openapi.yaml` without implementation carries `x-oea-status: planned` and declares the shared response `NotImplemented` (501). A planned operation keeps its authorization rule (ADR-032) and answers 501 with the problem type `urn:oea:problem:not-implemented` instead of an empty body. The startup coverage check fails if an implemented operation is marked planned or an unimplemented one is not, so the marker cannot drift. Parameter and payload details of requirements count as non-binding until the implementing change aligns them; the proposal rule in `openspec/config.yaml` asks every change to list the REQ/US sections it realigns.
- **Storybook tests in CI** (A4): the stories run with their play functions and the accessibility check (`a11y: { test: 'error' }`, already configured) through `@storybook/addon-vitest` in a headless Chromium, as a step of the existing job "Frontend build". The job "Requirements completeness" additionally runs `openspec validate --specs --strict` with the OpenSpec version of the generated files.
- **License regeneration on Dependabot pull requests** (A5): a workflow regenerates `THIRD-PARTY-LICENSES.md` on Dependabot pull requests in a read-only job and commits the file to the Dependabot branch in a separate job that may change nothing else (REQ-192); it pushes with the token of a GitHub App, so the required checks run on the new commit.
- **Local development guide** (A9): `docs/development.md` describes the prerequisites (Docker or rootless Podman, JDK 25, Node.js 24, Python 3.12), the commands for builds, tests and the development environment, and the known workarounds (Testcontainers with rootless Podman, SELinux volume labels, Distrobox git). The README section "Development" links to it.

## Out of Scope

- Removing planned operations from the contract (decided against in the retrospective)
- Aligning the parameter and payload details of existing REQs; each implementing change does it for its own operations
- A dev container (optional in A9, not needed yet)
- Screenshot tests of the user manual (ADR-045 proposal, package S2c)

## Capabilities

### New Capabilities

- `platform/api-contract`: how the published contract states the implementation status of its operations and how the instance answers operations that are not implemented yet

### Modified Capabilities

- none

## Impact

- `api/openapi.yaml`: `x-oea-status: planned` and the response `NotImplemented` on every unimplemented operation (88 at the time of the proposal); a check in `scripts/check_openapi_authorization.py` for the allowed values of `x-oea-status`
- `backend/`: `io.oea.config.security.OperationCoverageCheck` (status consistency, handler for every operation), `NotImplementedInterceptor` (501 problem response), `io.oea.api.planned.PlannedOperationsController` (handlers of the generated interfaces without controller)
- `frontend/`: `@storybook/addon-vitest`, `@vitest/browser-playwright` (dev dependencies only, not distributed), a Vitest project for the stories, script `test:stories`
- `.github/workflows/ci.yml` (new steps in existing jobs; no new required check), new workflow `.github/workflows/dependabot-licenses.yml`; the repository owner creates the GitHub App "OEA license bot" and its Dependabot secrets
- `openspec/config.yaml` (proposal rule of A1), `docs/development.md`, `README.md`, the retrospective (status of A1, A4, A5, A9)
- No Flyway migration, no data model change, no screen change
