# US-054: Place a Catalog in the Navigation Tree

**ID**: US-054
**Story Points**: 2
**Status**: backlog
**Sprint**: unassigned

## Story

As a Lead Enterprise Architect, I want to be able to place a catalog in a folder of the navigation tree, so that all users can find it contextually – e.g. "IT Landscape > Application Inventory" – and open it directly.

## Reference

**Use Case**: [UC-06: Create, Configure and Use an Architecture Catalog](../use-cases/UC-06-create-and-use-catalog.md)
**Persona**: [SH-03: Kurt – Lead Enterprise Architect](../../business-analysis/stakeholders/SH-03-kurt-lead-enterprise-architect.md)
**Requirement**: [REQ-050: Place a Catalog in the Navigation Tree](../req/REQ-050-catalog-navigation-tree.md)

## Acceptance Criteria

**AC1** (attach to tree, happy path):
- Given: Catalog "Application Inventory" and folder "IT Landscape" in the navigation tree exist
- When: Kurt chooses "Attach to navigation tree" in the catalog context menu and selects folder "IT Landscape"
- Then: Catalog appears under "IT Landscape" in the navigation tree; TreeNodeItem created

**AC2** (open via tree):
- Given: Catalog is attached in the tree under "IT Landscape"
- When: Franz clicks the catalog entry in the navigation tree
- Then: Catalog opens directly; default SavedView is applied (if present)

**AC3** (multiple placement – soft reference):
- When: Kurt additionally attaches "Application Inventory" in "Compliance/ISO 27001 View"
- Then: Catalog appears in both folders; only one Catalog record exists

**AC4** (removing from tree does not delete the catalog):
- When: Kurt removes the entry from "IT Landscape" (context menu "Remove from tree")
- Then: TreeNodeItem deleted; catalog in "Compliance/ISO 27001 View" remains; `GET /api/v1/catalogs/{id}` returns HTTP 200

**AC5** (display label in tree):
- When: Kurt attaches the catalog with displayLabel "System Inventory"
- Then: "System Inventory" appears in the navigation tree; the catalog itself is still called "Application Inventory"

## Technical Notes

- Affected components: navigation tree component, catalog context menu, backend `POST /api/v1/tree-nodes/{nodeId}/items` + `DELETE /api/v1/tree-nodes/{nodeId}/items/{itemId}`
- UX: context menu on the catalog (right-click or kebab menu): "Attach to navigation tree" → folder selection dialog (tree picker); alternatively: drag and drop into the navigation tree
- Dependency: US-046; TreeNode management (separate US, still TBD)

## Definition of Done

- [ ] Acceptance criteria AC1–AC5 met
- [ ] Code review by a second person
- [ ] Tests: placement, open via tree, soft reference (multiple), removal without catalog deletion, displayLabel
- [ ] Linter and type checks green
- [ ] Entered in trace matrix
