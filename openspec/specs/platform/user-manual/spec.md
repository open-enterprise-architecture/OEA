# platform/user-manual Specification

## Purpose

Defines the published user manual of OEA: its structure in English and German, the check that every implemented function is described in both languages, the API reference generated from the contract, and its publication next to Storybook.

## Requirements

### Requirement: Manual structure in two languages

The manual SHALL offer an English root and a German locale under `/de/`, each with the chapters Introduction, Setup and first steps, Administration, Usage and API reference and the same pages; a page that exists in only one language SHALL fail the build.

Trace: REQ-196 AC1, ADR-045 rules 1 and 2

#### Scenario: Chapters in both languages

- **WHEN** a reader opens the site root and the German locale `/de/`
- **THEN** both contain the chapters Introduction, Setup and first steps, Administration, Usage and API reference with the same pages

#### Scenario: Page in one language only

- **WHEN** CI runs with an English manual page that has no German counterpart
- **THEN** the build fails and names the page and the missing language

### Requirement: Every implemented function covered

Every implemented screen of the screen inventory and every user story with status `in-progress`, `review`, `done` or `realized` SHALL be covered by a manual page in both languages, and the German page SHALL cover the same screens and stories as its English page; otherwise CI SHALL fail and name what is missing.

Trace: REQ-196 AC2, ADR-045 rule 3, US-001, US-013, US-033, US-046, US-049, US-141, US-143, US-147

#### Scenario: Implemented screen not covered

- **WHEN** CI runs with a screen of status `implemented` that no manual page covers in German
- **THEN** the build fails and names the screen and the language

#### Scenario: Story in progress not covered

- **WHEN** CI runs with a user story of status `in-progress`, `review`, `done` or `realized` that no manual page covers
- **THEN** the build fails and names the story and the languages

#### Scenario: Translation covers something different

- **WHEN** CI runs with a German page whose covered screens or stories differ from those of its English page
- **THEN** the build fails and names the page and the differing entries

#### Scenario: All functions covered

- **WHEN** CI runs and every implemented screen and every story in progress, in review or realized is covered in both languages
- **THEN** the coverage check passes

### Requirement: API reference from the contract

The API reference SHALL be generated from the contract when the manual is built and list every implemented operation with its parameters, responses, problem types and required permission, and no planned operation; a reference that does not contain exactly the implemented operations SHALL fail the build.

Trace: REQ-196 AC3, ADR-045 rule 4, ADR-013

#### Scenario: Implemented operation listed

- **WHEN** the manual is built from a contract with an implemented operation
- **THEN** the API reference in both languages lists the operation with its parameters, responses, problem types and required permission

#### Scenario: Planned operation left out

- **WHEN** the manual is built from a contract with an operation marked as planned
- **THEN** the API reference does not list the operation

### Requirement: Publication next to Storybook

Every change to `main` SHALL publish the manual at the site root and Storybook under `/storybook/` of the same site; until the first release is published, every page of the manual SHALL show that it describes the unreleased state of `main`.

Trace: REQ-196, ADR-045 rule 7, ADR-031, owner decision of 2026-10-24

#### Scenario: Manual and Storybook on one site

- **WHEN** a change is merged into `main`
- **THEN** the site root shows the manual of that commit and `/storybook/` shows its Storybook

#### Scenario: Unreleased manual marked

- **WHEN** a reader opens a manual page before the first release is published
- **THEN** the page states that it describes the unreleased development state
