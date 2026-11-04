# Design

## Context

See proposal.md – Why. Current state:

- Metatypes, categories, property definitions and enum values have one name in the corporate language (`V5__metamodel.sql`, `V15__metatype_editing.sql`). `docs/data-model.puml` already describes the table `i18n_entries` (ADR-025), but no migration creates it.
- `getI18nBundle` and `getEventStream` are `x-oea-status: planned`. The package import rejects `i18nBundles` as unsupported (`MetamodelPackages`).
- `persons.ui_language` exists (`TEXT NOT NULL DEFAULT 'de'`, V3), but nothing reads or sets it.
- The web portal (`frontend/`) has neither vue-i18n nor Pinia. Its texts are English literals in the pages and components. Its state lives in small `reactive` modules (`session/store.ts`).
- The walkthrough tests already take the language from `OEA_WALKTHROUGH_LANGUAGE` and set it as the browser locale (`manual/playwright.config.ts`). `pages.yml` renders only `en`.

## Goals / Non-Goals

**Goals:**

- One query builds the bundle of a locale, including the fallback names, cheaply enough to run on every request; a 304 needs no extra storage.
- Label maintenance by organization and by packages, with the same layering as ADR-049.
- The language choice works per person, without a new login.

**Non-Goals:**

- No server push; `getEventStream` stays planned.
- No label of descriptions: metatypes and property definitions have no description field yet. The key convention allows `.description` later.
- No translation of role names (`authorization_role` in the data model); roles follow with the access administration screens.

## Architecture Constraints

- ADR-025: two layers. Static texts come from vue-i18n bundles and need no API call. Dynamic labels come from `GET /i18n/{locale}` with ETag and `Cache-Control: max-age=300, stale-while-revalidate=3600`. SSE is optional.
- ADR-049: package content stays read-only. The organization adds its own entries, and on the same key the organization entry wins.
- ADR-028: `io.oea.api.i18n` and `io.oea.api.metamodel` → `io.oea.app.metamodel` (`MetamodelLabels`) → JdbcTemplate.
- ADR-024/ADR-033: label changes are audited as `metamodel.labels-changed`. The import records its labels in `metamodel.package-imported`.
- ADR-032: `getI18nBundle` needs only an authenticated principal. The label maintenance needs `metamodel:write`. The preferences belong to the caller.
- ADR-043: vue-i18n is MIT-licensed.

## Decisions

### D1: Label keys with stable identifiers

Keys follow `{entity_type}.{uuid}.{field}` from ADR-025:

- `metatype.{id}.name`
- `property-category.{id}.name`
- `property-def.{id}.label`
- `enum-value.{id}.label`

The abbreviation (`metatype.ac.name` in the ADR example) is not used. The abbreviation of an organization metatype can change, but the UUID cannot. The contract example is updated accordingly.

### D2: Table `i18n_entries` with scope

The columns follow the data model: `entity_type`, `entity_id`, `locale`, `field` and `value`. Two columns are added:

- `scope` (`organization` | `imported`), with `UNIQUE (entity_type, entity_id, locale, field, scope)`;
- `updated_at`.

Package entries and organization entries are separate rows. A re-import therefore deletes the `imported` rows of the package's elements and inserts the new ones, as `replaceMappings` does for the mappings. The organization rows stay untouched. Without a database foreign key (the reference is polymorphic), deleting a metatype, category, property definition or enum value also deletes its rows in the same transaction (`MetatypeEditing`, `PropertyDefinitions`).

Alternative: one row per key, with the package text overwritten on re-import. This was rejected because it loses the organization override (ADR-049).

### D3: Bundle built in one query, ETag as a content hash

`MetamodelLabels.bundle(locale)` runs one `UNION ALL` over metatypes, categories, property definitions and enum values. A `LEFT JOIN` on `i18n_entries` takes the organization entry first, then the package entry, then the name.

The ETag is a weak validator, `W/"<first 16 hex digits of SHA-256>"`, computed over the sorted bundle. The controller compares `If-None-Match` after it builds the bundle and answers 304 without a body.

ADR-025 names `MAX(updated_at)`. That value changes neither on a deleted entry nor on a renamed element without a translation, so a fallback name would stay stale. The content hash covers both cases and needs no extra column. ADR-025 gets a dated refinement note, because the hash is still taken over the label state of the locale. The cost is one query per request. For the metamodel sizes of the walking skeleton (a few hundred keys) this is cheap, and a cache would add invalidation paths. Revisit if the bundle exceeds 10 000 keys.

Alternative: a version counter per locale, increased by every metamodel change. This was rejected because every write path of the metamodel would have to remember to increase it.

### D4: Maintenance API per locale

- `getMetamodelLabels` (`GET /metamodel/labels/{locale}`, `metamodel:write`) returns, per key, the element type, the corporate-language name, the package text and the organization text. SCR-030 can show it as a table.
- `updateMetamodelLabels` (`PUT /metamodel/labels/{locale}`, `metamodel:write`) takes `{ "labels": { key: text } }`:
  - a non-blank text sets the organization entry;
  - an empty string or `null` removes it;
  - keys that are not named stay unchanged;
  - texts are trimmed and at most 200 characters long;
  - unknown or malformed keys are rejected together with `422 validation-failed` and `invalidFields`, and nothing is stored;
  - one audit event `metamodel.labels-changed` records the locale and the counts of set and removed keys.

Alternative: label fields on each metatype or property operation. This was rejected because it multiplies the contract changes and does not fit the bundle format.

### D5: Package labels

`i18nBundles` (locale → bundle) becomes a supported section. Each key must name an element of the same package, and the locale must be supported; otherwise the import fails with 422 and the key in the problem. The labels are written after the elements, in the import transaction. The import result and `metamodel.package-imported` carry the number of labels.

