---
id: REQ-027
title: Password generator for secure local passwords
type: functional
priority: must
status: proposed
version: 0.1.0
created: 2026-06-25
last_updated: 2026-06-25
author: requirements-engineer
references:
  use_cases:
    - UC-03
  business_objects:
    - local-credential
  business_rules: []
  stakeholders:
    - SH-06
  concept:
    - concept/70-platform/21-api-architecture.md
  adrs:
    - ADR-006
supersedes: []
superseded_by: []
---

# REQ-027: Password generator for secure local passwords

## Statement

The system MUST provide a cryptographically secure password generator that produces passwords from the full printable ASCII special character set and automatically satisfies the respective configured password policies (REQ-028); the generator MUST be available both in the admin context (setting the initial password, REQ-024) and in the user context (changing the password).

## Rationale

Administrators and users should not have to invent strong passwords themselves. An integrated generator ensures that generated passwords satisfy the configured policies (REQ-028) from the start and that no weak passwords arise. Addresses SH-06's requirement for secure local user management without a mandatory external password manager. The full special character set (all printable ASCII special characters, not just a restricted subset) maximizes entropy per character.

## Context

The generator produces passwords in plain text that are hashed immediately afterwards (REQ-024, future password change UC). It is not a standalone service but a helper function within the auth module. Generated passwords must take the current instance configuration (REQ-028) into account so that a generated password is guaranteed to satisfy the policies.

## Type-Specific Fields

### For type=functional

**Inputs**:
- Desired length (optional; default: instance configuration or fallback default of 20 characters)
- Active password policies (from REQ-028, read at runtime)

**Processing**:
- Assemble the character pool from enabled character types (uppercase, lowercase, digits, special characters)
- Use a cryptographically secure random source (CSPRNG, e.g., `java.security.SecureRandom` or OS entropy)
- Guarantee at least one character for each enforced character type (REQ-028)
- Choose the remaining characters randomly from the overall pool
- Randomly permute the order of characters (Fisher-Yates) so that enforced characters are not always at fixed positions
- Special character set: all printable ASCII special characters (codes 33–126, excluding A-Z, a-z, 0-9):
  `!"#$%&'()*+,-./:;<=>?@[\]^_` `` ` `` `{|}~`

**Outputs**:
- Plain-text password (string)
- No persistence by the generator itself; the caller is responsible for hashing and secure handover

**Error Cases**:
- Configured minimum length (REQ-028) is too short to satisfy all enforced character types → error with a specific message (e.g., "Minimum length must be at least 4 if all four character types are enforced")
- CSPRNG not available → error; the generator MUST NOT fall back to a weaker random source

## Acceptance Criteria

**AC1**:
- Given: the instance configuration enforces uppercase, lowercase, digits, and special characters
- When: the generator creates a password
- Then: it contains at least one character of each enforced type

**AC2**:
- Given: special characters are enabled
- When: the generator creates 1000 passwords
- Then: each contains at least one special character from the full ASCII special character set (not just `!@#$%` or similar)

**AC3**:
- Given: a generated password
- When: it is checked against REQ-028 policies
- Then: it satisfies all active rules (no rework by the caller needed)

**AC4**:
- Given: CSPRNG is available
- When: 1000 passwords of the same length are generated
- Then: all are different (no collisions in this sample)

**AC5**:
- Given: the minimum length configuration is too low for the enforced character types
- When: the generator is called
- Then: it returns an error instead of creating a non-compliant password

## Verification Method

- [x] Method: test (automated)
- [x] Test setup: call the generator with various policy combinations; statistically check outputs for character types; CSPRNG mock for the error case
- [x] Measurement tool: test suite of the auth module
- [x] Pass criterion: AC1–AC5 green
- [ ] Integrated in CI: yes, once the implementation exists

## Dependencies

- **Prerequisites**: REQ-028 (password policies must be configurable so that the generator can read them)
- **Downstream effects**: REQ-024 (admin uses the generator when setting the initial password); future password change UC (user uses the generator)
- **Conflicts**: none known

## Risks of Non-Fulfillment

- Risk 1: Without a generator, admins choose weak or predictable initial passwords – increased risk from weak credentials, medium severity
- Risk 2: A generator with a weak random source (e.g., `Math.random()`) is predictable – severe (mitigated by AC4/CSPRNG obligation)

## Trade-offs

- The full ASCII special character set can in rare cases lead to incompatibilities with applications that do not accept certain special characters. Since OEA manages the passwords itself, this is not a problem; the generator only creates passwords for OEA's own credentials.

## Implementation Notes

- CSPRNG: operating system entropy (`/dev/urandom`, `CryptGenRandom`, `java.security.SecureRandom`) – never `rand()`, `Math.random()`, or similar
- Fisher-Yates shuffle after character selection to randomize the positions of enforced characters
- Generator as a pure function (input: policy object → output: string); no state, no side effects; easy to test
- UI: "Generate password" button next to the password field; display the generated password immediately (once, then hide)

## Implementation

- ADR(s): [ADR-006](../../adrs/ADR-006-auth-stack-choice.md)
- Implemented by: –
- Tests: –
- Verification status: not-verified

## Notes

Derived from operator requirement (SH-06): password generator with the full special character set. "All special characters allowed" means: no exclusion of characters such as `"`, `\`, `<`, `>`, etc., which some systems restrict.

## Change History

| Version | Date | Author | Change |
|---|---|---|---|
| 0.1.0 | 2026-06-25 | requirements-engineer | Initial draft |
