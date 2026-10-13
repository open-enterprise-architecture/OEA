# Migration and Naming Notes

This document summarizes the naming decision and possible future adjustments. **Once established, this file can be deleted.**

## Project Name: OEA

**Official designation**: OEA – Open Enterprise Architecture

**Rationale**:

- Clearly descriptive: everyone understands immediately what it is
- Low trademark risk (no known conflicts)
- No risk of confusion with OpenAI or other protected brands
- "Open" as a prefix follows established OSS convention (OpenSSH, OpenStack, OpenStreetMap)
- "EA" is an established term in the professional discourse for Enterprise Architecture

## Usage in the Repo

- **Proper name**: OEA (capital letters, without article)
- **Long form**: Open Enterprise Architecture
- **Repo slug**: `oea`
- **Domain**: TBD (`oea.dev`, `oea.org`, or `open-enterprise-architecture.org`)
- **Identifier in configs**: `oea`

## Important Distinctions

Distinguish carefully in texts and code comments:

- **OEA** = our project
- **EA** = enterprise architecture as a discipline
- **EA tools** (plural) = market reference to existing commercial tools (Sparx EA, Avolution, LeanIX, Ardoq, etc.)
- **EA discipline**, **EA area**, **EA model** = technical terms, remain unchanged

## To Set Up Before Go-Live

- [ ] Reserve domain: check availability of `oea.dev`, `oea.org`, `oea.ch`, `open-enterprise-architecture.org`
- [ ] Set up email addresses:
  - `security@<domain>` (SECURITY.md)
  - `conduct@<domain>` (CODE_OF_CONDUCT.md)
  - `maintainers@<domain>` (MAINTAINERS.md)
- [ ] Trademark search for DE, EU, US (even if conflicts are unlikely)
- [ ] Update repository URL in `.github/ISSUE_TEMPLATE/config.yml`
- [ ] MAINTAINERS.md: fill the lead maintainer line with your name
- [ ] Design a logo (not strictly necessary for the first release)

## Note on Spelling

In German, "OEA" works as a proper name without an article:

- ✓ "OEA unterstützt Use Cases"
- ✓ "OEA wird als Open Source entwickelt"
- ✗ "Das OEA macht..." (sounds like "das BMW")

In lists or in a technical context, short references such as "im OEA-Repository" or "OEA-Konfiguration" are idiomatic.

## If OEA Must Be Changed Later After All

Most mentions are quickly findable via `grep -r "OEA"` and `grep -r "Open Enterprise Architecture"`. Pay particular attention to "EA" technical terms (see list above) during bulk replacements.
