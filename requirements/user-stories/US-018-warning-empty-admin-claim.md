# US-018: Warning for Empty or Non-Existent Remote Admin Claim

**ID**: US-018
**Story Points**: 3
**Status**: done
**Sprint**: S3
**Implementation**: OpenSpec change `us-019-admin-safeguards`, commit 065c58b (#117) – warning `group-not-seen` and `lastMatchedAt` on IdP group mappings

## Story

As an operator, I want to be warned when configuring a remote bootstrapping mapping if the specified group is empty or does not exist, so that I do not accidentally lock myself out of the instance.

## Reference

**Use Case**: [UC-02: System Admin Bootstrapping](../use-cases/UC-02-system-admin-bootstrapping.md)
**Persona**: [SH-06: Max – Operator in an SME](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirement**: [REQ-018: Warning for Empty Remote Admin Claim](../req/REQ-018-warning-empty-admin-claim.md)

## Acceptance Criteria

**AC1**:

- Given: a remote mapping to a group that is empty or non-existent at configuration time
- When: Max saves the configuration
- Then: he receives a warning that this mapping currently grants access to nobody

**AC2**:

- Given: the same situation
- When: Max acknowledges the warning
- Then: he can still save the configuration (no hard block)

## Technical Notes

- Affected components: configuration UI/CLI for remote mapping, IdP API client
- Affected EntityTypes/Relations: `SystemAdminAccount` with `mode: remote`
- API endpoints: configuration endpoint for claim mapping (validation call)
- Database changes: no additional ones

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (test IdP with empty/non-existent group)
- [ ] Documentation updated
- [ ] ADR created if architecture decision
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting on: US-014
- Blocks: none

## Notes

AC1 realigned by OpenSpec change `us-019-admin-safeguards` (owner decision 2026-10-28): "empty or non-existent" means "no login at this instance has presented the group yet" – OEA uses no provider API. AC2 holds: the mapping is saved and the response carries the warning.

The check is best-effort (dependent on provider API access, e.g. Microsoft Graph), not a mandatory hard block.
