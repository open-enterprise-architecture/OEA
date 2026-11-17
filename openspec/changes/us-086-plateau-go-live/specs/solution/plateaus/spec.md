## ADDED Requirements

### Requirement: Action "Go live" on the page "Plateaus"

The page "Plateaus" (SCR-065) SHALL offer "Go live" on the target or transition plateau whose predecessor is the baseline, to holders of `plateau:go-live` only. After a go-live, it SHALL show the new baseline and the old one under "Realized".

Trace: US-086, UC-11 steps 1–3, 10

#### Scenario: Next plateau

- **GIVEN** "Target 2027" succeeds the baseline and "Target 2028" succeeds "Target 2027"
- **WHEN** Kurt opens the page
- **THEN** "Target 2027" shows "Go live", and "Target 2028" does not

#### Scenario: Without plateau:go-live

- **WHEN** Michael, who holds `solution:write` only, opens the page
- **THEN** no plateau shows "Go live"
