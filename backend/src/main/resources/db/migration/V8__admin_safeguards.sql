-- Admin safeguards (change us-019-admin-safeguards; docs/data-model.puml). Runs as oea_owner.

-- Last time a mapping granted its role effectively (US-018, SoD-7 usable path; design D2)
ALTER TABLE idp_group_mappings
    ADD COLUMN last_matched_at TIMESTAMPTZ;

-- A remote system admin is a person; the local account may also deactivate itself (design D7)
ALTER TABLE system_admin_accounts
    ADD COLUMN deactivated_by_person_id BIGINT REFERENCES persons (id),
    ADD CONSTRAINT system_admin_accounts_deactivated_by_single CHECK (
        deactivated_by_admin_id IS NULL OR deactivated_by_person_id IS NULL);
