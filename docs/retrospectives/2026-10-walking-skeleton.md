# Retrospective: Walking Skeleton (S1–S2)

**Date**: 2026-10-22
**Status**: concluded – owner decisions of 2026-10-22 in [section 6](#6-decisions)
**Scope**: architecture retrospective after the walking skeleton, as planned in [docs/sprint-plan.md](../sprint-plan.md) ("After S2: retrospective on the architecture; insights from the slice feed into S3 onward")
**Participants**: [Lukas Mathis – Repository Owner](../../MAINTAINERS.md); prepared by the coding agent

---

## 1. Facts

| Item | Value |
|---|---|
| Period | 2026-10-13 (ADR-032 to ADR-036) – 2026-10-22 (change 7 archived), 10 calendar days |
| Plan | S1–S2, i.e. 4 weeks, 31 story points plus project setup |
| OpenSpec changes | 8 archived: `ws-01-project-setup`, `us-013-local-bootstrapping`, `us-013-system-admin-login` (3a), `us-001-oidc-login` (3b), `us-033-import-metamodel`, `us-141-entity-batch-upsert`, `us-046-create-catalog`, `us-049-show-catalog-data` |
| Pull requests | #11–#59, of them 7 Dependabot updates on 2026-10-22 |
| ADRs created during the slice | ADR-036 to ADR-044 (ADR-038 Spring Boot 4.1, ADR-039 Java 25, ADR-040 Nuxt UI after the PrimeVue license change, ADR-041 root POM, ADR-042 setup phase, ADR-043 license policy, ADR-044 view state in the URL) |
| Main specs | 20 capabilities in 6 domains (`audit`, `catalog`, `identity`, `metamodel`, `platform`, `repository`) |
| Code | backend ≈ 11 500 lines Java, backend tests ≈ 12 400 lines (Java and Gherkin), frontend ≈ 3 000 lines Vue/TypeScript |
| Tests | 246 backend tests, 123 Cucumber scenarios, 65 frontend unit tests |
| CI | backend job 11–14 minutes, all other jobs below 1 minute |
| Contract | 126 operations in `api/openapi.yaml`, about 28 implemented |
| DoD of the walking skeleton | met: the end-to-end scenario runs in the development environment with real Keycloak logins (verified for change 6 and change 7) |

## 2. What the Slice Confirmed

| Decision | Observation |
|---|---|
| Spec-first contract with generated interfaces (ADR-013) | Every API change started in `api/openapi.yaml`; generated server interfaces and frontend types kept backend and frontend aligned without drift. The authorization extension `x-oea-authorization` plus the matrix test caught every unprotected operation automatically. |
| Layer separation api/app/core (ADR-028) | Held through seven changes; the ArchUnit rules never had to be weakened. |
| OpenSpec cycle propose → implement → archive | Every change had a reviewable plan before code; owner decisions were recorded in the proposal; the archive produced growing main specs. Scope cuts (US-141 AC1 only, US-049 without joins) stayed visible. |
| PostgreSQL only with Testcontainers (ADR-030, ADR-016) | No H2 surprises; schema, grants and triggers are tested against the real database. |
| Real IdP in development and tests (ADR-036) | No authentication stub; the OIDC flow including TOTP and `amr` was tested end to end. It found real issues early (`amr` in development realm tokens, #39). |
| Audit hash chain and role separation (ADR-033, ADR-037) | Implemented in change 2 and used by every later change without rework. |
| Storybook screen stories (ADR-031) | The stories doubled as test fixtures; the screen images come from the real components. |

## 3. Findings

### F1 – Requirements written before the architecture needed realignment in most changes

Evidence: REQ-046 described `sortBy`/`sortDir`, `pageSize` ≤ 500 and `totalCount` instead of the contract conventions (aligned in change 7); US-046 AC4, REQ-043 AC5 and UC-06 A4 contradicted ADR-032 (aligned in change 6); the contract contained `GET /views/catalog` operations that were never implemented and had to be removed. About 98 of 126 contract operations are still unimplemented sketches from the requirements phase.

Consequence: every change spends effort on reconciling documents; unimplemented operations look binding to readers and to the authorization check.

### F2 – The shared exception handler grows request-path branches

Evidence: `ApiExceptionHandler` decides by request path in 8 places (batch, catalogs, package import, catalog query) how a validation error is answered (400 or 422, field list or pointer).

Consequence: every new operation with its own problem format adds a branch; the mapping is not visible at the operation.

### F3 – Test and CI run time grows with every change

Evidence: the backend job takes 11–14 minutes; the Cucumber run alone about 8.5 minutes; the tests start 37 containers (own database per test class, own Keycloak instance per feature area).

Consequence: with 19 sprints ahead the backend job will exceed its time limit and slow down every pull request.

### F4 – Quality gates that exist in configuration but not in CI

Evidence: the Storybook preview declares `a11y: { test: 'error' }`, but no CI job runs the stories, so light-mode contrast violations stayed unnoticed until #59; `openspec validate --specs --strict` is not part of CI, so an invalid main spec was merged and fixed only in #58; screen images are rendered only after the merge to `main`.

Consequence: rules of REQ-130 and of the OpenSpec workflow depend on manual checks.

### F5 – Every frontend dependency update breaks the license check

Evidence: Dependabot PR #51 failed the third-party license check twice – once for the new versions, once after `main` was merged into the branch; both times `THIRD-PARTY-LICENSES.md` had to be regenerated by hand.

Consequence: dependency updates need manual work, which invites postponing them.

### F6 – The sprint plan's assumptions no longer hold

Evidence: the plan assumes Devstral (Mistral API) as LLM assistant and 2-week sprints with about 30 story points; the slice was built with Claude Code in 10 days instead of 4 weeks.

Consequence: dates and sprint contents of `docs/sprint-plan.md` are not a reliable basis for S3 onward.

### F7 – Deferred items are scattered across design documents

Evidence: the risk sections of the archived designs defer, among others: keyset paging (REQ-071), savepoint batching for large batches, throttling of the setup endpoint (REQ-168), structured logging of the checkpoint line, a build-time check for bundled icons, typed column and join schemas (US-047, US-048), property visibility beyond `public` (REQ-160), optimistic locking for interactive edits.

Consequence: none of them is tracked in the sprint plan or as an issue; they can be lost.

### F8 – Status of the security baseline review is unclear

Evidence: [security-reviews/requirements-baseline-2026-10.md](../../security-reviews/requirements-baseline-2026-10.md) grants sign-off "once the listed findings are resolved"; ADR-032 to ADR-037 and the identity changes address several findings (e.g. SEC-01, SEC-06, SEC-07, SEC-09), but no review confirms which findings are closed by the implementation.

Consequence: the expansion sprints build on an unverified security baseline.

### F9 – Local development environment needs undocumented workarounds

Evidence: in the Distrobox environment no JDK is installed, symlinks of the checkout (e.g. `.claude/commands`, `node_modules/.bin`) became plain files, and rootless Podman with SELinux needs relabelled bind mounts for `compose.dev.yaml`.

Consequence: running the full checks locally depends on knowledge outside the repository.

## 4. Proposed Actions

| # | Action | Finding | Proposed timing | Owner decision needed |
|---|---|---|---|---|
| A1 | Mark contract operations without implementation (e.g. `x-oea-status: planned`) and treat parameter and payload details in REQs as non-binding until the implementing change aligns them; the proposal of each change lists the REQ/US sections it realigns | F1 | before S3 | decided: mark as planned |
| A2 | Replace the path branches of `ApiExceptionHandler` by exception types that carry status, problem type and fields | F2 | first change of S3 | no |
| A3 | Share one PostgreSQL and one Keycloak container per test JVM with per-test-class databases created from a migrated template; split the backend job into unit/integration and Cucumber | F3 | S3 | no |
| A4 | Add a CI job that runs the Storybook stories with their play functions and axe (`@storybook/addon-vitest` or the test runner), and `openspec validate --specs --strict` to the CI | F4 | S3 | no |
| A5 | Regenerate `THIRD-PARTY-LICENSES.md` automatically on Dependabot pull requests (workflow commit with a restricted token, REQ-192) or group frontend updates weekly | F5 | S3 | decided: automatic |
| A6 | Re-baseline `docs/sprint-plan.md`: actual assistant, measured throughput, order of S3–S8 | F6 | before S3 | decided: before S3 |
| A7 | Collect the deferred items of F7 in a backlog section of the sprint plan (or as GitHub issues) with the sprint that resolves them | F7 | before S3 | decided: GitHub issues |
| A8 | Security re-review of the implemented skeleton against the baseline findings (security-engineer role), result in `security-reviews/` | F8 | before S3 | no |
| A9 | Document the local prerequisites and workarounds (JDK 25, symlinks, SELinux labels) in `docs/workflow-example.md` or a development guide; optionally a dev container | F9 | when convenient | no |

A2, A3 and the request-size limit of WS-02 (#73) are implemented in the change `s2b-backend-hardening` (package S2b): 13 instead of 119 PostgreSQL container starts per backend run; Cucumber in 166 s instead of 516 s locally.

A1, A4, A5 and A9 are implemented in the change `s2b-ci-and-contract` (package S2b): 88 of 126 operations carry `x-oea-status: planned` and answer 501 after authorization, and the backend does not start if the marker and the implementation disagree; the stories run as tests with their accessibility check and `openspec validate --specs --strict` runs in CI; `dependabot-licenses.yml` regenerates `THIRD-PARTY-LICENSES.md` on Dependabot pull requests; [`docs/development.md`](../development.md) describes the local environment (no dev container).

## 5. Questions for the Owner

1. Which observations are missing from the owner's perspective (product, workflow, collaboration with the agents)?
2. Is the convention of A1 acceptable, or should the unimplemented operations be removed from the contract until a change needs them?
3. Should the deferred items (A7) live in the sprint plan or as GitHub issues?
4. Is a workflow that commits to Dependabot branches acceptable (A5), or should the regeneration stay manual?
5. Which actions go into S3, and does S3 stay UC-02 (system admin bootstrapping remainder) as planned?

## 6. Decisions

Owner decisions of 2026-10-22:

- **A1**: contract operations without implementation are marked as planned (`x-oea-status: planned`); parameter and payload details of requirements are non-binding until the implementing change aligns them
- **A5**: a workflow regenerates `THIRD-PARTY-LICENSES.md` on Dependabot pull requests automatically, with write access limited to that file on `dependabot/*` branches (REQ-192)
- **A7**: deferred items are tracked as GitHub issues with the sprint that resolves them – created as #61 to #70 (label `deferred`, milestones S3, S8–S11); the savepoint batching of US-141 is not tracked because its 5-second test passes
- **Order**: the actions come first – re-baselined sprint plan (A6), issues for the deferred items (A7), security re-review (A8) and the technical actions A2–A5 – and S3 with UC-02 (system admin bootstrapping remainder) follows as planned
