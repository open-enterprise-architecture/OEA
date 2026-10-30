-- Enforcement of a second factor at the password login of persons (change
-- us-031-second-factor-enforcement, design D1; docs/data-model.puml). Runs as oea_owner. The
-- operator can force the enforcement with oea.auth.second-factor.enforced; it is not stored.

ALTER TABLE system_settings
    ADD COLUMN second_factor_enforced     BOOLEAN NOT NULL DEFAULT false,
    -- role IDs that need no second factor; never a role with an admin-class permission (REQ-029)
    ADD COLUMN second_factor_exempt_roles TEXT[]  NOT NULL DEFAULT '{}';
