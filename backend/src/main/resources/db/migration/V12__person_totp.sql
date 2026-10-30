-- TOTP login of persons (change us-021-person-totp, design D4; docs/data-model.puml). Runs as
-- oea_owner. A login transaction belongs to exactly one owner: the system admin account or a
-- person whose password step succeeded and who must enter a TOTP code.

ALTER TABLE login_transactions
    ADD COLUMN person_id BIGINT REFERENCES persons (id),
    ALTER COLUMN system_admin_account_id DROP NOT NULL,
    ADD CONSTRAINT login_transactions_owner CHECK ((system_admin_account_id IS NULL) <> (person_id IS NULL));
