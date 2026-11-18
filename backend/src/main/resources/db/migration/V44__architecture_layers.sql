-- Architecture layers of the metamodel (change s17-architecture-layers D1; US-057 AC4, REQ-053 BR-04,
-- ADR-017; docs/data-model.puml, business-objects/metamodel-configuration.md). Runs as oea_owner.

CREATE TABLE architecture_layers (
    id                     UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    config_id              UUID        NOT NULL DEFAULT '00000000-0000-0000-0000-000000000001'
                                       REFERENCES metamodel_configurations (id)
                                       CONSTRAINT architecture_layers_instance
                                       CHECK (config_id = '00000000-0000-0000-0000-000000000001'),
    name                   TEXT        NOT NULL CONSTRAINT architecture_layers_name
                                       CHECK (name = btrim(name) AND length(name) BETWEEN 1 AND 100),
    sort_order             INTEGER     NOT NULL CONSTRAINT architecture_layers_sort_order CHECK (sort_order >= 0),
    version                INTEGER     NOT NULL DEFAULT 1 CONSTRAINT architecture_layers_version CHECK (version >= 1),
    scope                  TEXT        NOT NULL DEFAULT 'organization'
                                       CONSTRAINT architecture_layers_scope CHECK (scope IN ('imported', 'organization')),
    source_package_id      TEXT,
    source_package_version TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             BIGINT      REFERENCES persons (id),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by             BIGINT      REFERENCES persons (id),
    CONSTRAINT architecture_layers_origin CHECK ((scope = 'imported') = (source_package_id IS NOT NULL)
                                                 AND (source_package_id IS NULL) = (source_package_version IS NULL))
);

-- the name is unique regardless of case and surrounding blanks (BR-18)
CREATE UNIQUE INDEX architecture_layers_name_unique ON architecture_layers (config_id, lower(btrim(name)));
CREATE INDEX architecture_layers_source_package ON architecture_layers (source_package_id)
    WHERE source_package_id IS NOT NULL;

-- The column has held free text since V5, but nothing ever wrote it that referenced a layer: no
-- layer existed, the screen never sent a value and packages never carried one. A value an API
-- client may have written is cleared before the column becomes a reference.
UPDATE metatypes SET architecture_layer_id = NULL WHERE architecture_layer_id IS NOT NULL;
ALTER TABLE metatypes ALTER COLUMN architecture_layer_id TYPE UUID USING NULL;
ALTER TABLE metatypes ADD CONSTRAINT metatypes_architecture_layer
    FOREIGN KEY (architecture_layer_id) REFERENCES architecture_layers (id);
CREATE INDEX metatypes_architecture_layer_idx ON metatypes (architecture_layer_id)
    WHERE architecture_layer_id IS NOT NULL;

-- layers are an element of the translatable labels (key architecture-layer.{id}.name)
ALTER TABLE i18n_entries DROP CONSTRAINT i18n_entries_entity_type_check;
ALTER TABLE i18n_entries ADD CONSTRAINT i18n_entries_entity_type_check
    CHECK (entity_type IN ('metatype', 'property_category', 'property_def', 'enum_value', 'architecture_layer'));

REVOKE ALL ON architecture_layers FROM PUBLIC;
-- Runtime role: organization layers are deleted for real; the 20-layer limit is checked by the service
GRANT SELECT, INSERT, UPDATE, DELETE ON architecture_layers TO oea_app;
