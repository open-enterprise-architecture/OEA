# US-147: Grant Access Through an IdP Group Mapping

**ID**: US-147
**Story Points**: 5
**Status**: in-progress
**Sprint**: S1
**Implementation**: OpenSpec change `us-013-system-admin-login`, commit 1fafd95 (#31) – AC1, AC4 and AC7 implemented through the admin API; OpenSpec change `us-001-oidc-login` – AC2, AC3, AC5 and AC6 implemented, AC7 for derived roles of existing sessions (the 409 rule for the last path to `system-admin` follows with US-146); commit hash follows when the change is archived

## Story

As Max – Operator, I want to map an immutable IdP group identifier to an authorization role, so that access follows group membership in our identity provider and removal from the group withdraws access within 15 minutes without manual maintenance in OEA.

## Reference

**Use Case**: [UC-23: Manage Roles and Role Assignments](../use-cases/UC-23-manage-roles-and-role-assignments.md) (A1)
**Persona**: [SH-06: Max – Operator](../../business-analysis/stakeholders/SH-06-max-operator-sme.md)
**Requirements**: [REQ-157](../req/REQ-157-permission-model.md), [REQ-167](../req/REQ-167-immutable-admin-claim-mapping.md), [REQ-165](../req/REQ-165-session-timeouts-token-storage.md), [REQ-175](../req/REQ-175-security-event-logging.md)

## Acceptance Criteria

**AC1** (Mapping created):

- Given: Max holds `role:assign`, an Entra ID provider is configured, and Max is not a member of the group
- When: he creates a mapping of the Entra group object ID `6f1c…` to the role `viewer`
- Then: the mapping is stored with issuer, group identifier, role, `createdBy` and `createdAt`, and an audit event `idp-mapping.created` exists

**AC2** (Role derived at login):

- Given: the mapping of AC1
- When: a member of the group logs in with an ID token from the configured issuer
- Then: the person holds `viewer` with source `idp`, and the derived roles are recorded with the session

**AC3** (Fail closed):

- Given: the mapping of AC1
- When: the same group identifier arrives from a different issuer, or the token contains an Entra overage indicator instead of the groups claim
- Then: no role is derived from the mapping, and a warning event is written

**AC4** (Display names rejected):

- Given: Max creates a mapping
- When: he enters a display name such as "EA-Viewers" instead of an immutable identifier in the provider's format
- Then: the request is rejected with 422 (REQ-167)

**AC5** (Own group rejected):

- Given: the group `6f1c…` is contained in Max's own current token
- When: Max creates or changes a mapping for this group
- Then: the response is 403 with reason `self-administration` (SoD-1)

**AC6** (Removal in the IdP within 15 minutes):

- Given: a person holding `viewer` only through the mapping and an active session
- When: the person is removed from the group in the IdP at time T
- Then: requests after T + 15 min are evaluated without `viewer` (REQ-165 AC5)

**AC7** (Mapping deleted in OEA):

- Given: an active mapping
- When: Max deletes or deactivates it
- Then: affected sessions lose the derived role at the next request, at the latest after 60 s; deleting the last path to `system-admin` is rejected with 409 (US-146); an audit event `idp-mapping.deleted` exists

**AC8** (Service accounts excluded):

- Given: a service account
- When: tokens of the service account are evaluated
- Then: no role is derived from IdP group mappings (SoD-4)

## Technical Notes

- Affected components: authorization module, OIDC login and token refresh, session store
- Affected EntityTypes/Relations: `idp_group_mappings` (ADR-032 DM-5), session-recorded derived roles (DM-10)
- API endpoints: `GET/POST /admin/idp-group-mappings`, `PUT/DELETE /admin/idp-group-mappings/{mappingId}` (ADR-032 API-4)
- Database changes: new table `idp_group_mappings`

## Definition of Done

- [ ] Acceptance criteria met
- [ ] Code review by a second person
- [ ] Tests written (unit, integration with IdP stub; controlled clock for AC6)
- [ ] Documentation updated (identifier formats per provider)
- [ ] Linter and type checks green
- [ ] Entered in trace matrix

## Dependencies

- Waiting for: US-143, US-001 (OIDC login), US-014 (remote bootstrapping; the remote system admin mapping becomes an IdP group mapping to `system-admin`)
- Blocks: –

## Notes

Not part of the walking skeleton; planned for S3–S8 per UC-23 Implementation Notes.
