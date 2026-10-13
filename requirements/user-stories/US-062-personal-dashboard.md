# US-062: Create and Protect a Personal Dashboard

**ID**: US-062
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As a Solution Architect, I want to create a personal dashboard for my own analysis that is visible only to me – so that I can try out and refine my own evaluations without affecting the public dashboards.

## Reference

**Use Case**: [UC-07](../use-cases/UC-07-create-and-use-dashboard.md)
**Persona**: Michael – Solution Architect (SH-04); applies to all stakeholders
**Requirements**: [REQ-051](../req/REQ-051-create-dashboard.md), [REQ-056](../req/REQ-056-dashboard-access-visibility.md)

## Acceptance Criteria

**AC1** (create personal dashboard without special permission):
- Given: Michael is logged in (role Solution Architect; no dashboard write permission)
- When: Michael creates a dashboard with scope=personal
- Then: HTTP 201; dashboard created

**AC2** (not in other users' list):
- Given: Michael has a scope=personal dashboard
- When: Kurt, Franz or the CIO call GET /api/v1/dashboards
- Then: Michael's dashboard appears in none of these lists

**AC3** (direct access via ID blocked):
- Given: Third parties know Michael's dashboard ID (e.g. by guessing)
- When: Direct GET /api/v1/dashboards/{id} or /data without permission
- Then: HTTP 403

**AC4** (scope upgrade with permission):
- Given: Michael subsequently receives the dashboard write permission
- When: PUT with scope=instance
- Then: HTTP 200; dashboard is visible to everyone from now on

**AC5** (scope=instance rejected without permission):
- Given: Michael has no dashboard write permission
- When: PUT with scope=instance
- Then: HTTP 403

## Technical Notes

- No separate permission for scope=personal; every logged-in user may create personal dashboards
- `scope=personal` dashboards do not appear in `GET /api/v1/dashboards` of other users (server-side filtering by `createdBy = currentUser`)
- Admins can see all personal dashboards (for support/moderation)

## Definition of Done

- [ ] AC1–AC5 met
- [ ] Tests: create without permission, not in others' list, 403 direct access, upgrade with/without permission
- [ ] Code review
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
