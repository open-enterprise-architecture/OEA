# US-157: Take the Solutions of a Plateau Live with It

**ID**: US-157
**Story Points**: 8
**Status**: backlog
**Sprint**: S14

## Story

As Kurt – Lead Enterprise Architect, I want the go-live of a plateau to take all active solutions that target it live in the same transaction, so that the new baseline contains exactly the changes planned for it.

## Reference

**Use Case**: [UC-11: Define Architecture Plateau and Put It into Production](../use-cases/UC-11-define-plateau-and-go-live.md) (basic flow); [UC-05](../use-cases/UC-05-describe-architecture-vision.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirements**: [REQ-088: Go-live as atomic transition](../req/REQ-088-go-live-atomic-transition.md), [REQ-040](../req/REQ-040-capture-entity-deltas.md)

## Acceptance Criteria

**AC1** (Solutions taken live):

- Given: the active solutions "CRM Replacement" and "ERP Extension" target "Target 2027", which succeeds the baseline
- When: Kurt takes "Target 2027" live
- Then: the deltas of both solutions are in the catalog, both solutions are `implemented`, "Target 2027" is the baseline and the old baseline is realized

**AC2** (Field conflicts stop the go-live):

- Given: a field that "ERP Extension" changes was changed in the catalog after the capture, or by "CRM Replacement" before it
- When: Kurt takes "Target 2027" live
- Then: nothing changes; the merge dialog lists the conflict with the current value, the value of the solution and an own value

**AC3** (Only with its plateau):

- Given: "CRM Replacement" targets "Target 2027"
- When: Michael takes "CRM Replacement" live on its own
- Then: the go-live is refused; the solution goes live with its plateau

**AC4** (Other plateaus follow the new baseline):

- Given: "Target 2028" succeeds the old baseline as well
- When: "Target 2027" goes live
- Then: "Target 2028" succeeds "Target 2027", the new baseline, and its solutions start from it

## Technical Notes

- Owner decisions 2026-11-17: the plateau go-live takes the active solutions of the plateau live; only a plateau whose predecessor is the baseline can go live; other plateaus that succeeded the old baseline follow the new one
- The solutions go live in the order of their last change, each as in US-152 (merge, open issues, soft deletion)

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration where applicable)
