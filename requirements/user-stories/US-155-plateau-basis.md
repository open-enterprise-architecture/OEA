# US-155: Choose the Basis of a Plateau

**ID**: US-155
**Story Points**: 8
**Status**: done
**Sprint**: S14
**Implementation**: OpenSpec change `us-084-plateaus` – the basis `catalog` or `predecessor` of a plateau, its content computed on read, the baseline of the solutions of a plateau with the lifecycle of each entry, before values from the basis (AC1–AC4)

## Story

As Kurt – Lead Enterprise Architect, I want to choose, when I create a target or transition plateau, whether it builds on the current catalog or on the planned content of its predecessor plateau, so that the solutions of a later plateau can work on the state the earlier plateau will reach.

## Reference

**Use Case**: [UC-11: Define Architecture Plateau and Put It into Production](../use-cases/UC-11-define-plateau-and-go-live.md) (A2, A3)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirements**: [REQ-084: Create plateau](../req/REQ-084-create-plateau.md), [REQ-039](../req/REQ-039-landscape-baseline.md)

## Acceptance Criteria

**AC1** (Basis "catalog"):

- Given: the target plateau "Target 2027" succeeds the baseline and has the basis "catalog"
- When: Michael opens the baseline of a solution that targets "Target 2027"
- Then: he sees the current catalog

**AC2** (Basis "predecessor"):

- Given: the solution "CRM Replacement" targets "Target 2027" and retires "Legacy CRM", and the target plateau "Target 2028" succeeds "Target 2027" with the basis "predecessor"
- When: Michael opens the baseline of a solution that targets "Target 2028"
- Then: he sees the catalog with the changes of the active solutions of "Target 2027": "Legacy CRM" is marked as retiring, modified values show their planned value, and new entities of "Target 2027" are listed as proposed

**AC3** (Change the basis):

- Given: "Target 2028" is not realized
- When: Kurt changes its basis from "predecessor" to "catalog"
- Then: the baseline of its solutions is the current catalog from then on

**AC4** (Basis needs a predecessor):

- When: Kurt creates a target plateau with the basis "predecessor" and without a predecessor
- Then: the creation is rejected with 422 naming `succeeds`; no plateau is created

## Technical Notes

- Owner decision 2026-11-17: the basis is chosen when the plateau is created, "catalog" or "predecessor"
- The content of a plateau is its basis with the deltas of the active solutions that target it; a predecessor with the basis "predecessor" builds on its own predecessor in turn
- The baseline plateau has no basis; its content is the current catalog

## Definition of Done

- [x] Acceptance criteria met
- [ ] Code review by a second person
- [x] Tests written (unit, integration where applicable)
