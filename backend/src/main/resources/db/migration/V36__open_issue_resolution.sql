-- Open issues blocking and resolvable, and snapshots of metamodel changes in solutions (change
-- us-153-open-issues D1; US-153, US-154; docs/data-model.puml, business-objects/solution.md).
-- Runs as oea_owner.
--
-- An open issue is resolved with a value or by removing it, with person and time; an automatic
-- resolution is computed on read and never stored. An issue `not-in-metamodel` keeps the value
-- that the go-live could not write. A snapshot keeps a property, or one enumeration value, of an
-- instance metatype for one solution after a metamodel change removed it (decision `keep`).

ALTER TABLE solution_open_issues
    ADD COLUMN resolution  TEXT        CHECK (resolution IN ('value', 'removed')),
    ADD COLUMN resolved_at TIMESTAMPTZ,
    ADD COLUMN resolved_by BIGINT      REFERENCES persons (id),
    ADD COLUMN kept_value  TEXT,
    DROP CONSTRAINT solution_open_issues_kind_check,
    ADD CONSTRAINT solution_open_issues_kind_check
        CHECK (kind IN ('mandatory-missing', 'invalid-value', 'not-in-metamodel')),
    ADD CONSTRAINT solution_open_issues_resolved
        CHECK (num_nonnulls(resolution, resolved_at, resolved_by) IN (0, 3)),
    ADD CONSTRAINT solution_open_issues_kept
        CHECK ((kind = 'not-in-metamodel') = (kept_value IS NOT NULL));

-- The definition of an issue `not-in-metamodel` may be deleted once no snapshot keeps it; the
-- issue stays readable with its field.
ALTER TABLE solution_open_issues
    DROP CONSTRAINT solution_open_issues_property_def_id_fkey;

CREATE INDEX solution_open_issues_unresolved ON solution_open_issues (entity_id)
    WHERE resolution IS NULL;

GRANT UPDATE (resolution, resolved_at, resolved_by) ON solution_open_issues TO oea_app;

CREATE TABLE solution_metamodel_snapshots (
    id              UUID        PRIMARY KEY,
    solution_id     UUID        NOT NULL REFERENCES solutions (id),
    metatype_id     UUID        NOT NULL REFERENCES metatypes (id),
    property_def_id UUID        NOT NULL REFERENCES property_definitions (id) ON DELETE RESTRICT,
    -- null keeps the whole property; a value keeps one deleted enumeration value
    enum_value      TEXT        CHECK (length(enum_value) BETWEEN 1 AND 255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by      BIGINT      NOT NULL REFERENCES persons (id)
);

CREATE UNIQUE INDEX solution_metamodel_snapshots_unique
    ON solution_metamodel_snapshots (solution_id, metatype_id, property_def_id, coalesce(enum_value, ''));
CREATE INDEX solution_metamodel_snapshots_property ON solution_metamodel_snapshots (property_def_id);

REVOKE ALL ON solution_metamodel_snapshots FROM PUBLIC;
GRANT SELECT, INSERT, DELETE ON solution_metamodel_snapshots TO oea_app;
