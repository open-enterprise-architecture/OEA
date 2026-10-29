-- Local passwords of persons (change us-011-local-password, design D3; docs/data-model.puml).
-- Runs as oea_owner. A local credential belongs to exactly one owner: the system admin account
-- or a person.

ALTER TABLE local_credentials
    ADD COLUMN person_id BIGINT REFERENCES persons (id),
    DROP CONSTRAINT local_credentials_owner,
    ADD CONSTRAINT local_credentials_owner CHECK ((system_admin_account_id IS NULL) <> (person_id IS NULL));

-- At most one active password per person (as per system admin account)
CREATE UNIQUE INDEX local_credentials_one_active_password_per_person
    ON local_credentials (person_id) WHERE type = 'password' AND status = 'active';

-- Login of persons by e-mail address, case-insensitive (design D2)
CREATE INDEX persons_email_lower ON persons (lower(email));
