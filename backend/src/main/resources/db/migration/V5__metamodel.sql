-- Metamodel (change us-033-import-metamodel; docs/data-model.puml, ADR-002, ADR-022). Runs as oea_owner.
-- Viewpoints, layers, rules, visibility roles and translations follow with their features.

CREATE TABLE metamodel_configurations (
    id                         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    scope                      TEXT        NOT NULL DEFAULT 'instance' CHECK (scope IN ('instance', 'solution')),
    parent_id                  UUID        REFERENCES metamodel_configurations (id),
    solution_id                UUID,
    schema_version             TEXT        NOT NULL DEFAULT '1.0',
    edit_mode                  TEXT        NOT NULL DEFAULT 'gui-and-import'
                                           CHECK (edit_mode IN ('gui-and-import', 'import-only')),
    rules_edit_mode            TEXT        NOT NULL DEFAULT 'gui-and-import'
                                           CHECK (rules_edit_mode IN ('gui-and-import', 'import-only')),
    last_modified_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_modified_by_person_id BIGINT      REFERENCES persons (id),
    last_modified_by_admin_id  UUID        REFERENCES system_admin_accounts (id),
    config                     JSONB       NOT NULL DEFAULT '{}',
    CONSTRAINT metamodel_configurations_one_actor
        CHECK (last_modified_by_person_id IS NULL OR last_modified_by_admin_id IS NULL),
    CONSTRAINT metamodel_configurations_parent CHECK ((scope = 'solution') = (parent_id IS NOT NULL))
);

-- The instance configuration (singleton, design D4)
CREATE UNIQUE INDEX metamodel_configurations_one_instance ON metamodel_configurations (scope) WHERE scope = 'instance';
INSERT INTO metamodel_configurations (id, scope) VALUES ('00000000-0000-0000-0000-000000000001', 'instance');

CREATE TABLE metatypes (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id              UUID        NOT NULL REFERENCES metamodel_configurations (id),
    name                   TEXT        NOT NULL CHECK (length(name) BETWEEN 1 AND 100),
    abbreviation           TEXT        CHECK (length(abbreviation) <= 10),
    is_connection          BOOLEAN     NOT NULL DEFAULT false,
    architecture_layer_id  TEXT,
    sort_order             INTEGER     NOT NULL DEFAULT 0,
    scope                  TEXT        NOT NULL DEFAULT 'organization'
                                       CHECK (scope IN ('built-in', 'imported', 'organization')),
    source_package_id      TEXT,
    source_package_version TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             BIGINT      REFERENCES persons (id),
    CONSTRAINT metatypes_name_per_config UNIQUE (config_id, name),
    CONSTRAINT metatypes_origin CHECK ((scope = 'imported') = (source_package_id IS NOT NULL)
                                       AND (source_package_id IS NULL) = (source_package_version IS NULL))
);

CREATE TABLE metatype_connection_constraints (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    metatype_id         UUID NOT NULL REFERENCES metatypes (id),
    direction           TEXT NOT NULL CHECK (direction IN ('source', 'target')),
    allowed_metatype_id UUID NOT NULL REFERENCES metatypes (id),
    CONSTRAINT metatype_connection_constraints_unique UNIQUE (metatype_id, direction, allowed_metatype_id)
);

CREATE TABLE property_categories (
    id                     UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    name                   TEXT    NOT NULL UNIQUE CHECK (length(name) BETWEEN 1 AND 100),
    sort_order             INTEGER NOT NULL DEFAULT 0,
    description            TEXT,
    scope                  TEXT    NOT NULL DEFAULT 'organization'
                                   CHECK (scope IN ('built-in', 'imported', 'organization')),
    source_package_id      TEXT,
    source_package_version TEXT,
    CONSTRAINT property_categories_origin CHECK ((scope = 'imported') = (source_package_id IS NOT NULL)
                                                 AND (source_package_id IS NULL) = (source_package_version IS NULL))
);

