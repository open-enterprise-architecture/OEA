# Sprint Plan: OEA Implementation Order

**As of:** 2026-10-28 (S3 sliced into changes, UC-24 added as S22; re-baselined on 2026-10-22 after the walking-skeleton retrospective, action A6 of [retrospectives/2026-10-walking-skeleton.md](retrospectives/2026-10-walking-skeleton.md); before: 2026-10-13, walking skeleton moved to the beginning)  
**Basic assumptions:**

- Sprints are **content packages** of ~30 SP in a fixed order, not time boxes; the plan carries no calendar dates (owner decision 2026-10-22)
- Measured throughput: S1–S2 (31 SP + project setup) took 10 calendar days (2026-10-13 to 2026-10-22); the forecast is updated after each milestone, not per sprint
- Way of working: Claude Code and OpenCode with the shared configuration of `AGENTS.md` (agent roles in `.opencode/agents/`); the plan does not fix a model
- Total: ~577 SP + project setup → 21 content packages plus the retrospective package S2b
- Order: walking skeleton first (thin E2E slice), then dependencies > MoSCoW priority > thematic coherence
- Deferred items of earlier changes are GitHub issues (label `deferred`) with the milestone of the package that resolves them
- UC-01 (66 SP, 19 USs) and UC-05 (55 SP) are deliberately stretched

---

## Milestone 1 — Walking Skeleton + Foundation (Sprints 1–8)

**Goal:** A thin end-to-end slice in S1–S2 (bootstrapping → login → metamodel import → entities → catalog), then expansion of auth and metamodel on this scaffold.

