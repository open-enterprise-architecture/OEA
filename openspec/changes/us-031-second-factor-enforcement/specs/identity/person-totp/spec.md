# Spec Delta

## MODIFIED Requirements

### Requirement: Enrollment offer after the password login

After a password login of a person without an active second factor, the web portal SHALL offer the enrollment of a second factor; after a required password change, the offer SHALL follow the change. The person SHALL be able to skip the offer and enroll later in the profile, unless the session requires the enrollment.

Trace: US-021, US-025, US-031 AC2, owner decision 2026-10-30

#### Scenario: First login

- **WHEN** an invited person logs in with the initial password and changes it
- **THEN** the web portal shows the TOTP enrollment with a "Skip" button, and skipping it opens the start page

#### Scenario: Enrollment required

- **WHEN** the session of a person requires the enrollment of a second factor
- **THEN** the web portal shows the enrollment without a "Skip" button and opens no other page until a second factor is confirmed
