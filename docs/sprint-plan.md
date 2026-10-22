# Sprint Plan: OEA Implementation Order

**As of:** 2026-10-13 (walking skeleton moved to the beginning, see `docs/walking-skeleton.md`; S1 setup aligned with ADR-033 and ADR-036)  
**Basic assumptions:**

- 2-week sprints, ~30 SP/sprint
- LLM assistant: Devstral (Mistral API, 128k context)
- Total: ~577 SP + project setup → 21 sprints ≈ 10.5 months
- Order: walking skeleton first (thin E2E slice), then dependencies > MoSCoW priority > thematic coherence
- UC-01 (66 SP, 19 USs) and UC-05 (55 SP) are deliberately stretched

---

## Milestone 1 — Walking Skeleton + Foundation (Sprints 1–8)

**Goal:** A thin end-to-end slice in S1–S2 (bootstrapping → login → metamodel import → entities → catalog), then expansion of auth and metamodel on this scaffold.

| Sprint | UC | Content | SP |
|--------|----|--------|----|
| **S1** | Setup + UC-02 + UC-01 | **⭐ Walking Skeleton (1/2):** project setup (Maven `backend/`, Vue `frontend/`, `api/openapi.yaml`, operator `compose.yaml` and development `compose.dev.yaml` with Keycloak dev realm (ADR-036), migration step with owner/runtime role separation (ADR-033), CI), US-013 local bootstrapping, US-001 OIDC login | 13 + setup |
| **S2** | UC-04 + UC-06 + UC-19 | **⭐ Walking Skeleton (2/2):** US-033 import starter package, US-141 batch creation (AC1), US-046 create catalog, US-049 display catalog data (basis) | 18 + E2E hardening |
| **S3** | UC-02 (remainder) | System admin bootstrapping: setup token, remote bootstrapping, break-glass, audit log | ~30 |
| **S4** | UC-01 (1/2) | Login: session hardening, minimal password, generic error messages | ~25 |
| **S5** | UC-01 (2/2) | Login: passkey, TOTP, configurable token lifetime, lifecycle invited→active, audit log | ~33 |
| **S6** | UC-03 | Set up authentication method: 2FA enrollment (TOTP + passkey), password policies, password reset | 39 |
| **S7** | UC-04 (1/2) | Metamodel: create/edit EntityTypes, basic PropertyDefinitions, GUI configuration | ~27 |
| **S8** | UC-04 (2/2) + UC-06 | Metamodel: connection EntityTypes, architecture extension, lock mode, audit log + US-047 configure columns | ~22 + 5 |

**Deliverable S2:** Walking skeleton runnable (DoD in `docs/walking-skeleton.md`):
admin bootstrap → OIDC login → starter package → entities via batch API → catalog table.

**Deliverable S8:** Auth (UC-01, UC-02, UC-03) and metamodel (UC-04) complete.

> After S2: retrospective on the architecture; insights from the slice feed into S3 onward. Held on 2026-10-22: [retrospectives/2026-10-walking-skeleton.md](retrospectives/2026-10-walking-skeleton.md).

---

## Milestone 2 — Core Platform (Sprints 9–14)

**Goal:** Complete catalog, property model, solution lifecycle

| Sprint | UC | Content | SP |
|--------|----|--------|----|
| **S9** | UC-06 (2/2) | Catalog: join definition, join mode runtime, filters, pagination, saved views (incl. remainder of US-049) | ~30 |
| **S10** | UC-21 + UC-05 (1/3) | Configure property visibility + create solution, define baseline | 22 + ~8 |
| **S11** | UC-05 (2/3) | Solution: capture entity deltas, diff view, parallel-solution conflict warning | ~25 |
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

## Overall Overview

| Milestone | Sprints | Topic | MUST UCs | SHOULD UCs |
|-----------|---------|-------|----------|------------|
| Walking skeleton + foundation | S1–S8 | E2E slice (S1–S2), then auth + metamodel complete | UC-01, UC-02, UC-03, UC-04, UC-06 (partial) | UC-19 (US-141 partial) |
| Core platform | S9–S14 | Catalog complete + solution lifecycle | UC-06, UC-11, UC-14, UC-21 | — |
| EA views | S15–S18 | Navigation + dashboard + processes | UC-10, UC-12, UC-13 | UC-07, UC-08 |
| SHOULD features | S19–S21 | Arc42 + continuum + TRM | — | UC-09, UC-17, UC-18, UC-19, UC-20 |

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

**Open auth points must be decided in S1–S2** (no DoD blocker, but critical before S3):
setup token handover channel (REQ-017), recovery/break-glass (UC-02 E4),
claim mapping Entra ID→OEA roles. See [UC-02](../requirements/use-cases/UC-02-system-admin-bootstrapping.md),
[ADR-006](../adrs/ADR-006-auth-stack-choice.md) and [US-018](../requirements/user-stories/US-018-warning-empty-admin-claim.md).

**Devstral (Mistral API, 128k context) recommended** for multi-file Spring Boot features
(ADR-028: api/app/core layer separation). The context window allows processing complete feature branches
in a single request. For highly complex architecture decisions, cross-check
with a frontier model (Claude Sonnet) if needed.

**SHOULD UCs can be pulled forward on stakeholder request** — the dependencies
are documented; UC-07 (dashboard) and UC-08 (data lineage) have no predecessors
other than UC-01/UC-04/UC-06.
