-- Lineage model (change us-065-lineage-model-and-query D3; REQ-062, ADR-010;
-- docs/data-model.puml and business-objects/metamodel-configuration.md). Runs as oea_owner.

-- Which metatypes play the parts data object, data flow, carries-data, data input and data
-- output; one row like organization_model (V53), a part is unset (NULL) until set
CREATE TABLE lineage_model (
    id                        SMALLINT    PRIMARY KEY CHECK (id = 1),
    data_object_metatype_id   UUID        NULL REFERENCES metatypes (id) ON DELETE SET NULL,
    data_flow_metatype_id     UUID        NULL REFERENCES metatypes (id) ON DELETE SET NULL,
    carries_data_metatype_id  UUID        NULL REFERENCES metatypes (id) ON DELETE SET NULL,
    data_input_metatype_id    UUID        NULL REFERENCES metatypes (id) ON DELETE SET NULL,
    data_output_metatype_id   UUID        NULL REFERENCES metatypes (id) ON DELETE SET NULL,
    version                   INTEGER     NOT NULL DEFAULT 1,
    updated_at                TIMESTAMPTZ NULL,
    updated_by                BIGINT      NULL REFERENCES persons (id)
);
INSERT INTO lineage_model (id) VALUES (1);

REVOKE ALL ON lineage_model FROM PUBLIC;
GRANT SELECT, UPDATE ON lineage_model TO oea_app;
