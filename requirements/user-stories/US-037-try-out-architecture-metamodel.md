# US-037: Try Out a New Metamodel in a Solution

**ID**: US-037
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Solution Architect, I want to be able to create my own editable metamodel extension object for a specific solution, so that I can try out new entity types for this initiative without changing the locked instance default metamodel.

## Reference

**Use Case**: [UC-04: Configure the Metamodel Collaboratively](../use-cases/UC-04-configure-metamodel.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-037: Solution-Specific Metamodel Extension](../req/REQ-037-architecture-metamodel-extension.md)

## Acceptance Criteria

**AC1**:

- Given: instance metamodel with `editMode=import-only`; solution "Cloud Migration 2027" exists (baseline P0 → target P1)
- When: Kurt creates an extension configuration with `editMode=gui-and-import` for this solution and defines `CloudService`
- Then: `CloudService` is available only in "Cloud Migration 2027"; the instance metamodel remains untouched and locked

**AC2**:

- Given: solution "Cloud Migration 2027" with extension
- When: an architect creates a new entity in this solution
- Then: both groups are available in the type selection: standard (from instance) and solution-specific; `CloudService` appears with a badge as a solution type

**AC3**:

- Given: Kurt tries to define a type `ApplicationComponent` (instance type) in the solution extension
- Then: an error appears; no entry is created

**AC4**:

- Given: solution extension with `editMode=gui-and-import`
- When: another architect (with permission) also creates types in the extension
- Then: these appear in the extension; the audit log attributes the changes to the correct person

## Technical Notes

- Affected components: solution management UI, metamodel configuration module (`scope=solution` path), entity creation dialog (effective metamodel)
- API endpoints:
  - `POST /solutions/{id}/metamodel` – create extension configuration
  - `GET /solutions/{id}/effective-metamodel` – union of instance + solution
  - `POST /solutions/{id}/metamodel/import` – import for solution scope
- Database: `scope`, `parent_id`, `solution_id` columns in `metamodel_configurations` table

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Tests: create extension; effective metamodel (union); name collision; `editMode` independent of instance
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-032 (EntityType form – reused for solution scope); US-035 (lock mode – motivation); solution management UC (solutions must exist)
- Blocks: future US for entity creation in solutions (must use effective metamodel)

## Notes

5 SP because of: `scope` column + `solution_id` in the DB schema; effective metamodel API (union + cache invalidation); UI extension (grouped type selection with origin badge). Solution management itself (creating/editing solutions incl. plateau assignment) is a separate UC and US.
