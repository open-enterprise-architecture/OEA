# metamodel/history-visibility Specification

## Purpose

Defines the history visibility per authorization role and metatype: whether a change to a field the reader may not read appears as restricted or is left out of the change history, who maintains it, its limits and audit, and the section "Change history" on the page "Property visibility".

## Requirements

### Requirement: History visibility per role and metatype

Each pair of authorization role and metatype SHALL have the history visibility `show-restricted` (default) or `hide`, which decides whether a field the reader may not read is shown as restricted or left out of the history of an entity of that metatype. Neither mode SHALL show its value. With several roles, the field SHALL be hidden only when every role of the reader is set to `hide`.

Trace: REQ-098, REQ-160, owner decision 2026-11-14

#### Scenario: Nothing configured

- **WHEN** no history visibility is configured
- **THEN** every combination of role and metatype reads as `show-restricted`

#### Scenario: More open setting wins

- **WHEN** the role `viewer` is set to `hide` and the role `auditor` is not configured for `Application Component`, and a person holds both roles
- **THEN** a restricted field in the history of an application component is shown as restricted to this person

#### Scenario: All roles hide

- **WHEN** both `viewer` and `auditor` are set to `hide` for `Application Component`, and a person holds both roles
- **THEN** the restricted field is left out of the history for this person

### Requirement: Scope of a history visibility setting

A combination that is not configured SHALL count as `show-restricted`. A metatype SHALL have its own setting; settings SHALL NOT be inherited along base types. A package re-import SHALL keep the settings. Deleting a role SHALL remove its settings.

Trace: REQ-098, ADR-049, owner decision 2026-11-14

#### Scenario: No inheritance

- **WHEN** `viewer` is set to `hide` for `Application Component`, and `Business Application` is a subtype of it
- **THEN** the history of a business application shows restricted fields to a viewer as restricted

#### Scenario: Re-import keeps the settings

- **WHEN** `viewer` is set to `hide` for the imported metatype `Application Component`, and its package is imported again
- **THEN** the setting is still `hide`

### Requirement: Reading and changing the history visibility

Holders of `visibility:configure` SHALL read all configured entries and change one or several at once.

- **Change:** setting an entry to `show-restricted` SHALL remove it.
- **Atomicity:** a change with an invalid entry (unknown role, metatype or mode) SHALL change nothing, be answered with 422 and name the entry in `invalidFields`.
- **Permission:** others SHALL be rejected with 403. `metamodel:write` SHALL NOT include the permission.

Trace: REQ-161 AC2, ADR-032 SoD-2, owner decision 2026-11-14

#### Scenario: Hide for a role

- **WHEN** a security administrator sets `viewer` to `hide` for `Application Component`
- **THEN** reading the history visibility lists this entry

#### Scenario: Back to the default

- **WHEN** the security administrator sets the same entry to `show-restricted`
- **THEN** the entry is no longer listed, and the combination reads as `show-restricted`

#### Scenario: Unknown role among valid entries

- **WHEN** a change sets two entries, the second one for the role `no-such-role`
- **THEN** the change is rejected with 422 naming the second entry, and neither entry is stored

#### Scenario: Metamodel administrator

- **WHEN** a metamodel administrator without `visibility:configure` changes the history visibility
- **THEN** the request is rejected with 403, and nothing changes

### Requirement: Size of a history visibility change

A change SHALL hold 1 to 500 entries; an empty change or one with 501 entries SHALL be answered with 400 and SHALL store nothing.

Trace: REQ-161, owner decision 2026-11-14

#### Scenario: Entry limits

- **WHEN** a change holds 500 entries, 501 entries or none
- **THEN** 500 are accepted, and 501 and an empty change are rejected with 400

### Requirement: Audit of history visibility changes

Every changed entry SHALL write the audit event `visibility.history-changed` with the role, the metatype and the old and new mode. An entry whose mode does not change SHALL write no event. An event SHALL be flagged as self-benefiting when it sets a role the acting person holds to `show-restricted`.

Trace: REQ-161, ADR-032 SoD-6, ADR-033

#### Scenario: Change audited

- **WHEN** a security administrator sets `viewer` to `hide` for `Application Component`
- **THEN** an event `visibility.history-changed` names `viewer`, `Application Component`, the old mode `show-restricted` and the new mode `hide`

#### Scenario: Unchanged entry

- **WHEN** the security administrator sets an entry to the mode it already has
- **THEN** no event is written

#### Scenario: Self-benefiting change

- **WHEN** a security administrator who holds `auditor` sets `auditor` from `hide` back to `show-restricted`
- **THEN** the event is flagged as self-benefiting

### Requirement: Section "Change history" on the page "Property visibility"

The page SCR-032 SHALL have a section "Change history" for holders of `visibility:configure`. After choosing a metatype, it SHALL list every authorization role with the choice "Restricted" or "Hidden" and save the changed roles together. It SHALL explain that values are never shown and that the more open setting of several roles applies.

Trace: REQ-161, owner decision 2026-11-14

#### Scenario: Set a role to hidden

- **WHEN** the security administrator chooses `Application Component`, sets `viewer` to "Hidden" and saves
- **THEN** the section shows `viewer` as "Hidden" after reloading the page

#### Scenario: Default in the section

- **WHEN** the security administrator chooses a metatype without configured entries
- **THEN** every role shows "Restricted"
