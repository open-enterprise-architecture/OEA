# solution/plateau-go-live Specification

## Purpose

Defines the go-live of a plateau in plateau mode: its preview with the solutions and warnings, the confirmation by name, the transition that takes the active solutions of the plateau live and makes it the new baseline while the old baseline is realized, the effects on other plateaus, the permission, the audit, and the dialog SCR-066.

## Requirements

### Requirement: Preview of a plateau go-live

A principal with `plateau:go-live` SHALL read the preview of the go-live of a plateau: the old and the new baseline, the active solutions that go live with their status and counts, the counts summed over them, the field conflicts per solution, the open issues the go-live will leave, and the warnings. The preview SHALL change nothing.

Trace: US-086, US-087 AC1, US-157, UC-11 step 4

#### Scenario: Preview

- **GIVEN** "Target 2027" succeeds the baseline, and the active solutions "CRM Replacement" (status `draft`) and "ERP Extension" (status `approved`) target it
- **WHEN** Kurt reads the preview of "Target 2027"
- **THEN** it names the baseline as realized and "Target 2027" as the new baseline, lists both solutions with their counts, and warns that one solution is not yet approved

#### Scenario: No warnings

- **GIVEN** the only solution of "Target 2027" is `approved` and leaves no open issue
- **WHEN** Kurt reads the preview
- **THEN** it carries no warning

#### Scenario: Without plateau:go-live

- **WHEN** Michael, who holds only `solution-architect`, reads the preview
- **THEN** the response is 403

### Requirement: Only the next plateau goes live

Only a target or transition plateau whose predecessor is the current baseline SHALL go live. Any other plateau SHALL be refused with 409 `plateau-not-next`, and nothing SHALL change.

Trace: US-157, owner decision 2026-11-17

#### Scenario: Next plateau

- **GIVEN** "Target 2027" succeeds the baseline
- **WHEN** Kurt takes "Target 2027" live with its name
- **THEN** the response is 200

#### Scenario: Later plateau

- **GIVEN** "Target 2028" succeeds "Target 2027"
- **WHEN** Kurt takes "Target 2028" live
- **THEN** the response is 409 `plateau-not-next`, and no plateau or solution changes

### Requirement: Confirmation by name

The go-live SHALL carry the exact name of the plateau. Another name SHALL be refused with 422 naming `name`, and nothing SHALL change.

Trace: US-086 AC2, AC3, REQ-086, UC-11 E2

#### Scenario: Exact name

- **WHEN** Kurt takes "Target 2027" live with the name "Target 2027"
- **THEN** the go-live runs

#### Scenario: Wrong name

- **WHEN** Kurt takes "Target 2027" live with the name "target 2027"
- **THEN** the response is 422 naming `name`, and nothing changes

### Requirement: Solutions go live with their plateau

The go-live SHALL take every active solution of the plateau live in the order of the last change of its deltas, oldest first, then by name, each as a single solution goes live: deltas into the catalog, soft deletion of retired entities, open issues for metamodel violations, and the status `implemented`. Implemented and archived solutions SHALL be left as they are.

Trace: US-157 AC1, owner decision 2026-11-17

#### Scenario: Two solutions

- **GIVEN** "CRM Replacement" retires "Legacy CRM" and "ERP Extension" renames "ERP-Core" to "ERP-Core 2", both targeting "Target 2027"
- **WHEN** Kurt takes "Target 2027" live
- **THEN** "Legacy CRM" is deleted, "ERP-Core" is named "ERP-Core 2", and both solutions are `implemented`

#### Scenario: Archived solution

- **GIVEN** an archived solution targets "Target 2027"
- **WHEN** Kurt takes "Target 2027" live
- **THEN** its deltas are not applied and its status stays `archived`

### Requirement: Field conflicts stop a plateau go-live

A field of a delta `modified` whose value at its turn differs from the recorded before value SHALL be a field conflict. The value at its turn SHALL be the catalog value with the solutions applied that go live before it. While a conflict is unresolved, the go-live SHALL answer 409 `merge-required`, listing each conflict with its solution, and nothing SHALL change.

Trace: US-157 AC2, US-152 AC2

#### Scenario: Changed in the catalog

- **GIVEN** the description of "ERP-Core" was changed in the catalog after "ERP Extension" captured its change
- **WHEN** Kurt takes "Target 2027" live
- **THEN** the response is 409 `merge-required` naming the field and "ERP Extension", and no solution went live

