-- Dashboards and their widgets (change us-055-dashboards; docs/data-model.puml, business-objects/dashboard.md,
-- ADR-030, ADR-032). Runs as oea_owner. The configuration of a widget lives in config (JSONB): for a text widget
-- {content}, for a table widget {catalogId, savedViewId, pageSize}; the service validates it.

CREATE TABLE dashboards (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL CHECK (name = btrim(name) AND name <> '' AND length(name) <= 200),
    description TEXT        NOT NULL DEFAULT '' CHECK (length(description) <= 1000),
    scope       TEXT        NOT NULL CHECK (scope IN ('instance', 'personal')),
    owner_id    BIGINT      NOT NULL REFERENCES persons (id),
    version     INTEGER     NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by  BIGINT      NOT NULL REFERENCES persons (id),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by  BIGINT      NOT NULL REFERENCES persons (id)
);

-- Names are unique among the shared dashboards and among the personal dashboards of one owner,
-- compared case-insensitively after trimming (BR-01, US-055 AC2)
CREATE UNIQUE INDEX dashboards_instance_name ON dashboards (lower(btrim(name))) WHERE scope = 'instance';
CREATE UNIQUE INDEX dashboards_personal_name ON dashboards (owner_id, lower(btrim(name))) WHERE scope = 'personal';

CREATE TABLE dashboard_widgets (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    dashboard_id UUID        NOT NULL REFERENCES dashboards (id) ON DELETE CASCADE,
    type         TEXT        NOT NULL CHECK (type IN ('kpi', 'chart', 'table', 'text')),
    title        TEXT        NOT NULL CHECK (title = btrim(title) AND title <> '' AND length(title) <= 150),
    col          INTEGER     NOT NULL CHECK (col BETWEEN 1 AND 12),
    width        INTEGER     NOT NULL CHECK (width BETWEEN 1 AND 12),
    grid_row     INTEGER     NOT NULL CHECK (grid_row BETWEEN 1 AND 500),
    height       INTEGER     NOT NULL CHECK (height BETWEEN 1 AND 6),
    config       JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   BIGINT      NOT NULL REFERENCES persons (id),
    CONSTRAINT dashboard_widgets_grid CHECK (col + width <= 13)
);

CREATE INDEX dashboard_widgets_dashboard ON dashboard_widgets (dashboard_id);
CREATE INDEX dashboards_owner ON dashboards (owner_id) WHERE scope = 'personal';

REVOKE ALL ON dashboards, dashboard_widgets FROM PUBLIC;

-- Runtime role: dashboards are deleted for real, together with their widgets (no architecture content)
GRANT SELECT, INSERT, UPDATE, DELETE ON dashboards, dashboard_widgets TO oea_app;
