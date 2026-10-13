# US-125: Visibility Badge in the Metamodel Editor and Conditional Fields

**ID**: US-125
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As Kurt – Lead Enterprise Architect, I want to see at a glance which properties of an EntityType are restricted and to be able to adjust the restriction directly in the editor, so that I keep an overview of sensitive fields.

## Reference

**Use Case**: [UC-21](../use-cases/UC-21-configure-property-visibility.md)
**Persona**: Kurt – Lead Enterprise Architect (SH-03)
**Requirements**: [REQ-125](../req/REQ-125-property-visibility-configuration-form.md)

## Acceptance Criteria

**AC1** (Badge in property list):

- When: a property has `visibilityMode ≠ public`
- Then: shows a "Restricted" badge (icon or label) in the property list row

**AC2** (Conditional fields):

- When: mode selector changed
- Then: only relevant helper fields visible; unneeded ones hidden (no layout shift)

**AC3** (Inline validation):

- When: saving with an empty mandatory field
- Then: inline error hint appears; no server request sent

## Definition of Done

- [ ] AC1–AC3 fulfilled
- [ ] Tests present
- [ ] Code review; linter green; entered in trace matrix
