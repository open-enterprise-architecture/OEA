# Proposal

## Why

Metatype names, property labels and enum values exist only in the corporate language, and the web portal shows only English texts. Users who work in another language therefore see the metamodel and the interface in a language they did not choose. US-140 and REQ-152 call for labels that administrators can translate, that the client caches by ETag and that are refreshed after an administrator's change without a page reload. This is S7 change 3 of UC-04. It also prepares SCR-030 (change 4), which will edit these labels.

## Traceability

| Artifact | Reference |
|---|---|
| Use Case | [UC-04: Configure Metamodel](../../../requirements/use-cases/UC-04-configure-metamodel.md) |
| User Stories | [US-140: Reload Dynamic Labels in the Frontend](../../../requirements/user-stories/US-140-load-dynamic-labels.md) |
| Requirements | [REQ-152: Dynamic Labels with ETag Caching](../../../requirements/req/REQ-152-dynamic-labels-etag.md) |
| ADRs | [ADR-025: I18N Strategy](../../../adrs/ADR-025-i18n-strategy.md), [ADR-049: Organization Extensions of Package Metamodel Content](../../../adrs/ADR-049-organization-extensions-of-packages.md), [ADR-045: User Manual](../../../adrs/ADR-045-user-manual.md) |
| Sprint / Milestone | S7 / Milestone 1 ([Sprint-Plan](../../../docs/sprint-plan.md)) |

## What Changes

Owner decisions of 2026-11-04:

- **Translated labels:** a new table `i18n_entries` holds one text per label key and locale (`de`, `en`, `fr`, `it`). Keys follow ADR-025 with the stable identifier, for example `metatype.{uuid}.name`, `property-def.{uuid}.label`, `enum-value.{uuid}.label` and `property-category.{uuid}.name`.
- **Maintenance through the API:** metamodel administrators read and set the labels of a locale (`getMetamodelLabels`, `updateMetamodelLabels`, new). Organization entries may translate any content, including imported and built-in content. On the same key, the organization entry wins over the package entry, as in rule 5 of ADR-049. Changes are audited as `metamodel.labels-changed`.
- **Packages bring their labels:** the import accepts the `i18nBundles` section. It is no longer rejected as unsupported. A re-import replaces only the package entries. The starter package `oea-starter-togaf-classic` 1.1.0 ships German labels.
- **Label bundle with ETag:** `getI18nBundle` (`GET /i18n/{locale}`, so far `x-oea-status: planned`) returns all labels of a locale to every authenticated principal. When a key has no translation, the bundle contains the name in the corporate language. The response carries an `ETag` and `Cache-Control: max-age=300, stale-while-revalidate=3600`. A matching `If-None-Match` is answered with 304. The ETag is a hash of the bundle content, so a deleted entry or a renamed metatype also changes it. This refines the `MAX(updated_at)` rule of ADR-025 (see design).
- **Language of the user:** `GET /me` returns `uiLanguage`, and `updateMyPreferences` (`PUT /me/preferences`, new) lets every person set it on the profile page. No value means the language of the browser. Existing rows only carry the column default `de`, which nobody chose, so they are set to no value.
- **Multilingual web portal:** the static interface texts move into vue-i18n bundles in English and German. `fr` and `it` fall back to English for static texts. A label store loads the bundle for the user's language, checks it every five minutes with `If-None-Match`, and the catalog pages show translated metatype names and enum values.
- **Manual:** the walkthrough tests render the screenshots in English and German. The manual describes the language setting and the translation of labels in both languages.

## Out of Scope

- SSE invalidation (US-140 AC3, REQ-152 AC3): `GET /events/stream` stays `planned`. It follows in its own change together with all of its events. Until then, a label change reaches open portals within five minutes.
- Editing labels in the user interface: this comes with SCR-030 in S7 change 4.
- Static interface texts in French and Italian.
- Translating record fields (names and descriptions of entities). They stay in the corporate language (ADR-025).

## Capabilities

### New Capabilities

- `metamodel/labels`: translated labels of metamodel content: maintenance by locale, organization entries over package entries, the label bundle with fallback, ETag and caching headers.
- `platform/ui-language`: the language of the user interface: the person's language setting with browser fallback, static texts in English and German, and the portal reloading dynamic labels by ETag.

### Modified Capabilities

- `metamodel/package-import`: translation bundles are no longer an unsupported package section. A new requirement covers how the package labels are imported and replaced at a re-import.

## Impact

- `api/openapi.yaml`:
  - `getI18nBundle` is implemented (`429` added, `501` removed);
  - new operations `getMetamodelLabels`, `updateMetamodelLabels` and `updateMyPreferences`;
  - `Me.uiLanguage`;
  - new schemas for the label entries and the preferences.
- `backend/`:
  - Flyway V17 with `i18n_entries`, and `persons.ui_language` nullable;
  - `io.oea.app.metamodel` for labels and the bundle;
  - the import of `i18nBundles`;
  - the ETag handling in `io.oea.api`;
  - the preferences of `/me`.
- `packages/oea-starter-togaf-classic.json`: version 1.1.0 with `i18nBundles.de`.
- `frontend/`:
  - new dependency `vue-i18n` (MIT, ADR-043), with `THIRD-PARTY-LICENSES.md` regenerated;
  - message bundles `src/i18n/en.json` and `de.json`, with every page and component converted;
  - the label store;
  - the language select on the profile page;
  - Storybook renders in English.
- `manual/`: profile and metamodel pages in English and German; `OEA_WALKTHROUGH_LANGUAGES=en de` in `pages.yml`.
- `docs/data-model.puml`, `business-objects/` (i18n entry, person), the audit event catalog, US-140, REQ-152 and ADR-025 (refinement note).