The starter package `oea-starter-togaf-classic` goes to 1.1.0 and gets `i18nBundles.de`. Its English names stay the corporate-language names. The `StarterPackageCheck` and the starter list then show 1.1.0. An instance that imported 1.0.0 receives the labels by a re-import.

### D6: Language of the person

V17 makes `persons.ui_language` nullable, drops the default and sets every existing row to `NULL`. The `'de'` in those rows was never chosen by anyone. A `CHECK` restricts the column to `de`, `en`, `fr`, `it` or `NULL`.

`Me.uiLanguage` is nullable. `updateMyPreferences` (`PUT /me/preferences`, `{ "uiLanguage": "de" | "en" | "fr" | "it" | null }`, authenticated, `204`) changes only the caller's row and is not audited, because the setting is not security-relevant. Service accounts are rejected with 403, because the BO says the setting is ignored for them.

The effective language in the portal is computed in this order:

1. `me.uiLanguage`;
2. the first browser language in `navigator.languages` that is one of `de`, `en`, `fr`, `it`;
3. `en`.

### D7: vue-i18n and the label store in the portal

`vue-i18n` runs in composition mode with `legacy: false`. Messages are in `frontend/src/i18n/en.json` and `de.json`, with keys by page (`login.title`, `catalogs.create`, …). `fallbackLocale` is `en`, so `fr` and `it` show English.

`frontend/src/i18n/index.ts` creates the instance and exports `setLanguage()`. The router guard sets the language after `refreshSession`. Before login, the browser language applies (D6). Storybook installs the same instance with `en`, so the screen images do not change.

`frontend/src/i18n/labels.ts` is a `reactive` module like `session/store.ts`, not Pinia, because the portal uses no Pinia so far (US-140 names Pinia; the behavior is the same). It provides:

- `loadLabels(locale)`, which sends `If-None-Match` and on 304 keeps the map and ETag;
- `label(key, fallback)`;
- a 5-minute `setInterval` started after login and stopped on logout and when the language changes.

`CatalogPage` and `CatalogTable` show metatype names and enum values through `label()`. The API types come from `generated/schema.d.ts` as before.

### D8: Walkthrough and manual in two languages

`pages.yml` sets `OEA_WALKTHROUGH_LANGUAGES: "en de"`. The walkthrough selectors use `data-testid`, not texts. Where a test still matches on visible text, it switches to test IDs, so that both runs pass. The German manual then shows German screenshots without page changes ("Screenshots in the language of the page").

## Data Model

- **V17 `V17__i18n_entries.sql`:** creates the table `i18n_entries`:
  - columns `id UUID PK`, `entity_type`, `entity_id UUID`, `locale`, `field`, `value TEXT NOT NULL`, `scope`, `package_id` (origin package for `imported`, NULL for `organization`) and `updated_at`;
  - `CHECK`s on `entity_type`, `locale`, `field` and `scope`;
  - `UNIQUE (entity_type, entity_id, locale, field, scope)`;
  - an index on `(locale)`;
  - grants for the application role.

  The same migration changes `persons.ui_language`: nullable, no default, existing rows `NULL`, with a `CHECK`.
- **`docs/data-model.puml`:** `i18n_entries` gets `scope`, `package_id` and `updated_at` and the new unique key. `entity_id` becomes UUID; role labels are noted as not yet used. `persons.ui_language` becomes nullable.
- **`business-objects/person.md`:** `uiLanguage` becomes optional, with no value meaning the browser language.
- **`business-objects/metamodel-configuration.md`:** the labels per locale and the rule that the organization entry wins are added as a new business rule, with a version bump.

## API

Delta to `api/openapi.yaml`:

- `getI18nBundle`:
  - remove `x-oea-status: planned` and `501`, and add `429`;
  - the `404` for an unsupported locale is replaced by `400` (parameter enum);
  - add the request header `If-None-Match`;
  - the `304` gets an `ETag` header;
  - in the description, `MAX(updated_at)` becomes the content hash;
  - the `I18nBundle` example uses UUID keys.
- New `getMetamodelLabels` and `updateMetamodelLabels` under `/metamodel/labels/{locale}` (tag Metamodel, `metamodel:write`, 401/403/422/429). New schemas `MetamodelLabel` (`key`, `elementType`, `name`, `packageText`, `organizationText`) and `MetamodelLabelsUpdate`.
- New `updateMyPreferences` under `/me/preferences` (`authenticated`, 204/400/401/403/429). New schema `PersonPreferences`; its `uiLanguage` is a nullable string with the pattern `^(de|en|fr|it)$` instead of an enum, because the generated Java enum of a nullable enum reads an unknown value as `null` and would clear the setting instead of rejecting it with 400.
- `Me` gets `uiLanguage` (`[string, 'null']`, enum including null).
- `MetamodelPackage.i18nBundles`: the description names the key rule. `ImportResult` gets `labelCount`.

## Risks / Trade-offs

- [Converting every page to message keys can break existing tests that match on English text] → Vitest mounts with the English instance, so the texts stay the same. The specs switch to test IDs only where they also need to pass in German.
- [A bundle query per request] → One SQL statement over indexed tables. The ETag avoids the transfer, not the query; measure in the integration test with the starter package and revisit at 10 000 keys (D3).
- [Setting every `ui_language` to NULL changes the language of persons who relied on the default] → No interface ever showed or used the value, so no person relied on it.
- [German screenshots double the walkthrough time in `pages.yml`] → Accepted; the run happens only on publication.
- [Labels of deleted elements stay behind without a foreign key] → Every delete path removes its rows in the same transaction. A test deletes each element type and checks that its rows are gone.
