-- Partial restore of an entity (change us-104-partial-restore D1; UC-16, REQ-106;
-- docs/data-model.puml, business-objects/entity-version.md). Runs as oea_owner.
--
-- A version written by a partial restore records the keys of the fields it restored: name,
-- description, isLogical or the ID of a property definition. A full restore records none. The
-- runtime role keeps SELECT and INSERT only (append-only history).

ALTER TABLE entity_version_headers
    ADD COLUMN restored_fields TEXT[],
    ADD CONSTRAINT entity_version_headers_restored_fields
        CHECK (restored_fields IS NULL OR (restored_from_version IS NOT NULL AND cardinality(restored_fields) >= 1));