CREATE TABLE property_definitions (
    id                         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name                       TEXT        NOT NULL UNIQUE CHECK (length(name) BETWEEN 1 AND 100),
    sql_column_name            TEXT        NOT NULL UNIQUE CHECK (sql_column_name ~ '^[a-z][a-z0-9_]{0,62}$'
                                           AND sql_column_name NOT IN ('id', 'urn', 'metatype_id', 'name', 'description',
                                                                       'is_logical', 'created_at', 'updated_at')),
    data_type                  TEXT        NOT NULL CHECK (data_type IN ('string', 'integer', 'boolean', 'enum',
                                                                         'reference', 'date', 'url', 'text')),
    category_id                UUID        REFERENCES property_categories (id),
    ref_metatype_id            UUID        REFERENCES metatypes (id),
    max_length                 INTEGER,
    min_value                  NUMERIC,
    max_value                  NUMERIC,
    pattern                    TEXT,
    visibility_mode            TEXT        NOT NULL DEFAULT 'public'
                                           CHECK (visibility_mode IN ('public', 'role-restricted', 'connection-scoped')),
    scoping_connection_type_id UUID        REFERENCES metatypes (id),
    scope                      TEXT        NOT NULL DEFAULT 'organization'
                                           CHECK (scope IN ('built-in', 'imported', 'organization')),
    source_package_id          TEXT,
    source_package_version     TEXT,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by                 BIGINT      REFERENCES persons (id),
    CONSTRAINT property_definitions_reference CHECK ((data_type = 'reference') = (ref_metatype_id IS NOT NULL)),
    CONSTRAINT property_definitions_scoping
        CHECK ((visibility_mode = 'connection-scoped') = (scoping_connection_type_id IS NOT NULL)),
    CONSTRAINT property_definitions_origin CHECK ((scope = 'imported') = (source_package_id IS NOT NULL)
                                                  AND (source_package_id IS NULL) = (source_package_version IS NULL))
);

CREATE TABLE property_enum_values (
    id              UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    property_def_id UUID    NOT NULL REFERENCES property_definitions (id),
    value           TEXT    NOT NULL CHECK (length(value) BETWEEN 1 AND 100),
    sort_order      INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT property_enum_values_unique UNIQUE (property_def_id, value)
);

CREATE TABLE metatype_property_mappings (
    id                 UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    metatype_id        UUID        NOT NULL REFERENCES metatypes (id),
    property_def_id    UUID        NOT NULL REFERENCES property_definitions (id),
    necessity          TEXT        NOT NULL CHECK (necessity IN ('mandatory', 'warning', 'optional')),
    valid_from         DATE        NOT NULL,
    default_value_text TEXT,
    default_value_int  INT8,
    default_value_bool BOOLEAN,
    default_value_date DATE,
    default_value_ref  INTEGER,
    default_value_enum TEXT,
    sort_order         INTEGER     NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by         BIGINT      REFERENCES persons (id),
    CONSTRAINT metatype_property_mappings_unique UNIQUE (metatype_id, property_def_id, valid_from),
    CONSTRAINT metatype_property_mappings_one_default CHECK (num_nonnulls(default_value_text, default_value_int,
        default_value_bool, default_value_date, default_value_ref, default_value_enum) <= 1)
);

-- Package imports (design D5); the batch ID is shared by the audit events of the import
CREATE TABLE metamodel_package_imports (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    package_id            TEXT        NOT NULL,
    package_version       TEXT        NOT NULL,
    publisher             TEXT        NOT NULL,
    batch_id              UUID        NOT NULL UNIQUE,
    imported_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    imported_by_person_id BIGINT      REFERENCES persons (id),
    imported_by_admin_id  UUID        REFERENCES system_admin_accounts (id),
    metatypes_created     INTEGER     NOT NULL CHECK (metatypes_created >= 0),
    metatypes_updated     INTEGER     NOT NULL CHECK (metatypes_updated >= 0),
    CONSTRAINT metamodel_package_imports_one_actor
        CHECK ((imported_by_person_id IS NULL) <> (imported_by_admin_id IS NULL))
);

REVOKE ALL ON metamodel_configurations, metatypes, metatype_connection_constraints, property_categories,
    property_definitions, property_enum_values, metatype_property_mappings, metamodel_package_imports FROM PUBLIC;

-- Runtime role. Metamodel content is never deleted by the runtime; mappings, enum values and
-- connection constraints are replaced when a package is re-imported.
GRANT SELECT, INSERT, UPDATE ON metamodel_configurations, metatypes, property_categories, property_definitions,
    metamodel_package_imports TO oea_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON metatype_connection_constraints, property_enum_values,
    metatype_property_mappings TO oea_app;
