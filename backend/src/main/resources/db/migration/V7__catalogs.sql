-- Catalogs (change us-046-create-catalog; docs/data-model.puml, business-objects/catalog.md, ADR-030, ADR-032).
-- Runs as oea_owner. Columns, joins, saved filters and saved views live in config (JSONB, ADR-030).

CREATE TABLE catalogs (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                TEXT        NOT NULL CHECK (name = btrim(name) AND name <> '' AND length(name) <= 255),
    description         TEXT        NOT NULL DEFAULT '' CHECK (length(description) <= 1000),
    primary_metatype_id UUID        NOT NULL REFERENCES metatypes (id),
    default_join_mode   TEXT        NOT NULL DEFAULT 'aggregate' CHECK (default_join_mode IN ('rows', 'aggregate')),
    scope               TEXT        NOT NULL DEFAULT 'instance' CHECK (scope IN ('instance', 'personal')),
    created_by          BIGINT      NOT NULL REFERENCES persons (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_modified_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_modified_by    BIGINT      NOT NULL REFERENCES persons (id),
    config              JSONB       NOT NULL
                                    DEFAULT '{"columns": [], "joinDefinitions": [], "savedFilters": [], "savedViews": []}'
);

-- One namespace for both scopes, compared case-insensitively after trimming (REQ-043)
CREATE UNIQUE INDEX catalogs_name ON catalogs (lower(btrim(name)));
CREATE INDEX catalogs_created_by ON catalogs (created_by) WHERE scope = 'personal';

REVOKE ALL ON catalogs FROM PUBLIC;

-- Runtime role: deleting catalogs follows later (ADR-019)
GRANT SELECT, INSERT, UPDATE ON catalogs TO oea_app;
