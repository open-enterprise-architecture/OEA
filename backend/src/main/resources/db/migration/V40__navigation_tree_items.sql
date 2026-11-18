-- Navigation tree entries for entities and solutions, and the manual order (change
-- us-095-navigation-tree-items D1; US-095, US-096, US-097, REQ-095, REQ-096, REQ-097;
-- docs/data-model.puml, business-objects/tree-node.md). Runs as oea_owner.

-- Entities have INTEGER IDs, so an entity entry references them by its own column. Entities are
-- only soft-deleted (ADR-019), so the foreign key never blocks; the entry stays and is marked.
ALTER TABLE tree_node_items ALTER COLUMN reference_id DROP NOT NULL;
ALTER TABLE tree_node_items ADD COLUMN entity_id INTEGER REFERENCES entities (id);

ALTER TABLE tree_node_items DROP CONSTRAINT tree_node_items_item_type_check;
ALTER TABLE tree_node_items ADD CONSTRAINT tree_node_items_item_type_check
    CHECK (item_type IN ('tree-node', 'entity', 'diagram', 'catalog', 'solution'));

-- an entity entry has exactly the entity reference, every other entry exactly the UUID reference
ALTER TABLE tree_node_items ADD CONSTRAINT tree_node_items_reference CHECK (
    CASE WHEN item_type = 'entity' THEN entity_id IS NOT NULL AND reference_id IS NULL
         ELSE reference_id IS NOT NULL AND entity_id IS NULL END);

-- an entity appears at most once per folder, but in any number of folders (BR-06, REQ-097)
CREATE UNIQUE INDEX tree_node_items_entity_once_per_folder ON tree_node_items (tree_node_id, entity_id)
    WHERE entity_id IS NOT NULL;
CREATE INDEX tree_node_items_entity ON tree_node_items (entity_id);

-- Starting order (REQ-096): the order by name the tree showed so far, numbered densely per parent
-- folder and per folder; an entry is ordered by its display label, else by its catalog's name.
UPDATE tree_nodes t
SET sort_order = o.position
FROM (SELECT id, row_number() OVER (PARTITION BY parent_id ORDER BY lower(name) COLLATE "C", id) - 1 AS position
      FROM tree_nodes) o
WHERE t.id = o.id;

UPDATE tree_node_items t
SET sort_order = o.position
FROM (SELECT i.id,
             row_number() OVER (PARTITION BY i.tree_node_id
                                ORDER BY lower(coalesce(i.display_label, c.name)) COLLATE "C" NULLS LAST, i.id) - 1
                 AS position
      FROM tree_node_items i
      LEFT JOIN catalogs c ON i.item_type = 'catalog' AND c.id = i.reference_id) o
WHERE t.id = o.id;
