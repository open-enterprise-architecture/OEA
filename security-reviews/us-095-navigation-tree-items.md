# Security Review: us-095-navigation-tree-items

| Field | Value |
|---|---|
| Review type | Step 2 of the change workflow: security aspects added to the OpenSpec change before implementation |
| Reviewer | Security Engineer (agent) |
| Date | 2026-11-17 |
| Branch / commit | `feature/s15-navigation-tree-and-viewpoint-notations` @ `534f1629` |
| Scope | `openspec/changes/us-095-navigation-tree-items/` (proposal, design, tasks, delta spec `navigation/navigation-tree`), main spec `navigation/navigation-tree`, `NavigationTree.java`, `NavigationTreeController.java`, the navigation and solution operations of `api/openapi.yaml`, `docs/audit-event-catalog.md`, ADR-032 |
| Method | Threat analysis of the new operations and the extended entry types against the existing code; no code was run |
| Result | Additions made to the change; no open Critical or High risk once the added tasks are implemented. Sign-off of the specification granted; code review after implementation on request |

## Threats Considered

| ID | Threat | Severity without the addition | Addition |
|---|---|---|---|
| NT-01 | Two concurrent opposite folder moves both pass the cycle check and form a cycle; the recursive CTE `descendants` and the tree read then loop, and the tree is corrupt | High | Requirement "Consistent tree under concurrent changes"; design D7 (moves under `NavigationTreeStore.lock()`, CTE bounded by `MAX_DEPTH + 1`); task 2.3 (two-thread test) |
| NT-02 | A move bypasses the depth limit (10) and the entry limit (200) that `createTreeNode` and `addTreeNodeItem` enforce, growing the tree without bound | Medium | Requirement "Limits of placing and moving" with both boundaries; design D7; task 2.3 |
| NT-03 | A custom role with `viewpoint:manage` but without `entity:read` places arbitrary entity IDs and reads their names from the tree in the write response | Low | Requirement "Who places and moves"; design D7 and D4 (`permissions: [viewpoint:manage, entity:read]`); tasks 1.1, 2.3 |
| NT-04 | An entry is moved or removed through the path of another folder, or another person's personal catalog entry is addressed by its ID, confirming its existence | Low | Requirement "Consistent tree under concurrent changes" (404 for all of these); design D7 |
| NT-05 | Display labels (free text, may hold personal data) copied into the append-only audit trail; `tree-node.item-removed` of an entity entry fails because `referenceId` is null | Low | Requirement "Audit of moves and placements" extended; design D7; tasks 2.3, 4.1 |
| NT-06 | Stored XSS: entity names written by any holder of `entity:write` are now shown in the sidebar on every page, administrators included; the CSP `script-src 'self'` limits but does not remove HTML injection | Low | Requirement "Names in the sidebar shown as text"; design D7; task 3.2 |
| NT-07 | A drop of text from another window triggers a move with IDs taken from `dataTransfer` | Low | Same requirement (drops only from inside the tree); task 3.2 |

Checked without an addition:

- Deleted entities stay visible by name in the tree. Their history is readable with `entity:read` (`getEntityHistory`), so nothing new is disclosed.
- Solutions are readable by every holder of `entity:read` (`listSolutions`), the permission of the tree read.
- Rate limiting: the global limit of 600 requests per minute and principal (`RateLimitFilter`) covers the new writes; each write is bounded by the limits above.
- No `If-Match` on the tree: writes are serialized; the last of two moves wins, which is acceptable for folders.

## Points for the Solution Architect (consistency)

- The proposal names the paths `PUT /navigation/tree/{id}/position` and `PUT /navigation/tree/{id}/items/{itemId}/position`, while the existing operations live under `/tree-nodes/{nodeId}`. The added requirements do not depend on the path.
- Requiring `entity:read` in addition to `viewpoint:manage` also applies to placing catalogs through `addTreeNodeItem`. The built-in roles are not affected (ADR-032 role matrix).
- The position is declared 0–10000 in the contract; the added scenarios expect 422 naming `position` (mapped by `@ContractViolationResponse`, as for the other tree writes). If the contract answers 400 instead, adjust the scenario.
- The position counts among which entries? With another person's personal catalog in a folder, the caller sees fewer entries than the store holds. Counting among the visible entries matches what the client sends. Gaps in `sortOrder` reveal the number of hidden entries, which is accepted as negligible.

## Check of the Step-3 Decisions (2026-11-17)

The architect's decisions were checked against the findings above. None of them weakens a finding.

| Decision | Effect on the findings |
|---|---|
| All seven tree writes (`createTreeNode`, `renameTreeNode`, `deleteTreeNode`, `addTreeNodeItem`, `removeTreeNodeItem`, `moveTreeNode`, `moveTreeNodeItem`) need `viewpoint:manage` and `entity:read`; the UI offers the actions only with both | Strengthens NT-03. Every write returns the whole tree, so the condition now holds for all of them, not only for placing and moving. The built-in role `enterprise-architect` holds both permissions, so no built-in user loses a function |
| A position outside 0–10000 answers 422 naming `position` (`@ContractViolationResponse(status = 422)` on both move operations) | Neutral. Both boundaries stay rejected before any change; NT-02 stays covered |
| The position counts the entries the caller sees; hidden entries keep their relative order | Neutral to positive. Positions no longer depend on entries the caller cannot see, and NT-04 (404 for hidden entries) is unchanged. Gaps in `sortOrder` can still hint at the number of another person's personal catalog entries in a folder; this stays accepted as negligible (no name, no ID) |
| Paths under `/tree-nodes/{nodeId}`; a taken name, an unknown parent and a duplicate target are named by the target folder field (`parentId`, `nodeId`) | Neutral. Folders are visible to every reader, so naming the target folder discloses nothing; the path point above is closed |

## Business Questions for the Owner

- None.

## Sign-off

**Granted** for the specification. The step-3 decisions keep every finding covered. The code review before the merge checks tasks 2.3 and 3.2.