| Sprint | UC | Content | SP |
|--------|----|--------|----|
| **S1** ✓ | Setup + UC-02 + UC-01 | **⭐ Walking Skeleton (1/2), done 2026-10-15:** project setup (Maven `backend/`, Vue `frontend/`, `api/openapi.yaml`, operator `compose.yaml` and development `compose.dev.yaml` with Keycloak dev realm (ADR-036), migration step with owner/runtime role separation (ADR-033), CI), US-013 local bootstrapping, US-001 OIDC login | 13 + setup |
| **S2** ✓ | UC-04 + UC-06 + UC-19 | **⭐ Walking Skeleton (2/2), done 2026-10-22:** US-033 import starter package, US-141 batch creation (AC1), US-046 create catalog, US-049 display catalog data (basis, incl. pagination) | 18 + E2E hardening |
| **S2b** ✓ | – | **Retrospective actions, done 2026-10-23** (A1–A5, A8, A9 of the retrospective; changes `s2b-backend-hardening` and `s2b-ci-and-contract`): planned contract operations marked, `ApiExceptionHandler` without path branches, faster backend tests, Storybook a11y and `openspec validate` in CI, license regeneration on Dependabot PRs, security re-review of the skeleton, local development guide; request-size limit [#73](https://github.com/open-enterprise-architecture/OEA/issues/73) | – |
| **S2c** ✓ | – | **User manual foundation, done 2026-10-27** ([ADR-045](../adrs/ADR-045-user-manual.md), [REQ-196](../requirements/req/REQ-196-user-manual.md)); two changes: `s2c-manual-site` done 2026-10-25 (VitePress project, content, coverage check, API reference, Pages with Storybook under `/storybook/`), `s2c-manual-release` done 2026-10-27 (walkthrough tests with screenshots, versions per release under `/vX.Y/`, `main` under `/next/`, publication through `gh-pages`; first release manual `v0.1.0`). Scope: VitePress project `manual/` in English and German, GitHub Pages with Storybook under `/storybook/`, API reference generated from the contract, coverage check in CI, Playwright walkthrough tests with screenshots against the Compose stack, manual content for the walking-skeleton functions, release workflow with versions per release | – |
| **S3** | UC-02 (remainder) | System admin bootstrapping and the release conditions of the security re-review, in three changes: (1) `s3-throttling-and-denials` – per-principal rate limits [#74](https://github.com/open-enterprise-architecture/OEA/issues/74), setup-token throttling [#61](https://github.com/open-enterprise-architecture/OEA/issues/61), audit of permission denials [#75](https://github.com/open-enterprise-architecture/OEA/issues/75) (WS-01, WS-02, WS-04); (2) `us-019-admin-safeguards` – remote bootstrapping and admin safeguards: US-014, US-018, US-019, break-glass for UC-02 E4 as a CLI command in the backend container (owner decision 2026-10-28); (3) `s3-structured-logging-and-icons` – structured JSON logging [#63](https://github.com/open-enterprise-architecture/OEA/issues/63), build-time icon check [#62](https://github.com/open-enterprise-architecture/OEA/issues/62). Already done: US-015, US-016, US-017 (setup token, no parallel bootstrapping, audit). Status 2026-10-29: changes (1) and (2) implemented (#114, #117), change (3) in implementation | ~30 |
| **S4** | UC-01 (1/2) | Login: session hardening, minimal password, generic error messages | ~25 |
| **S5** | UC-01 (2/2) | Login: passkey, TOTP, configurable token lifetime, lifecycle invited→active, audit log | ~33 |
| **S6** | UC-03 | Set up authentication method: 2FA enrollment (TOTP + passkey), password policies, password reset | 39 |
| **S7** | UC-04 (1/2) | Metamodel: create/edit EntityTypes, basic PropertyDefinitions, GUI configuration | ~27 |
| **S8** | UC-04 (2/2) + UC-06 | Metamodel: connection EntityTypes, architecture extension, lock mode, audit log + US-047 configure columns; deferred [#64](https://github.com/open-enterprise-architecture/OEA/issues/64), [#65](https://github.com/open-enterprise-architecture/OEA/issues/65) | ~22 + 5 |

**Deliverable S2 (met 2026-10-22):** Walking skeleton runnable (DoD in `docs/walking-skeleton.md`):
admin bootstrap → OIDC login → starter package → entities via batch API → catalog table.

**User manual from S3 onward:** every change with user-visible behavior updates the manual in English and German and its walkthrough screenshots in the same change (ADR-045 rule 8); the release workflow of S2c publishes the manual per version tag (REQ-196).

**Re-scoping S4–S6:** the walking skeleton already implemented parts of UC-01 and UC-03 (sessions with timeouts and revocation, TOTP enrollment and login of the system admin, throttling of local logins, IdP group mappings; specs `openspec/specs/identity/`). Before S4 starts, its content is reconciled with these specs and the story points are re-estimated.

**Deliverable S8:** Auth (UC-01, UC-02, UC-03) and metamodel (UC-04) complete.

> After S2: retrospective on the architecture; insights from the slice feed into S3 onward. Held on 2026-10-22: [retrospectives/2026-10-walking-skeleton.md](retrospectives/2026-10-walking-skeleton.md).

---

## Milestone 2 — Core Platform (Sprints 9–14)

**Goal:** Complete catalog, property model, solution lifecycle

| Sprint | UC | Content | SP |
|--------|----|--------|----|
| **S9** | UC-06 (2/2) | Catalog: join definition, join mode runtime, filters, saved views (incl. remainder of US-049; pagination done in S2); deferred [#66](https://github.com/open-enterprise-architecture/OEA/issues/66), [#67](https://github.com/open-enterprise-architecture/OEA/issues/67), [#76](https://github.com/open-enterprise-architecture/OEA/issues/76) | ~30 |
| **S10** | UC-21 + UC-05 (1/3) | Configure property visibility + create solution, define baseline; deferred [#68](https://github.com/open-enterprise-architecture/OEA/issues/68) | 22 + ~8 |
| **S11** | UC-05 (2/3) | Solution: capture entity deltas, diff view, parallel-solution conflict warning; deferred [#69](https://github.com/open-enterprise-architecture/OEA/issues/69), [#70](https://github.com/open-enterprise-architecture/OEA/issues/70) | ~25 |
| **S12** | UC-05 (3/3) + UC-14 | Solution: approval workflow (proposed→approved) + view change history | ~22 + 13 |
| **S13** | UC-15 + UC-16 | Restore entity state (full) + partial restore of individual properties | 11 + 16 |
| **S14** | UC-11 | Define plateau, perform go-live, timeline, implementation status | 31 |

**Deliverable S14:** Complete solution lifecycle:
create → capture delta → detect conflict → plateau → history → restore.

---

## Milestone 3 — EA Views & Navigation (Sprints 15–18)

**Goal:** Viewpoints, navigation tree, dashboard, business processes — all MUST UCs covered

| Sprint | UC | Content | SP |
|--------|----|--------|----|
| **S15** | UC-12 + UC-13 (1/2) | Manage viewpoint + navigation tree basic structure | 15 + ~17 |
| **S16** | UC-13 (2/2) + UC-07 (1/2) | Navigation tree finished + dashboard scaffold, first widgets | ~17 + ~15 |
| **S17** | UC-07 (2/2) + UC-10 | Dashboard: charts, KPI cards, PropertyAggregation + model business processes | ~16 + 24 |
| **S18** | UC-08 | Data flow map (data lineage): n-connection model (ADR-010), visualization, analysis | 24 |

**Deliverable S18:** Complete navigation and visualization set; all 14 MUST UCs implemented.

---

## Milestone 4 — Extended Features / SHOULD (Sprints 19–21)

**Goal:** Arc42 documentation, continuum, TRM, conformance analysis

| Sprint | UC | Content | SP |
|--------|----|--------|----|
| **S19** | UC-09 (1/2) | Solution architecture per Arc42: basic structure, context diagram, building block view | ~23 |
| **S20** | UC-09 (2/2) + UC-17 | Arc42 finished (runtime, deployment view) + manage continuum building blocks | ~23 + 25 |
| **S21** | UC-18 + UC-19 + UC-20 | Import continuum package + configure TRM + conformance analysis | 13 + 17 + 13 |

**Deliverable S21:** Complete v1.0 feature set, production-ready.

---

## Milestone 5 — v1.x (after v1.0)

**Goal:** Real-time collaboration on the diagrams of milestone 3 ([UC-24](../requirements/use-cases/UC-24-collaborate-in-real-time.md), priority *should*, owner request of 2026-10-28)

| Sprint | UC | Content | SP |
|--------|----|--------|----|
| **S22** | UC-24 | Spike for client rebase and latency (ADR-047), collaboration session (WebSocket), presence and pointers, live changes, co-editing with operation log and versions per author, sticky notes, comment threads, checkpoints (REQ-197 to REQ-204); stories are cut when the package is planned | ~40 (estimate, complexity xl) |

UC-24 needs diagrams (UC-10, UC-12, S15–S17) and the version history (UC-14, S12); it can start after milestone 3 if the owner wants it before the SHOULD features of milestone 4.

---

## Overall Overview

| Milestone | Sprints | Topic | MUST UCs | SHOULD UCs |
|-----------|---------|-------|----------|------------|
| Walking skeleton + foundation | S1–S8 (+ S2b) | E2E slice (S1–S2), then auth + metamodel complete | UC-01, UC-02, UC-03, UC-04, UC-06 (partial) | UC-19 (US-141 partial) |
| Core platform | S9–S14 | Catalog complete + solution lifecycle | UC-06, UC-11, UC-14, UC-21 | — |
| EA views | S15–S18 | Navigation + dashboard + processes | UC-10, UC-12, UC-13 | UC-07, UC-08 |
| SHOULD features | S19–S21 | Arc42 + continuum + TRM | — | UC-09, UC-17, UC-18, UC-19, UC-20 |
| v1.x | S22 | Real-time collaboration | — | UC-24 |

---

## UC Dependencies (Order Not Negotiable)

```text
UC-02 (no predecessor — bootstrapping is the first step)
└── UC-01 (login requires person+role created by UC-02)
    ├── UC-03 (set up auth method)
    └── UC-04 (metamodel requires login)
        ├── UC-06 (catalog requires metamodel; preconditions in UC-06)
        │   └── UC-07 (dashboard builds on catalog)
        ├── UC-05 (solution requires metamodel; complements UC-06, but is not a precondition)
        │   └── UC-11 (plateau/go-live requires solutions)
        └── UC-21 (property visibility requires PropertyDefinitions)
```

Entities for the catalog are created in the walking skeleton via the batch API (US-141); the business path via solution deltas (UC-05) follows in milestone 2.

The SHOULD UCs (UC-08, UC-09, UC-15, UC-16, UC-18, UC-20) have no hard predecessors
other than UC-01/UC-04 and can be pulled forward from milestone 2 on if needed.

---

## Notes

**UC-01 is the largest single block at 66 SP.** Auth bugs propagate through all later
sprints — OIDC basis in the walking skeleton (S1), remainder deliberately stretched over S4+S5 and completed before
the metamodel expansion.

**Open auth points of S1–S2** (status 2026-10-22): the setup token handover channel (REQ-017)
is implemented as a token file inside the backend container (change 2, [First administrator](../manual/setup/first-administrator.md));
the claim mapping of IdP groups to OEA roles is implemented as IdP group mappings (US-147,
changes 3a/3b); recovery/break-glass (UC-02 E4) is a CLI command inside the backend container (owner decision 2026-10-28, S3 change `us-019-admin-safeguards`). See [UC-02](../requirements/use-cases/UC-02-system-admin-bootstrapping.md),
[ADR-006](../adrs/ADR-006-auth-stack-choice.md) and [US-018](../requirements/user-stories/US-018-warning-empty-admin-claim.md).

**Way of working:** every package is implemented in OpenSpec changes (propose → implement → archive,
`openspec/README.md`) with Claude Code or OpenCode and the agent roles of `AGENTS.md`; the walking skeleton
took one change per user story, with the proposal reviewed and merged before the implementation.

**SHOULD UCs can be pulled forward on stakeholder request** — the dependencies
are documented; UC-07 (dashboard) and UC-08 (data lineage) have no predecessors
other than UC-01/UC-04/UC-06.
