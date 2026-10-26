## ADDED Requirements

### Requirement: Screenshots from walkthrough tests

Every screenshot of the manual SHALL be produced by walkthrough tests that drive the running application; no screenshot SHALL be made by hand or taken from Storybook. A build that publishes the manual SHALL run the walkthrough tests against the stack of the same commit and SHALL fail if a screenshot referenced by a manual page was not produced.

Trace: REQ-196 AC4, ADR-045 rule 5

#### Scenario: Screenshots produced for a release

- **WHEN** the manual of a version tag or of `main` is published
- **THEN** the walkthrough tests run against the stack of that commit and produce every screenshot the manual references

#### Scenario: Referenced screenshot missing

- **WHEN** a manual page references a screenshot that the walkthrough tests did not produce
- **THEN** the publication build fails and names the screenshot

### Requirement: Screenshots in the language of the page

The walkthrough tests SHALL render every screenshot in each language the user interface offers. A manual page SHALL show the screenshot in its own language if one was produced and the English screenshot otherwise, without the page naming the language.

Trace: REQ-196 AC5, ADR-045 rule 5, ADR-025

#### Scenario: User interface only in English

- **WHEN** the user interface offers only English and the German manual is built
- **THEN** the German pages show the English screenshots

#### Scenario: User interface also in German

- **WHEN** the user interface offers German and the walkthrough tests produce German screenshots
- **THEN** the German pages show the German screenshots without changes to the manual pages

### Requirement: One manual per release

A version tag `vX.Y.Z` SHALL publish the manual of the tagged commit under `/vX.Y/` and at the site root, without the unreleased notice; a later patch release of the same minor version SHALL replace the manual under `/vX.Y/`. Published manuals of other minor versions SHALL stay unchanged.

Trace: REQ-196 AC6, ADR-045 rule 6, owner decision of 2026-10-26

#### Scenario: Latest release at the root

- **WHEN** the releases v0.1.0 and v0.2.0 are published and a reader opens the site root
- **THEN** it shows the manual of v0.2.0, and `/v0.1/` still shows the unchanged manual of v0.1.0

#### Scenario: Patch release replaces its minor version

- **WHEN** v0.2.1 is published after v0.2.0
- **THEN** `/v0.2/` and the site root show the manual of v0.2.1, and `/v0.1/` is unchanged

### Requirement: Version selector

Every manual page SHALL offer a version selector that lists every published version and the development state, including versions published after the page was built.

Trace: REQ-196 AC6, ADR-045 rule 6

#### Scenario: All versions offered

- **WHEN** the releases v0.1.0 and v0.2.0 are published and a reader opens the site root
- **THEN** the version selector offers v0.1, v0.2 and the development state

#### Scenario: Older manual lists newer releases

- **WHEN** a reader opens the manual of v0.1 after v0.2.0 has been published
- **THEN** the version selector of that page offers v0.2 as well

## MODIFIED Requirements

### Requirement: Publication next to Storybook

Every change to `main` SHALL publish the manual of `main` and Storybook under `/storybook/` of the same site. Until the first release is published, the manual of `main` SHALL be published at the site root; afterwards it SHALL be published under `/next/`, and the site root SHALL show the latest release. Every page of the manual of `main` SHALL show that it describes the unreleased state of `main`.

Trace: REQ-196, ADR-045 rules 6 and 7, ADR-031, owner decisions of 2026-10-24 and 2026-10-26

#### Scenario: Manual and Storybook on one site

- **WHEN** a change is merged into `main` before the first release is published
- **THEN** the site root shows the manual of that commit and `/storybook/` shows its Storybook

#### Scenario: Development state after the first release

- **WHEN** a change is merged into `main` after a release has been published
- **THEN** `/next/` shows the manual of that commit, `/storybook/` shows its Storybook, and the site root still shows the latest release

#### Scenario: Unreleased manual marked

- **WHEN** a reader opens a page of the manual of `main`
- **THEN** the page states that it describes the unreleased development state
