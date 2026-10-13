# US-028: Configure 2FA Exemption for Admin Roles

**ID**: US-028
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As an operator, I want to be able to define which OEA roles are exempt from the instance-wide 2FA requirement, so that administrators get access even without a set-up second factor and I am not forced to enforce 2FA for everyone or for nobody.

## Reference

**Use Case**: [UC-01: Regular Login](../use-cases/UC-01-login.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-029: Role-Based 2FA Exemption](../req/REQ-029-role-based-2fa-exemption.md)

## Acceptance Criteria

**AC1**:
- Given: Max has configured `enforce2FA=true` and `twoFactorExemptRoles=['administrator']`
- When: a person with the admin role logs in without a 2FA credential
- Then: they get direct access without a required action

**AC2**:
- Given: the same configuration
- When: a person without the admin role logs in without a 2FA credential
- Then: UC-03 required action is triggered

**AC3**:
- Given: Max tries to enter a non-existent role ID as an exemption
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

- Waiting on: US-020 (2FA enforcement REQ-020 must be implemented so that the exemption can take effect); OEA role model must exist
- Blocks: none

## Notes

Default: `twoFactorExemptRoles=[]` – no difference from the previous REQ-020 behavior. Max enables the exemption only if he deliberately wants it. The system admin account (UC-02) is always exempt, independent of this.
