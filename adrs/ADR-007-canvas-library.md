# ADR-007: Canvas Library for Interactive Diagram Editing

**Status**: accepted
**Date**: 2026-06-26
**Deciders**: [Rigobert – Product Owner](../business-analysis/stakeholders/SH-09-rigobert-product-owner.md) (SH-09)
**Consulted**: Requirements Engineer (UC-05, US-045, REQ-040)
**Informed**: –
**Updated**: 2026-06-26 — React Flow → **Vue Flow** following ADR-011 (frontend framework: Vue 3)  
**Updated**: 2026-06-28 — remaining React artifacts cleaned up; EPL 2.0 license terms for ELK.js explicitly documented; ADR-008 status corrected to accepted

> **Previously deferred** (until ADR-008 was decided): [ADR-008](./ADR-008-gui-architecture-dual-track.md) (accepted) determines that the interactive diagram canvas runs in the **client app**. This removes the reason for the blocker.

> **Update 2026-06-26**: [ADR-011](./ADR-011-frontend-framework.md) decides the frontend framework as **Vue 3 + TypeScript**. The original choice of React Flow is therefore replaced by **Vue Flow** (`@vue-flow/core`, MIT) — a direct port of React Flow for Vue 3 with identical concepts (nodes, edges, composables instead of hooks).

## Context and Problem

UC-05 (describe the architecture vision of a change initiative) requires that solution architects can place and name entities directly in an **interactive diagram** (canvas). US-045 specifies a special behavioral requirement: when entering the name of a new entity on the canvas, a suggestion list with existing components from the IT landscape must appear, so that the architect immediately sees whether a component already exists and should create a `modified` or `retiring` delta instead of a `new` delta.

This requires a **web canvas library** that:
- Makes nodes (entities) and edges (relations) interactively placeable and editable
- Supports in-place text editing directly on the canvas element (no separate dialog)
- Integrates into a Vue 3/TypeScript web application
- Serializes state as clean JSON (Git-friendly diffs, no binary format)

§21.2.1 of the concept deliberately postponed the visualization strategy until after requirements engineering. This ADR addresses only the **interactive editing canvas** (core requirement for UC-05 / Walking Skeleton), not the entire visualization strategy (rendering backends for read-only diagrams, notation support, etc.) – that remains ADR-008.

## Decision Drivers

- **OSS compatibility**: OEA is licensed under Apache 2.0; the canvas library must have a compatible open-source license (MIT, Apache 2.0, BSD)
- **Vue 3/TypeScript-native**: the frontend is developed as a Vue 3 application (ADR-011); the library should fit naturally into this ecosystem
- **In-place editing**: name input directly on the canvas node (US-045 AC1–AC3)
- **Serializability**: diagram state as diffable JSON; no proprietary binary format (concern SH-04: Git-native workflow)
- **Custom node shapes**: different entity types (ApplicationComponent, Interface, BusinessProcess …) need different visual representations
- **Active community / maintenance**: library must be maintained long-term; no dead forks
- **License costs**: no per-seat or per-deployment fees (OSS principle)

## Considered Options

### Option 1: Vue Flow (`@vue-flow/core`) ✓ CHOSEN

Vue Flow is a direct port of React Flow for Vue 3. MIT-licensed. Nodes are Vue 3 components.

- **Pro**:
  - Best Vue 3/TypeScript integration; nodes are plain Vue components (SFCs)
  - Same concepts as React Flow (nodes, edges, handles, MiniMap, controls) — React Flow documentation is transferable
  - `useVueFlow()` composable instead of React hooks — idiomatic Vue 3
  - In-place editing easy to implement (input element in node SFC)
  - State (nodes, edges) as JSON array; diff-friendly
  - MIT license, no restrictions
  - Actively maintained; GitHub: `bcakmakoglu/vue-flow`
- **Con**:
  - Smaller community than React Flow (but similar API → React Flow resources usable)
  - Originally optimized for flow/DAG diagrams; EA-specific layout must be built in-house
  - Auto-layout (hierarchical, layered) needs an external library (ELK.js, dagre)
  - Performance optimizations may be needed for very large graphs (>1000 nodes)

### Option 2: maxGraph (community fork of mxGraph)

maxGraph is the Apache 2.0-licensed community fork of mxGraph, on which draw.io / diagrams.net is based. A very powerful, battle-tested library with decades of EA diagram support.

- **Pro**:
  - Extremely feature-rich: auto-layout, predefined EA shape libraries, groups, swimlanes
  - Battle-tested in draw.io (millions of users)
  - Apache 2.0 license
  - Well suited for complex architectural layouts (layered, orthogonal routing)
- **Con**:
  - No native React integration; requires a wrapper layer
  - API is conceptually older (mxGraph legacy from the early 2000s); TypeScript types incomplete
  - Smaller community than React Flow; documentation more patchy
  - Steeper learning curve; in-place editing is configurable, but not React-native
  - maxGraph is a young fork (since 2022); project stability still being established

### Option 3: AntV X6

AntV X6 is an Apache 2.0-licensed graph editor library from Ant Group (Alibaba). Feature-rich, good React integration, designed for interactive diagram editing.

- **Pro**:
  - Apache 2.0 license
  - Strong graph editing features (including in-place edit, custom shapes, ports)
  - Good React integration (@antv/x6-react-shape)
  - Actively maintained, professional backing by Ant Group
