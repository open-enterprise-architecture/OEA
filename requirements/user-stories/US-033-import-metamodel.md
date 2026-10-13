# US-033: Import Metamodel from File

**ID**: US-033
**Story Points**: 5
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want to be able to import a prepared metamodel from a YAML or JSON file, so that I do not have to enter every entity type manually via form and can directly adopt my organization's existing metamodel definitions.

## Reference

**Use Case**: [UC-04: Configure the Metamodel Collaboratively](../use-cases/UC-04-configure-metamodel.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-033: Import Metamodel from File](../req/REQ-033-metamodel-import.md)

## Acceptance Criteria

**AC1**:
- Given: a valid YAML file with 5 custom EntityTypes
- When: Kurt uploads the file and confirms after preview
- Then: all 5 types are in the MetamodelConfiguration; the audit log shows all 5 as `imported` with the same batch ID

**AC2**:
- Given: the file contains a type with the name `ApplicationComponent` (built-in)
- Then: the preview shows this entry in red as "not importable (built-in)"; import not possible without resolution

**AC3**:
- Given: a syntactically faulty YAML file
- Then: an error message with line and column appears immediately; no preview

**AC4**:
- Given: a file with 3 new types and 1 conflict (overwritable custom type)
- When: Kurt confirms only the 3 new ones
- Then: only these are imported; the conflict remains untouched

## Technical Notes

- Affected components: import UI component (file upload + diff preview), backend `POST /admin/metamodel/import` + `POST /admin/metamodel/import/confirm`
- Database changes: same as US-032 (same target table)
- Batch UUID is generated at import confirm and used for all audit entries

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests: valid YAML, valid JSON, syntax error, built-in conflict, partial import
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-032 (backend schema must exist, since import populates the same schema)
- Blocks: none

## Notes

5 SP because of the preview component (diff rendering), the two-phase API (parse → confirm) and the partial import logic (checkbox per entry). YAML and JSON are auto-detected. ArchiMate XMI is explicitly out of scope.
