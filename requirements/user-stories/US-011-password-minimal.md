# US-011: Username/Password Login Without a Second Factor (Minimal Variant)

**ID**: US-011
**Story Points**: 3
**Status**: done
**Sprint**: S5
**Implementation**: OpenSpec change `us-011-local-password` – setting `oea.auth.person-password.enabled` (default off), password login of persons with their e-mail address through `localLogin`

## Story

As an operator of a simple self-hosting instance, I want to be able to deliberately enable the minimal variant of username/password without a second factor, so that I can offer local access even without passkey or TOTP infrastructure.

## Reference

**Use Case**: [UC-01: Login](../use-cases/UC-01-login.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-011: Username/Password Login Without a Second Factor](../req/REQ-011-username-password-minimal.md)

## Acceptance Criteria

**AC1**:

- Given: an instance on which this variant has been explicitly enabled, and a person with the correct password
- When: they sign in
- Then: they receive a session according to their active role assignments

**AC2**:

- Given: a freshly installed, unconfigured OEA instance
- When: its authentication setup is checked
- Then: this variant is disabled by default

**AC3**:

- Given: every login via this variant
- When: the audit log entry is created
- Then: it is recognizable that no second factor was used

## Technical Notes

- Affected components: auth module, instance configuration
- Affected EntityTypes/Relations: password hash (shared with US-010)
- API endpoints: login endpoint (single-step)
- Database changes: configuration flag for enabling this variant

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person (with focus on security, as the weakest mechanism)
- [ ] Tests written (incl. default configuration of a fresh installation)
- [ ] Documentation updated (incl. security warning in the setup wizard)
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-001, US-006, US-023 (password enrollment must exist)
- Blocks: none

## Notes

Realigned by OpenSpec change `us-011-local-password`: "enabled at instance level" is the operator setting `oea.auth.person-password.enabled`, not the planned settings API; persons log in with the e-mail address of the person record (system admin login names contain no `@`). A password session is never second-factor-verified, so privileged roles stay ineffective until TOTP or passkeys for persons exist.

Enabling must be a deliberate configuration decision accompanied by a warning, not hidden in a config file (see REQ-011, Implementation Notes).