- **Con**:
  - Primary documentation and community in Chinese; English documentation patchy
  - Less widespread in the Western open-source ecosystem; less StackOverflow/GitHub issues support
  - API introduced breaking changes between v1 and v2; long-term stability harder to assess
  - TypeScript types complete, but API ergonomics less consistent than React Flow

### Option 4: GoJS

Commercial canvas library with professional EA diagram support.

- **Pro**:
  - Very powerful, mature EA/UML support
  - Excellent documentation
- **Con**:
  - Commercial license (per-developer fee); incompatible with an Apache 2.0 OSS project
  - Ruled out

## Decision

We choose **Option 1: Vue Flow** (`@vue-flow/core`), because it offers the best integration into the Vue 3/TypeScript stack (ADR-011), in-place editing is trivially realizable through Vue components as nodes, state is available as a diffable JSON array, and the concepts were adopted 1:1 from React Flow (minimal porting effort, documentation transferable).

The EA-specific gaps (auto-layout, ArchiMate shape library) are known, solvable problems:
- Auto-layout will be retrofitted via **ELK.js** (Eclipse Layout Kernel, EPL 2.0; usable via bundling in an Apache 2.0-compatible way) as soon as the requirement becomes concrete
- Custom shapes (ApplicationComponent, Interface, BusinessProcess, etc.) are defined as Vue SFCs directly in the OEA frontend — no third-party shape set needed

GoJS is ruled out due to its commercial license. maxGraph and AntV X6 are technically suitable, but have no native Vue 3 integration. Vue Flow is the most natural choice for the present stack.

## Consequences

### Positive Consequences

- US-045 (create new entity in diagram) is implementable: typeahead suggestion list and in-place edit are directly realizable via Vue node SFCs
- Developers do not have to learn a new abstraction layer — Vue Flow is Vue 3
- Diagram state (nodes, edges, positions) is storable as JSON in the database and git-diff-friendly

### Negative Consequences / Trade-offs

- Auto-layout for large EA diagrams (landscape map (Bebauungsplan) view) is not available out of the box; ELK.js integration is a separate task when this view is built
- Vue Flow is not built specifically for ArchiMate or TOGAF notation; all domain-specific shapes must be maintained in the OEA frontend itself
- Performance with very large graphs (> 500 visible nodes) may require custom virtualization optimizations; not a problem for the Walking Skeleton

### Follow-up Decisions

- **ADR-008** (accepted): Full visualization strategy – which diagram types (ArchiMate layer views, C4, sequence, BPMN) does OEA render, and with which backends (text-based vs. canvas)? Vue Flow is the canvas building block for the client app (§21.2.1)
- **ELK.js integration**: when hierarchical auto-layout is needed for landscape map views; to be specified as a separate requirement
- **Shape library**: ArchiMate-compliant node shapes (ApplicationComponent, Interface, BusinessProcess, DataObject …) as Vue SFCs – design task for the UI designer (SH-UI-Designer agent)

## References

**Concept Chapters**:
- [§21.2.1 Visualization Strategy](../concept/70-platform/21-api-architecture.md)

**Related ADRs**:
- [ADR-008](./ADR-008-gui-architecture-dual-track.md) (accepted): client app + web portal; Vue Flow runs in the client app context (Electron)

**Use Cases / Requirements**:
- [UC-05: Describe the Architecture Vision of a Change Initiative](../requirements/use-cases/UC-05-describe-architecture-vision.md)
- [REQ-040: Capture EntityDeltas of a Solution](../requirements/req/REQ-040-capture-entity-deltas.md)
- [US-045: Create New Entity in Diagram as Delta](../requirements/user-stories/US-045-delta-new-entity-diagram.md)

## Notes

Vue Flow: `@vue-flow/core` (MIT). GitHub: `bcakmakoglu/vue-flow`. Requires Vue 3.x. Check the current stable version before implementation.

### ELK.js – EPL 2.0 License Terms

ELK.js (Eclipse Layout Kernel) is licensed under **EPL 2.0** (Eclipse Public License 2.0). EPL 2.0 is a weak copyleft: it applies only to the EPL-licensed code itself, not to the entire project. The following terms apply to OEA:

| Term | OEA situation | Measure |
|---|---|---|
| **Source code availability** | ELK.js is included unmodified as an npm package | No action required: upstream repo (`eclipse/elk`) is public; a link in the NOTICE file suffices |
| **Copyleft contagion** | EPL 2.0 is a "weak copyleft" — applies only on modification or direct redistribution of the EPL file | OEA does not modify ELK.js → no copyleft on OEA's own code |
| **NOTICE file obligation** | Distribution of the Electron bundle (`.dmg`, `.exe`, `.AppImage`) contains ELK.js | NOTICE file must contain: `ELK.js (Eclipse Layout Kernel) – Eclipse Public License 2.0 – https://github.com/eclipse/elk` |
| **Compatibility with Apache 2.0** | EPL 2.0 contains a "Secondary License" clause that allows combination with Apache 2.0 in the same product | No conflict; OEA project license (Apache 2.0) remains unaffected |

**Conclusion**: ELK.js is usable in OEA under EPL 2.0. The only obligation upon distribution: NOTICE file entry. If ELK.js is replaced at a later point by an MIT/Apache 2.0 alternative (e.g. `elkjs` wrapper with a different build), the condition no longer applies.
