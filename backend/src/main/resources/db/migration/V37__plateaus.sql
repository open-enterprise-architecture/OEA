-- Plateaus of plateau mode (us-084-plateaus D1; US-084, US-085, US-089, US-155): at most one
-- baseline, a predecessor and a basis for target and transition plateaus, the foreign keys from
-- solutions, and the permission plateau:write (owner decisions 2026-11-17).

CREATE TABLE plateaus (
    id          UUID        PRIMARY KEY,
    name        TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 255),
    description TEXT        CHECK (length(description) <= 2000),
    status      TEXT        NOT NULL CHECK (status IN ('baseline', 'transition', 'target', 'realized')),
    succeeds    UUID        REFERENCES plateaus (id),
    basis       TEXT        CHECK (basis IN ('catalog', 'predecessor')),
    valid_from  DATE,
    valid_to    DATE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  BIGINT      NOT NULL REFERENCES persons (id),
    updated_at  TIMESTAMPTZ,
    updated_by  BIGINT      REFERENCES persons (id),
    -- a baseline has neither predecessor nor basis, a target or transition both; a realized
    -- plateau keeps what it had
    CONSTRAINT plateaus_baseline_shape CHECK (status <> 'baseline' OR (succeeds IS NULL AND basis IS NULL)),
    CONSTRAINT plateaus_planned_shape CHECK (status NOT IN ('target', 'transition')
        OR (succeeds IS NOT NULL AND basis IS NOT NULL)),
    -- longer cycles are checked by the service, a check cannot follow the chain
    CONSTRAINT plateaus_not_own_predecessor CHECK (succeeds <> id)
);

-- names are stored trimmed, so lower(name) is the case-insensitive comparison
CREATE UNIQUE INDEX plateaus_name_unique ON plateaus (lower(name));

-- BR-01 of the plateau: at most one baseline
CREATE UNIQUE INDEX plateaus_one_baseline ON plateaus (status) WHERE status = 'baseline';

CREATE INDEX plateaus_succeeds ON plateaus (succeeds);

GRANT SELECT, INSERT ON plateaus TO oea_app;
-- status and valid_to follow with the plateau go-live (S14 change 4)
GRANT UPDATE (name, description, valid_from, succeeds, basis, updated_at, updated_by) ON plateaus TO oea_app;

ALTER TABLE solutions
    ADD CONSTRAINT solutions_from_plateau FOREIGN KEY (from_plateau_id) REFERENCES plateaus (id),
    ADD CONSTRAINT solutions_to_plateau FOREIGN KEY (to_plateau_id) REFERENCES plateaus (id);

CREATE INDEX solutions_to_plateau ON solutions (to_plateau_id);

-- the new permission comes before plateau:go-live in the catalog
UPDATE permissions SET sort_order = sort_order + 1 WHERE sort_order >= (
    SELECT sort_order FROM permissions WHERE id = 'plateau:go-live');
INSERT INTO permissions (id, category, admin_class, sort_order)
    SELECT 'plateau:write', 'plateau', false, sort_order - 1 FROM permissions WHERE id = 'plateau:go-live';

INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('enterprise-architect', 'plateau:write');
