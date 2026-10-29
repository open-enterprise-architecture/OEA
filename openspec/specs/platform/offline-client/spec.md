# platform/offline-client Specification

## Purpose

Defines what the frontend build guarantees so that the client works without network access to third parties: every icon the client uses is part of the bundle.

## Requirements

### Requirement: Every used icon is bundled

The frontend build SHALL fail if an icon name used in the application source (`i-<collection>-<name>`) is not contained in the built bundle, and SHALL name every missing icon with the file that uses it. The client SHALL never load icons from a network source at runtime.

Trace: REQ-075, ADR-035, ADR-040, issue #62

#### Scenario: Icon missing from the bundle

- **WHEN** a component uses an icon name that the bundle does not contain and the build runs
- **THEN** the build fails and names the icon and the file

#### Scenario: All icons bundled

- **WHEN** every icon used in the application source is contained in the bundle and the build runs
- **THEN** the build succeeds
