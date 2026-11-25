-- Index for the list of deleted entities (change s17-deleted-entities; ADR-019): newest deletion first, then by ID.
-- Runs as oea_owner. The partial index holds deleted rows only, so it stays small and does not change active reads.

CREATE INDEX entities_deleted ON entities (deleted_at DESC, id DESC) WHERE deleted_at IS NOT NULL;
