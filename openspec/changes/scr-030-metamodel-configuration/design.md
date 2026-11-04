# Design

## Context

S7 changes 1–3 built the metamodel API:

- metatypes with base types, property definitions with enum values, categories and property mappings (`us-032-metatype-editing`);
- dated mapping entries and organization entries on package mappings (`us-137-mapping-effective-date`, ADR-049);
- translated labels with the bundle and the label store in the portal (`us-140-dynamic-labels`).

The portal so far has a start page, the profile and the catalogs (SCR-020, SCR-028). Its state lives in `reactive` modules, it uses Nuxt UI 4 components, and its texts come from vue-i18n in English and German. SCR-030 exists only as a legacy SVG mockup of the client app. The mockup shows a metatype list on the left, a detail on the right with tabs, and buttons for import, export and validation.

The metamodel tables have no version column. Writes today follow last-write-wins without any notice.

## Goals / Non-Goals

**Goals:**

- One page in the web portal on which metamodel administrators maintain everything the metamodel API offers, and on which readers see the metamodel.
- Detect concurrent changes to the same element. Warn the administrator and let them decide, as UC-04 A5 describes.
- Keep existing API clients working.

**Non-Goals:**

- Relations, stereotypes, constraint rules, the upload of a package file and the export.
- The client app (Electron).
- Live updates of an open configuration page when someone else changes the metamodel. The event stream follows in its own change, and until then the collision warning catches stale writes.

## Decisions

### D1 – A version per element, not per configuration

`metatypes`, `property_definitions` and `property_categories` each get `version INTEGER NOT NULL DEFAULT 1 CHECK (version >= 1)` (Flyway V18). Every successful write on an element increments it in the same statement (`SET version = version + 1`). This includes the update by a package re-import.

Sub-resources bump their owner:

- A mapping write, mapping entry or re-import setting increments the version of the metatype the mapping belongs to.
- Adding or deleting an enum value increments the version of the property definition.

A change to a base type's mappings does not bump its subtypes. The subtype shows the inherited mappings, but a collision there is caught on the base type itself.

Alternatives considered:

- One version for the whole metamodel configuration. It is simpler, but two administrators who edit different metatypes would block each other. That contradicts A5 ("the same EntityType").
- `updated_at` as the version. Timestamps can coincide within a transaction, and the client would have to send them back exactly. An integer is unambiguous.

### D2 – ETag and If-Match on the existing operations

- **Version in the response.** `MetatypeDetail`, `PropertyDefinitionDetail` and `PropertyCategory` get a read-only `version`, so the lists carry the version of each element without one request per element. `getMetatype` and every write that returns or changes a single element send `ETag: "<version>"` (a strong ETag, since the version is exact).
- **Optional `If-Match`.** All writes listed in the proposal accept an optional `If-Match` with that ETag. Without the header the write proceeds (compatible with scripts, agents and the package path).
- **Stale version.** If the header is present and does not name the current version, the write is rejected with 412, `type: urn:oea:problem:stale-version` and the extension `currentVersion`, and nothing changes. A header that is not one quoted integer counts as not matching. `*` matches any existing element.
- **No race.** The check and the write run in the existing transaction. The check reads the row `FOR UPDATE`, so two concurrent writes with the same version cannot both succeed.
- **Order of checks.** The order is 404, then 412, then the rules of the element (409 read-only, 409 in-use, 422). A stale client therefore learns about the newer state first, before it gets a rule message that may no longer apply.

`428 Precondition Required` was considered and rejected: it would break every existing client, and the owner asked only for the warning.

### D3 – Page structure and routes

- **Routes.** `/metamodel` opens the section `metatypes`. The sections are reachable as `/metamodel/{section}` (`metatypes`, `properties`, `categories`, `translations`, `settings`), and a metatype as `/metamodel/metatypes/{id}`. The routes keep the selection on reload and allow links from the manual.
- **Metatypes section.** A list on the left with a search field over name and abbreviation, the scope badge and the source package. On the right is the detail:
  - header with name, abbreviation, scope and base type;
  - actions (edit, delete) only for organization metatypes;
  - the properties table: own and inherited mappings, with necessity, valid-from, state, scope and "inherited from", and the dated entries of a mapping as expandable rows;
  - the metatype's re-import setting with the effective value.

  Below the breakpoint `lg`, the list fills the width, and selecting a metatype shows the detail with a back link.
