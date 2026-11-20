-- Origin of a cascade deletion (change s17-entity-delete; docs/data-model.puml, business-objects/entity.md, ADR-019).
-- Runs as oea_owner. A deleted entity takes its children on all levels and the connections of both along; each of
-- them records the entity whose deletion took it along, so that the restore finds exactly this cascade. The entity
-- itself, an entity deleted before this migration and an active entity have no origin.

ALTER TABLE entities ADD COLUMN deleted_with_entity_id INTEGER REFERENCES entities (id);

ALTER TABLE entities ADD CONSTRAINT entities_deleted_with_deleted
    CHECK (deleted_with_entity_id IS NULL OR deleted_at IS NOT NULL);

CREATE INDEX entities_deleted_with ON entities (deleted_with_entity_id) WHERE deleted_with_entity_id IS NOT NULL;
