# US-154: Decide Whether a Metamodel Change Applies to Active Solutions

**ID**: US-154
**Story Points**: 8
**Status**: backlog
**Sprint**: S14

## Story

As Kurt – Lead Enterprise Architect, I want to be asked, for every active solution a metamodel change affects, whether the change applies there too, so that a solution in progress can keep a property it still needs and its values are not lost unnoticed.

## Reference

**Use Case**: [UC-04: Configure the Metamodel](../use-cases/UC-04-configure-metamodel.md); [UC-05](../use-cases/UC-05-describe-architecture-vision.md) (solutions in project mode)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirements**: [REQ-032: EntityType GUI configuration](../req/REQ-032-entitytype-gui-configuration.md) (AC4), [REQ-037: Architecture metamodel extension](../req/REQ-037-architecture-metamodel-extension.md)

## Acceptance Criteria

**AC1** (Asked per affected solution):

- Given: the active solution "CRM Replacement" has a delta with a value of "Cost Center" for an application
- When: Kurt removes the mapping of "Cost Center" from "Application Component", deletes one of its enumeration values, or changes a base type so that the property is no longer inherited
- Then: OEA lists "CRM Replacement" and asks whether the change applies there too; the change is made only once every affected solution has an answer

**AC2** (Apply):

- Given: Kurt answers "yes" for "CRM Replacement"
- Then: the values of the property are removed from its deltas, and the metatypes of its extension lose the property as the instance metatypes do

**AC3** (Keep a snapshot):

- Given: Kurt answers "no" for "CRM Replacement"
- Then: the solution keeps the property in its own metamodel as a snapshot; its deltas keep their values, and new deltas may set it, until the go-live

**AC4** (Merge at the go-live):

- Given: "CRM Replacement" kept "Cost Center" as a snapshot and its deltas have values of it
- When: Michael takes it live
- Then: the merge dialog lists each such value; a value Michael does not resolve becomes an open issue that blocks its entity after the go-live until it is resolved

**AC5** (Unaffected solutions):

- Given: no active solution uses the metatype or has a value of the property
- When: Kurt removes the mapping
- Then: OEA does not ask, and the change is made as before

## Technical Notes

- Owner decisions 2026-11-17; the plateaus of S14 change 3 get the same question for plateaus with their own metamodel or changes
- Active solution: status not `implemented` and not `archived`
- API: `deletePropertyMapping`, `deleteEnumValue` and `updateMetatype` take the decisions; without them, 409 `solution-decision-required` lists the affected solutions

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration where applicable)
