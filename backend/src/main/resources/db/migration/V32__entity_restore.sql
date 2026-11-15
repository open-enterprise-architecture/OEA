-- Restore of an entity to an earlier version (change us-101-entity-restore D1; UC-15, REQ-102;
-- docs/data-model.puml, business-objects/entity-version.md). Runs as oea_owner.
--
-- A version written by a restore records the version it was restored from and the optional
-- reason. The version before the restore stays unchanged as the state before it (ADR-022), so
-- no copy is stored. The runtime role keeps SELECT and INSERT only (append-only history).

ALTER TABLE entity_version_headers
    ADD COLUMN restored_from_version INTEGER,
    ADD COLUMN change_reason TEXT,
    ADD CONSTRAINT entity_version_headers_restored_from
        CHECK (restored_from_version IS NULL OR (restored_from_version >= 1 AND restored_from_version < version)),
    ADD CONSTRAINT entity_version_headers_reason
        CHECK (change_reason IS NULL OR (restored_from_version IS NOT NULL AND length(change_reason) <= 1000));
