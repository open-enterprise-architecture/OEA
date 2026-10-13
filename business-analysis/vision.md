# Vision

## Vision in One Sentence

> Enterprise architecture as a versioned, modular, and tool-open open-source discipline – conceptually as rigorous as necessary, as pragmatic and low-threshold as possible.

## With Explanation

OEA makes an organization's architecture the shared, versioned truth – for architects as a maintenance tool, for business units as an inventory, for compliance as an asset source, for automated pipelines as a verifiable artifact.

Three deliberate design principles shape OEA:

- **Open, text-based representation instead of proprietary formats**, because architecture knowledge must outlive the vendor.
- **Progressive disclosure instead of enforced modeling depth**, because different roles need different views.
- **Modular, interface-oriented architecture instead of a monolith**, because the tool does not have to do everything itself but should fit into an existing tool landscape.

## Target Audience (Primary)

Seven persona profiles describe the primary addressees:
- Junior to lead architects in SMEs, mid-sized companies, and corporate groups (SH-01, SH-03, SH-04)
- Specialized architects for data and business (SH-02, SH-07)
- Operations owners in regulated environments (SH-06)
- Strategic sponsors at C-level (SH-05)

See [stakeholders/](stakeholders/) for detailed profiles.

## Success Criteria

OEA is successful when:
- Stakeholders of different experience levels can work productively with it
- Migrations from existing EA tools (Sparx, Avolution, LeanIX, wiki) are realistically feasible
- Compliance requirements of regulated industries are met without subsequent tooling
- It is perceived as a serious OSS alternative in the EA field

## Non-Goals

- Not a replacement for classic requirements tools (Doors, Jama)
- Not a code generator
- No multi-tenant SaaS offering in the first version
- Not a visualization platform for non-architectural data
- Not a full BPM suite (BPMN modeling as an optional adapter module)

## Design Principles (In Depth)

### Open, Text-Based Representation
Architecture content exists as versioned text artifacts: Markdown, YAML, JSON Schema. Version control is native, diff-friendliness is a given, no proprietary binary format blocks migration.

### Progressive Disclosure
The logical layer is optional, not mandatory. The inventory view is the low-threshold entry point; layer view and landscape map (Bebauungsplan) are only visible where they are needed. Complexity accessible at the push of a button, not imposed.

### Modular, Interface-Oriented Architecture
API-first (OpenAPI 3.1 for REST, AsyncAPI for events). Modules are exchangeable: BPMN integration, GRC adapter, ITSM bridge. The tool is not all-encompassing but integration-capable.
