# platform/ui-language Specification

## Purpose

Defines the language of the user interface: the language setting of a person with the browser language as fallback, the static interface texts in English and German, and how the web portal shows and refreshes the translated metamodel labels.

## Requirements

### Requirement: Language setting of a person

Every person SHALL read their language (`de`, `en`, `fr`, `it` or none) with `GET /me` and set it for themselves; without a value the interface SHALL use the language of the browser. The setting SHALL apply to the person's next page load and all their sessions.

Trace: US-140 AC1, ADR-025, owner decision 2026-11-04

#### Scenario: Choosing German on the profile page

- **WHEN** a person whose browser is set to English chooses German on the profile page
- **THEN** `GET /me` returns `uiLanguage=de` and the interface is shown in German

#### Scenario: No language chosen

- **WHEN** a person who never chose a language logs in with a browser set to German
- **THEN** `GET /me` returns no `uiLanguage` and the interface is shown in German

### Requirement: Static texts in English and German

The web portal SHALL show its static texts – menus, buttons, messages – in English or German by the effective language; for `fr` and `it` it SHALL show English. Before login the browser language SHALL apply.

Trace: US-140, ADR-025 layer 1, owner decision 2026-11-04

#### Scenario: Login page in German

- **WHEN** a browser set to German opens the login page
- **THEN** the page title and buttons are shown in German

#### Scenario: Language without static texts

- **WHEN** a person with `uiLanguage=fr` opens the catalog overview
- **THEN** the static texts are shown in English

### Requirement: Translated metamodel labels in the portal

The web portal SHALL load the label bundle of the effective language once after login and show metatype names and enum values from it. It SHALL check the bundle again at the latest every five minutes with `If-None-Match` and SHALL keep the shown labels when the answer is 304.

Trace: US-140 AC1, US-140 AC2, REQ-152

#### Scenario: Metatype names in German

- **WHEN** a person with `uiLanguage=de` opens a catalog after the starter package with German labels was imported
- **THEN** the metatype names and the values of `Status` are shown in German

#### Scenario: Periodic check without a change

- **WHEN** five minutes have passed since the bundle was loaded and the labels did not change
- **THEN** the portal sends `If-None-Match` with the known ETag, receives 304 and leaves the shown labels unchanged

#### Scenario: Label changed by an administrator

- **WHEN** an administrator changes a German label while a person has the portal open
- **THEN** the person sees the new label at the latest five minutes later without reloading the page
