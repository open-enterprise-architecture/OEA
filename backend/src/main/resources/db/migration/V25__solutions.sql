-- Solutions as containers of change initiatives, in project mode (us-038-create-solution D1, D2;
-- US-038, REQ-038), and the permission and built-in role that create them (ADR-032 amendment
-- 2026-11-12).

CREATE TABLE solutions (
    id              UUID        PRIMARY KEY,
    name            TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 255),
    description     TEXT        CHECK (length(description) <= 2000),
    status          TEXT        NOT NULL DEFAULT 'draft'
                                CHECK (status IN ('draft', 'proposed', 'approved', 'in-progress', 'implemented', 'archived')),
    -- plateau mode follows in S14; the foreign keys to plateaus come with that table
    from_plateau_id UUID,
    to_plateau_id   UUID,
    owner_id        BIGINT      NOT NULL REFERENCES persons (id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT      NOT NULL REFERENCES persons (id),
    -- BR-01: both plateau references or none
    CONSTRAINT solutions_plateaus_both_or_none CHECK ((from_plateau_id IS NULL) = (to_plateau_id IS NULL))
);

-- names are stored trimmed, so lower(name) is the case-insensitive comparison; partial over
-- non-archived solutions once archiving exists (S12)
CREATE UNIQUE INDEX solutions_name_unique ON solutions (lower(name));

GRANT SELECT, INSERT ON solutions TO oea_app;

-- the new permission follows solution-metamodel:write in the catalog (design D1)
UPDATE permissions SET sort_order = sort_order + 1 WHERE sort_order > 13;
INSERT INTO permissions (id, category, admin_class, sort_order) VALUES
    ('solution:write', 'solution', false, 14);

INSERT INTO authorization_roles (id, name, is_builtin, assignable_to_service_accounts) VALUES
    ('solution-architect', 'Solution Architect', true, false);

INSERT INTO role_permissions (role_id, permission_id) VALUES
    ('enterprise-architect', 'solution:write'),
    ('solution-architect', 'portal:read'),
    ('solution-architect', 'entity:read'),
    ('solution-architect', 'solution:write'),
    ('solution-architect', 'solution-metamodel:write');
