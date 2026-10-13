# US-031: Configure Second Factor as Mandatory Instance-Wide

**ID**: US-031
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As an operator, I want to be able to define instance-wide that all regular persons must present a second factor at login, so that password-only logins are ruled out for my organization.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-020: Instance-Wide Enforcement of a Second Factor](../req/REQ-020-enforce-second-factor.md)

## Acceptance Criteria

**AC1**:

- Given: Max enables `enforce2FA=true` in the instance configuration
- When: a person tries to log in via REQ-011 (password without second factor)
- Then: the login is rejected

**AC2**:

- Given: `enforce2FA=true` is active
- When: a person without a set-up 2FA credential logs in
- Then: UC-03 required action is triggered; they must set up a second factor before they gain access

**AC3**:

- Given: `enforce2FA=true` is active
- When: the system admin account performs its first bootstrap login via UC-02
- Then: it is unaffected by the enforcement for this login only; it must enroll a second factor before any administrative action, and later logins require it (REQ-171)

**AC4**:

- Given: `enforce2FA=false` (default)
- When: a person logs in with a password without a second factor
- Then: this remains permitted (no obligation)

## Technical Notes

- Affected components: admin configuration module, login flow (UC-01/UC-03 required action check)
- API endpoints: `GET/PUT /admin/config/auth` (field `enforce2FA`)
- Database changes: no new table; field in `instance_config`
- Interplay: REQ-029 (role-based exemptions) extends this setting

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (enforce=true blocks REQ-011; required action triggered; system admin exempt; default=false)
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-010 (TOTP login), US-009 (passkey login) – must be available as a second factor before enforcement makes sense
- Blocks: US-028 (role-based exemption – presupposes active enforcement)

## Notes

Default `enforce2FA=false` so that OEA can be operated without configuration. Max enables the enforcement deliberately. The system admin account (UC-02) is exempt only for its first bootstrap login – it exists before the Person/Role system and must not block bootstrapping (changed 2026-10-13 per security review SEC-10, REQ-020 v0.2.0, REQ-171).
