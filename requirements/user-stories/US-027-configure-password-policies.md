# US-027: Configure Password Policies as Operator

**ID**: US-027
**Story Points**: 3
**Status**: backlog
**Sprint**: unassigned

## Story

As an operator, I want to define instance-wide which character types passwords must contain, how long they are valid and how many previous passwords may not be reused, so that my organization's password policy is enforced in OEA.

## Reference

**Use Case**: [UC-03: Set Up Authentication Method](../use-cases/UC-03-set-up-authentication-method.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-028: Configure Password Policies by Operator](../req/REQ-028-password-policies.md)

## Acceptance Criteria

**AC1**:

- Given: Max opens the instance configuration
- When: he enables `requireUppercase`, `requireDigits`, `requireSpecialChars` and sets `minLength=16`
- Then: these rules are enforced from now on for every password set or change

**AC2**:

- Given: `expiryDays=90` is configured
- When: a person logs in after 91 days
- Then: they are prompted to change their password before they gain access

**AC3**:

- Given: `historyCount=5` is configured
- When: a person sets a password that was one of the last 5
- Then: it is rejected with an understandable error message

**AC4**:

- Given: Max tries to enable `minLength=2` with all four character types
- When: he wants to save
- Then: a validation error appears; the configuration is not saved

## Technical Notes

- Affected components: admin configuration module, auth module (validation logic reads policies), login flow (expiry check)
- Affected EntityTypes/Relations: `instance_config` table or YAML config (fields: `password_min_length`, `password_require_uppercase`, `password_require_lowercase`, `password_require_digits`, `password_require_special_chars`, `password_expiry_days`, `password_history_count`)
- API endpoints: `GET/PUT /admin/config/password-policy`
- Database changes: new table `password_history` (`person_id`, `password_hash`, `created_at`) for history check

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (all rule combinations; expiry simulation; history check; rejection of inconsistency)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-023 (setting a password must exist so that policy checking takes effect), US-011 (login flow for expiry check)
- Blocks: US-026 (generator reads policies)

## Notes

All rules have sensible defaults (off / 0) so that OEA can be operated without explicit configuration. Max only enables what his organization actually mandates. The validation logic is a shared function between admin configuration, setting a password (REQ-024), generator (REQ-027) and the future password change UC – no duplicate code.
