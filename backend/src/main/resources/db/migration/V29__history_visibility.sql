-- History visibility per authorization role and metatype (change us-098-entity-history D3;
-- US-098, REQ-098, owner decision 2026-11-14; docs/data-model.puml). Runs as oea_owner.
-- Only `hide` is stored: a combination without a row is `show-restricted`, the default.
-- Deleting a role removes its rows; a package re-import does not touch the table.

CREATE TABLE history_visibility_settings (
    role_id     TEXT NOT NULL REFERENCES authorization_roles (id) ON DELETE CASCADE,
    metatype_id UUID NOT NULL REFERENCES metatypes (id),
    mode        TEXT NOT NULL CHECK (mode = 'hide'),
    PRIMARY KEY (role_id, metatype_id)
);

-- a change inserts `hide` rows and deletes rows set back to `show-restricted`
REVOKE ALL ON history_visibility_settings FROM PUBLIC;
GRANT SELECT, INSERT, DELETE ON history_visibility_settings TO oea_app;
