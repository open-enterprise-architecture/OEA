# ADR-049: Organization Extensions of Package Metamodel Content

**Status**: accepted
**Date**: 2026-10-31
**Deciders**: [Lukas Mathis – Repository Owner](../MAINTAINERS.md)
**Consulted**: Solution Architect, Business Engineer
**Informed**: all contributors

## Context and Problem

ADR-002 keeps the content of metamodel packages (`scope=imported`) and of the software (`scope=built-in`) read-only: it changes only by re-importing the same package, and changes of an organization need a fork. Most metatypes of an instance come from the starter package (ADR-005), so this rule decides whether organizations can adapt the metamodel they actually use.

Two changes of S7 need a decision:

- `us-032-metatype-editing` (US-032): metamodel administrators want their own properties, for example *Cost Center*, on imported metatypes such as *Application Component*. A re-import of the package must then decide what happens to these properties.
- `us-137-mapping-effective-date` (US-137, REQ-148): administrators want to make an imported property such as *Status* mandatory from a certain date, which changes the necessity of a package mapping.

A fork is not implemented. Without a decision, an organization can only use its own metatypes or leave package metatypes as they are. Whether its own properties should survive a package update also depends on the organization's way of working.

## Decision Drivers

- **Adaptability:** organizations must adapt imported metatypes to their needs without giving up the update path of the package (ADR-002).
- **Traceability:** what belongs to the package and what belongs to the organization must stay distinguishable in the data and in the API, so that a re-import replaces exactly the package content.
- **Different ways of working:** some organizations treat a package update as authoritative and want their extensions to be dropped; others want to keep them.
- **No fork yet:** the decision must work without copying metatypes.
- **History:** necessity changes follow the effective-date model of ADR-022 and never overwrite earlier entries.

## Considered Options

### Option 1: Fork only (ADR-002 unchanged)

An organization that wants to change an imported metatype forks it into an `organization` copy and maintains the copy.

- **Pro**: clean separation; package content stays untouched; no rule for re-imports needed.
- **Con**: the fork is not implemented; entities would have to move to the copy; package updates no longer reach the forked type; a single extra property leads to a full copy.

### Option 2: Organization extensions as separate entries

Package content stays read-only, but the organization adds its own entries to package metatypes: mappings of its own property definitions and entries with an effective date that override the necessity of a package mapping. Every mapping entry records its scope. A re-import replaces only the package entries; whether it keeps or removes the organization entries is configurable globally and per metatype, inherited along the base type.

- **Pro**: adaptation without a fork; package updates keep arriving; extensions and package content stay distinguishable by the scope of each entry; the re-import behavior fits both ways of working.
- **Con**: more rules: the scope per entry, a re-import setting at two levels and a same-date rule between package and organization entries; a package update can be hidden by an organization override.

### Option 3: Package content becomes editable

Administrators may change imported metatypes and their mappings directly; a re-import overwrites or merges the changes.

- **Pro**: simplest for administrators; no extra concept.
- **Con**: package and organization content can no longer be told apart; a re-import either destroys the changes or needs a three-way merge; contradicts ADR-002.

## Decision

We choose **Option 2: Organization extensions as separate entries**.

**Rationale**: it lets organizations adapt the metatypes they use while keeping the update path and the separation of ADR-002. Name, abbreviation, base type and the entries of a package stay read-only for administrators. The rules in detail (owner decisions of 2026-10-30 and 2026-10-31):

1. **Own properties on package metatypes:** organization property definitions may be mapped to imported and built-in metatypes (`us-032-metatype-editing`).
2. **Necessity overrides:** organization entries with a valid-from date may override the necessity of a package mapping; the entries of the package stay unchanged (`us-137-mapping-effective-date`).
3. **Scope per entry:** every mapping entry stores its scope (`organization` or the scope of the package content).
4. **Re-import:** a re-import replaces only the package entries. It keeps or removes the organization entries of each re-imported metatype by the effective re-import setting: the metatype's own value (`keep`, `remove`, `inherit`), else that of its base types, else the global value (default `keep`). Removing an entry deletes the values as any removal of a property does.
5. **Same date:** if a package entry and an organization entry have the same valid-from date, the organization entry wins; the import reports the skipped package entry.

## Consequences

### Positive Consequences

- Organizations adapt imported metatypes without a fork and keep receiving package updates.
- The API shows for every mapping whether it belongs to the package or to the organization (`scope`).
- The re-import behavior fits both ways of working and can differ per branch of the base type hierarchy.

### Negative Consequences / Trade-offs

- A re-import has to evaluate the re-import setting and the same-date rule.
- An organization override can hide a later package change of the necessity on the same date.
- With the setting `remove`, a re-import deletes the values of organization properties; administrators must know the setting before they import.
- A future fork must take the organization entries of a package metatype into account.

## References

**Related ADRs**: [ADR-002](./ADR-002-continuum-repository.md) (scopes, refined by this ADR), [ADR-005](./ADR-005-app-vs-tech-default.md) (starter configuration), [ADR-022](./ADR-022-structured-property-model.md) (effective dates)
**Concept**: [§4 Enterprise Continuum & TRM](../concept/10-foundations/04-enterprise-continuum-trm.md)
**Changes**: OpenSpec `us-032-metatype-editing` (archived), `us-137-mapping-effective-date`
