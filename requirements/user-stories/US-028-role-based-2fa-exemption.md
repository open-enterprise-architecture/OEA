# US-028: Configure 2FA Exemption for Non-Privileged Roles

**ID**: US-028
**Story Points**: 2
**Status**: done
**Sprint**: S5
**Implementation**: OpenSpec change `us-031-second-factor-enforcement`, commit 3023eb0 (#145) – field `exemptRoles` of `GET/PUT /admin/settings/second-factor-policy`

## Story

As an operator, I want to be able to define which OEA roles are exempt from the instance-wide 2FA requirement, so that selected low-risk roles get access even without a set-up second factor and I am not forced to enforce 2FA for everyone or for nobody.

## Reference

**Use Case**: [UC-01: Regular Login](../use-cases/UC-01-login.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-029: Role-Based 2FA Exemption](../req/REQ-029-role-based-2fa-exemption.md)

## Acceptance Criteria

**AC1**:

- Given: Max has configured `enforce2FA=true` and `twoFactorExemptRoles=['viewer']`
- When: a person whose only role is `viewer` logs in without a 2FA credential
- Then: they get direct access without a required action

**AC2**:

- Given: the same configuration
- When: a person with any role not in the exemption list (including a person holding `viewer` and another role) logs in without a 2FA credential
- Then: UC-03 required action is triggered

**AC3**:

- Given: Max tries to enter a non-existent role ID, or a role with an `admin`-class permission, as an exemption
- When: he saves
- Then: a validation error appears; the configuration is not saved

## Technical Notes

- Affected components: admin configuration module, login flow (UC-01/UC-03 required action check)
- API endpoints: `GET/PUT /admin/config/auth` (extended with `twoFactorExemptRoles`)
- Database changes: no new table; new field in `instance_config`

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (exempt role, non-exempt role, invalid role ID)
- [ ] Documentation updated
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-031 (2FA enforcement REQ-020 must be implemented so that the exemption can take effect); OEA role model must exist
- Blocks: none

## Notes

Realigned by OpenSpec change `us-031-second-factor-enforcement`: `twoFactorExemptRoles` is the field `exemptRoles` of the second-factor policy. A person is exempt only if they hold at least one role through a local role assignment and every such role is exempt; a person without roles is not exempt. Unknown roles and roles with an admin-class permission are rejected with 422 (AC3).

Default: `twoFactorExemptRoles=[]` – no difference from the previous REQ-020 behavior. Max enables the exemption only if he deliberately wants it. 2026-10-13 (security review SEC-10, REQ-029 v0.2.0): admin roles cannot be exempted, a person is exempt only if all roles are exempt, and the system admin account needs a second factor after its first bootstrap login (REQ-171).
