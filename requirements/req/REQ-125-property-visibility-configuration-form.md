---
id: REQ-125
title: Configuration Form for Property Visibility in the Metamodel Editor
type: functional
priority: must
status: proposed
version: 0.2.0
created: 2026-06-28
last_updated: 2026-10-13
author: requirements-engineer
references:
  use_cases:
    - UC-21
  business_objects:
    - metamodel-configuration
    - role
  stakeholders:
    - SH-03
  adrs: []
supersedes: []
superseded_by: []
---

# REQ-125: Configuration Form for Property Visibility in the Metamodel Editor

## Statement

The metamodel editor MUST provide a configuration form for visibility per `PropertyDefinition`. The form MUST:

1. Display a **mode selector** (dropdown or radio group) for `visibilityMode` with the three options `Public`, `Role-based`, `Connection-based`
2. When `role-restricted` is chosen: show two **role selections** (multi-select from all configured roles) for `readRoles` and `writeRoles`
3. When `connection-scoped` is chosen: show a **connection type selection** (single-select from all `isConnection=true` types) for `scopingConnectionType`
4. Visually highlight the currently set mode (e.g. badge or icon on the property entry in the list)

Fields not needed (`readRoles`/`writeRoles` for `public`, `scopingConnectionType` for `role-restricted`) MUST be hidden. The visibility section MUST be editable only for users holding `visibility:configure` (security administration, REQ-157, REQ-161); for other metamodel editors it MUST be read-only.

## Rationale

Without a dedicated configuration form, security administrators would have to manage visibility via YAML import — this is error-prone and excludes non-technical users.

Security review SEC-03 / owner decision Q4 (2026-10-13): metamodel administration and security administration are separate roles; a metamodel editor must not be able to lift a property restriction.

## Acceptance Criteria

**AC1** (Mode selector visible):

- When: Kurt opens a property in the metamodel editor
- Then: the visibility section is present; the current mode is preselected (`public` for new properties)

**AC2** (Conditional fields):

- When: the mode selector is set to `Role-based`
- Then: the role selection appears; the `scopingConnectionType` field is hidden

**AC3** (Badge):

- When: a property has `visibilityMode ≠ public`
- Then: the property entry in the list shows a "Restricted" badge or a corresponding icon

**AC4** (Save validation in the UI):

- When: Kurt clicks "Save" with empty `readRoles` for `role-restricted`
- Then: an inline error message appears; no server request is sent

**AC5** (Security administration only):

- Given: a user with metamodel administration permission but without `visibility:configure`
- When: they open a property in the metamodel editor
- Then: the visibility section is shown read-only; a direct API request to change it is rejected with 403 (REQ-161)

## Dependencies

- **Prerequisites**: REQ-124 (data model), REQ-032 (metamodel editor available), REQ-157 (permission model), REQ-161 (governance of visibility configuration)
- **Consequences**: –

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-28 | requirements-engineer | Initial draft |
| 0.2.0 | 2026-10-13 | requirements-engineer | Security review SEC-03 / owner decision Q4: separate read/write role selections; visibility section editable only with `visibility:configure` (AC5) |
