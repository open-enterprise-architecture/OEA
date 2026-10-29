-- Password policy, switch of password login of persons, password reset (change
-- us-027-password-administration, design D1, D6; docs/data-model.puml). Runs as oea_owner.
-- The operator floors (oea.auth.password-policy.*) are not stored; the effective value of every
-- rule is the stricter of the floor and the stored value.

ALTER TABLE system_settings
    ADD COLUMN password_min_length            SMALLINT NOT NULL DEFAULT 12
                                              CHECK (password_min_length BETWEEN 12 AND 128),
    ADD COLUMN password_require_uppercase     BOOLEAN  NOT NULL DEFAULT false,
    ADD COLUMN password_require_lowercase     BOOLEAN  NOT NULL DEFAULT false,
    ADD COLUMN password_require_digits        BOOLEAN  NOT NULL DEFAULT false,
    ADD COLUMN password_require_special_chars BOOLEAN  NOT NULL DEFAULT false,
    -- 0 = no expiry (REQ-028 AC5)
    ADD COLUMN password_expiry_days           SMALLINT NOT NULL DEFAULT 0
                                              CHECK (password_expiry_days BETWEEN 0 AND 3650),
    -- number of previous passwords, including the current one, that must not be reused
    ADD COLUMN password_history_count         SMALLINT NOT NULL DEFAULT 0
                                              CHECK (password_history_count BETWEEN 0 AND 24),
    -- switch of the administrators; effective only while the operator permits it
    ADD COLUMN person_password_login          BOOLEAN  NOT NULL DEFAULT true,
    ADD COLUMN updated_by_person_id           BIGINT   REFERENCES persons (id);

-- A password reset by a person administrator records who revoked the old password
ALTER TABLE local_credentials
    ADD COLUMN revoked_by_person_id BIGINT REFERENCES persons (id);

-- History check (design D3): the passwords of an owner by revocation time
CREATE INDEX local_credentials_password_history
    ON local_credentials (person_id, revoked_at DESC) WHERE type = 'password' AND status = 'revoked';