#### Scenario: Changed by an earlier solution

- **GIVEN** "CRM Replacement" and "ERP Extension" both change the description of "ERP-Core" from "ERP", and "CRM Replacement" goes first
- **WHEN** Kurt takes "Target 2027" live
- **THEN** the response is 409 `merge-required` naming the delta of "ERP Extension" with the current value of "CRM Replacement"

#### Scenario: Conflict resolved

- **GIVEN** the conflict of "ERP Extension" was resolved with the value of the solution
- **WHEN** Kurt takes "Target 2027" live
- **THEN** the go-live runs, and "ERP-Core" has the description of "ERP Extension"

### Requirement: Effects on plateaus

The go-live SHALL make the plateau the baseline with `validFrom` the day of the go-live (UTC), and the old baseline realized with `validTo` the same day. Every other plateau that succeeded the old baseline SHALL succeed the new one, and the solutions that started from the old baseline SHALL start from the new one.

Trace: US-088 AC2, US-157 AC4, REQ-088, UC-11 step 9

#### Scenario: New baseline

- **WHEN** Kurt takes "Target 2027" live on 2026-11-17
- **THEN** "Target 2027" is the baseline from 2026-11-17, the old baseline is realized until 2026-11-17, and editing it gives 422 "Realized plateau is read-only"

#### Scenario: Sibling plateau follows

- **GIVEN** "Phase 1" succeeded the old baseline and "Website Relaunch" targets it
- **WHEN** "Target 2027" goes live
- **THEN** "Phase 1" succeeds "Target 2027", and "Website Relaunch" starts from "Target 2027"

### Requirement: Atomic plateau go-live

The plateau go-live SHALL run in one transaction. A failure at any step SHALL leave every plateau, solution, entity and open issue as before.

Trace: US-088 AC1, REQ-088, UC-11 E3

#### Scenario: Failure after the solutions

- **GIVEN** the update of the old baseline fails
- **WHEN** Kurt takes "Target 2027" live
- **THEN** the response is an error, both plateaus keep their status, the solutions are not `implemented`, and the catalog is unchanged

### Requirement: Permission for a plateau go-live

The preview and the go-live SHALL need `plateau:go-live`. The go-live SHALL NOT check `solution:write` or the write roles of the values it writes. Without the permission, the response SHALL be 403 and nothing SHALL change.

Trace: UC-11 E4, owner decision 2026-11-16

#### Scenario: Without plateau:go-live

- **GIVEN** a person with `plateau:write` but not `plateau:go-live`
- **WHEN** they take "Target 2027" live
- **THEN** the response is 403, and nothing changes

### Requirement: Audit of a plateau go-live

A plateau go-live SHALL be audited as `plateau.went-live` with the new and the old baseline, the solutions and the summed counts. Each solution SHALL write its own `solution.went-live`, and each plateau that now succeeds the new baseline `plateau.updated` naming `succeeds`.

Trace: UC-11 step 11, ADR-033

#### Scenario: Audited

- **WHEN** "Target 2027" goes live with two solutions, and "Phase 1" succeeded the old baseline
- **THEN** one `plateau.went-live`, two `solution.went-live` and one `plateau.updated` naming `succeeds` are written

### Requirement: Dialog "Take plateau live"

On SCR-065, the plateau that succeeds the baseline SHALL show "Go live" to holders of `plateau:go-live`. It SHALL open the dialog SCR-066 with the summary, the solutions, the warnings, the notice "This action cannot be undone" and an input for the name. "Confirm go-live" SHALL be enabled only for the exact name.

Trace: US-086 AC1–AC3, US-087 AC1, AC3

#### Scenario: Button disabled without the name

- **WHEN** Kurt opens the dialog
- **THEN** "Confirm go-live" is disabled

#### Scenario: Wrong name

- **WHEN** Kurt types "Target 2026"
- **THEN** "Confirm go-live" stays disabled

#### Scenario: Exact name

- **WHEN** Kurt types "Target 2027"
- **THEN** "Confirm go-live" is enabled

#### Scenario: Conflicts in the dialog

- **WHEN** the go-live answers `merge-required`
- **THEN** the dialog lists the conflicts per solution and opens the merge dialog SCR-063 for a solution
