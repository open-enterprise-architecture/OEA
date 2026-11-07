-- Catalog query indexes (change s9-catalog-query-performance D7; REQ-071, #66;
-- docs/data-model.puml). Runs as oea_owner.

-- The default order of a catalog (name, then ID) and the keyset seek of its next page
CREATE INDEX entities_active_name ON entities (metatype_id, lower(name), id) WHERE deleted_at IS NULL;