- **Other sections.**
  - Properties: a table with data type, category, constraints and enum values, with dialogs to create and edit; enum values are added and deleted in the edit dialog.
  - Categories: a table with dialogs.
  - Translations: language select, filter, and a table of key, corporate name and translation input, saved together.
  - Settings: the global re-import setting.
- **Components.** The page is `MetamodelPage.vue`. The dialogs are components in `src/components/metamodel/` (`MetatypeDialog`, `PropertyDialog`, `CategoryDialog`, `MappingDialog`). They are built on `UModal` and `UForm` like the existing catalog dialog.
- **Header entry.** It appears for `entity:read` or `metamodel:write`. Without either permission, the page shows the same "access missing" state as the catalogs.

### D4 – Client state and saving

- **Loading.** A module `src/metamodel/store.ts` holds the metatypes, property definitions and categories of the instance. It loads them on entering the page and reloads them after every successful write. A write may change other elements too (a mapping changes the inherited mappings of subtypes, a deleted value changes definitions), so reloading the three lists is simpler and always correct. The lists are small: tens of metatypes and a few hundred definitions.
- **Writing.** Each write sends `If-Match` with the version of the element as loaded and the CSRF token. Each API function returns `{ ok: true, value } | { ok: false, rejection }` like `importStarterPackage`.
- **On 412.** The dialog stays open with the input. It shows a warning and the current state of the element, reloaded with `getMetatype` or from the lists, and offers two actions:
  - "Overwrite" sends the input again with the new version;
  - "Discard" closes the dialog and shows the current state.

  For a delete, the warning shows the current state and asks again.
- **Other rejections.** 409 and 422 are shown in the dialog. Each problem type has its own message (`name-taken`, `read-only-metatype`, `in-use` with `count`, `conflict`), and `invalidFields` are shown at the fields.
- **After saving translations.** The label store reloads the bundle of the current language (`loadLabels`), so the portal shows the new labels without a reload.

### D5 – Labels of the configuration itself

- **Display names.** The lists show the translated label of the user's language, as the catalog pages do. The detail and the dialogs also show the name in the corporate language, since only that is edited in the metatype dialog.
- **Translations section.** It reads `getMetamodelLabels` for the chosen language and saves only changed keys. Clearing a field sends `null`.

### D6 – Story, screen images and manual

- **Story.** `SCR-030-metamodel-configuration.stories.ts` renders `MetamodelPage` with `routedFetch` data from the starter package plus one organization metatype. The first story shows the administrator view with the detail of an organization metatype. Further stories show the read-only reader view, a 412 warning and the narrow layout.
- **Screen inventory.** SCR-030 becomes `CA+WP` and `implemented`, and the legacy SVGs are deleted.
- **Manual.** The page "Editing the metamodel" gets a section per part of the screen in English and German. The walkthrough logs in as metamodel administrator, creates `SecurityZone` with `trustLevel`, and takes screenshots in both languages. The curl examples move to a short section "Scripting the metamodel".

## Risks / Trade-offs

- **The optional `If-Match` protects only clients that send it.** A script without the header still overwrites silently. This is accepted for compatibility (owner decision), and the manual recommends the header for scripts.
- **A re-import increments versions.** An administrator who has a dialog open during a re-import of the same package gets a 412. That is correct, because the element did change.
- **Reloading all lists after each write** costs three requests. Measured against the size of a metamodel, this is negligible. If it ever matters, a write response could return the affected elements.
- **The page is large.** Splitting it into section components and dialog components keeps each file reviewable. Each dialog gets its own Vitest spec.

## Migration Plan

V18 adds the columns with `DEFAULT 1`, so existing rows start at version 1. The change needs no data migration and no API break. A rollback drops the columns. Clients that never send `If-Match` notice no difference.
