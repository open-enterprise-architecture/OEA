# US-156: Decide a Metamodel Change per Plateau

**ID**: US-156
**Story Points**: 3
**Status**: backlog
**Sprint**: S14

## Story

As Kurt – Lead Enterprise Architect, I want to be asked once per target plateau whether a metamodel change applies to its solutions, so that all solutions working toward the same plateau keep or lose a property together.

## Reference

**Use Case**: [UC-04: Configure the Metamodel](../use-cases/UC-04-configure-metamodel.md); [UC-11](../use-cases/UC-11-define-plateau-and-go-live.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirements**: [REQ-032: EntityType GUI configuration](../req/REQ-032-entitytype-gui-configuration.md) (AC4), [REQ-037: Architecture metamodel extension](../req/REQ-037-architecture-metamodel-extension.md)

## Acceptance Criteria

**AC1** (One question per plateau):

- Given: the active solutions "CRM Replacement" and "ERP Extension" target the plateau "Target 2027", and both have values of "Cost Center"
- When: Kurt removes the mapping of "Cost Center" from "Application Component"
- Then: OEA asks once for "Target 2027", naming both solutions; the answer applies to both

**AC2** (Solutions without a plateau):

- Given: the active solution "Website Relaunch" has no plateau and has a value of "Cost Center"
- When: Kurt removes the mapping
- Then: OEA asks for "Website Relaunch" on its own, as in US-154

**AC3** (Answer for a single solution of a plateau):

- Given: "CRM Replacement" targets "Target 2027"
- When: a client sends a decision for "CRM Replacement" alone
- Then: the change is rejected with 422 naming the solution; nothing changes

## Technical Notes

- Owner decision 2026-11-17: the question of US-154 is asked per plateau for solutions that target a plateau
- The decisions `apply` and `keep` and their effects are those of US-154

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration where applicable)
