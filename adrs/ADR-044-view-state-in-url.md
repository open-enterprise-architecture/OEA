# ADR-044: View State of List and Table Views in the URL

**Status**: accepted
**Date**: 2026-10-22
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Solution Architect, Frontend Engineer
**Informed**: all contributors

## Context and Problem

With the walking-skeleton change [us-049-show-catalog-data](../openspec/changes/us-049-show-catalog-data/design.md), the catalog page becomes the first view with user-controlled view state: sort column, sort direction and page. Later stories add filters (REQ-046 AC3), join modes and saved views (REQ-048), and further list and table views follow (navigation tree, dashboards, solution diffs).

If this state lives only in component state, it is lost as soon as the reader leaves the view: after opening another page and navigating back, or after a reload, the catalog starts again at page 1 in the default order. Readers who work through a long catalog page by page, or who compare entries in a sorted list, have to rebuild their view every time. The decision is needed now because the catalog table is the pattern that later views copy.

## Decision Drivers

- **Usability – stable views**: a view must look the same when the reader returns to it; navigating away must not cost the reader their position
- **Predictable back navigation**: "back" leaves a view instead of stepping through every sort click
- **Reload and links**: a reload, and in the web portal a copied link, show the same view
- **One pattern for all list and table views**: later views do not invent their own mechanism
- **No server-side state for transient views**: storing a view permanently is the job of saved views (REQ-048), not of every click

## Considered Options

### Option 1: Component state only

- **Pro**: simplest implementation, no URL handling
- **Con**: the view is lost on back navigation and reload; contradicts the main driver

### Option 2: Keep the component alive (`<KeepAlive>`) or store the state in sessionStorage

- **Pro**: back navigation restores the view without visible URL parameters
- **Con**: lost on reload (`<KeepAlive>`) or in a new window or tab (sessionStorage); a view cannot be linked; memory and cache invalidation of kept-alive pages have to be managed per view

### Option 3: View state in the URL query, written with `router.replace`

- **Pro**: back navigation, reload and links restore the view; the browser (and the router history of the client app) does the work; the state is visible and testable; one parser shared with the API parameters
- **Con**: every view has to parse and validate its query and fall back on invalid values; URLs become longer; large state (many filters) needs a compact encoding

### Option 4: Persist the last view per person on the server

- **Pro**: the view survives across devices and sessions
- **Con**: an API call per sort click, a new persistent business object with privacy implications, and overlap with saved views (REQ-048)

## Decision

We choose **Option 3: View state in the URL query, written with `router.replace`**.

**Rationale**: it is the only option that restores the view on back navigation *and* reload without server-side state, which serves the usability driver directly. Option 4 overlaps with saved views and is out of proportion for transient state.

Rules for list and table views:

1. The view state that a reader controls (sort, page, later filters and join modes) is part of the URL query. The parameter names and meaning follow the API parameters of the view (e.g. `sort=name:desc`, `page` from 0, ADR-013).
2. Default values are omitted from the URL; a view without query parameters shows its default.
3. Changes of the view state use `router.replace`, not `router.push`, so that they create no history entry.
4. On opening, the view reads its state from the URL. Invalid values (wrong format, unknown column, a value the API rejects with 400, a page beyond the last one) fall back to the default and are removed from the URL with `replace`.
5. Settings that are not view state, such as the page size, stay out of the URL until a story requires them.
6. Once saved views exist (REQ-048), a URL with view state takes precedence over the default saved view; a URL without view state applies the default saved view.

## Consequences

### Positive Consequences

- Readers keep their sort and page position when they leave a view and come back, and after a reload
- Back navigation leaves the view in one step instead of replaying sort clicks
- In the web portal a link shows exactly the view the sender saw
- Every list and table view follows the same pattern; component tests can set the state through the route

### Negative Consequences / Trade-offs

- Each view needs query parsing, validation and fallback, and tests for them
- The client app (Electron, ADR-009) has no visible address bar: back navigation and reload benefit, but links cannot be shared there
- Large view state (many filters) needs a compact encoding before it is added to the URL; this is decided with the filter story
- REQ-048 (saved views) has to state the precedence of rule 6 when it is implemented

## References

**Concept Chapters**:

- [§12.4 Progressive Disclosure – Three Views for Different Maturity Levels](../concept/30-dynamics/12-domain-views.md)
- [§22.8 Stored Views as Named Queries](../concept/70-platform/22-analyzability.md)

**Related ADRs**:

- [ADR-008](./ADR-008-gui-architecture-dual-track.md) (client app and web portal), [ADR-009](./ADR-009-client-app-framework.md) (client app framework), [ADR-011](./ADR-011-frontend-framework.md) (Vue 3, Vue Router), [ADR-013](./ADR-013-api-style.md) (paging and sort parameters of the contract)

**Use Cases / Requirements**:

- [UC-06: Create, Configure and Use an Architecture Catalog](../requirements/use-cases/UC-06-create-and-use-catalog.md)
- [US-049: Display and Sort Catalog Data](../requirements/user-stories/US-049-show-catalog-data.md)
- [REQ-046: Execute Catalog Query](../requirements/req/REQ-046-catalog-query.md), [REQ-048: Create a SavedView and apply it on open](../requirements/req/REQ-048-saved-view.md)

## Notes

Owner decision of 2026-10-22: going back to a view restores the previous view; the view should stay stable to increase usability. First applied in design decision D8 of the change us-049-show-catalog-data.
