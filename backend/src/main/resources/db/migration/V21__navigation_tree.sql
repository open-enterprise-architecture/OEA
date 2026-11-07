-- Navigation tree: folders and their entries (change us-054-catalog-navigation-tree D1; US-054,
-- REQ-050, REQ-094, REQ-095, REQ-097; docs/data-model.puml, business-objects/tree-node.md).
-- Runs as oea_owner.

CREATE TABLE tree_nodes (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL CHECK (name = btrim(name) AND length(name) BETWEEN 1 AND 255),
    description TEXT        CHECK (length(description) <= 500),
    parent_id   UUID        REFERENCES tree_nodes (id) ON DELETE CASCADE,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    created_by  BIGINT      REFERENCES persons (id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- only the root, created by this migration, has no creator
    CONSTRAINT tree_nodes_creator CHECK ((parent_id IS NULL) = (created_by IS NULL))
);

-- folder names are unique among their siblings regardless of case (BR-01)
CREATE UNIQUE INDEX tree_nodes_sibling_name
    ON tree_nodes (coalesce(parent_id, '00000000-0000-0000-0000-000000000000'), lower(name));
-- exactly one root (BR-02)
CREATE UNIQUE INDEX tree_nodes_single_root ON tree_nodes ((parent_id IS NULL)) WHERE parent_id IS NULL;
CREATE INDEX tree_nodes_parent ON tree_nodes (parent_id);

CREATE TABLE tree_node_items (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    tree_node_id  UUID        NOT NULL REFERENCES tree_nodes (id) ON DELETE CASCADE,
    item_type     TEXT        NOT NULL CHECK (item_type IN ('tree-node', 'entity', 'diagram', 'catalog')),
    -- soft reference without a foreign key: an entry never protects or deletes its target (REQ-095)
    reference_id  UUID        NOT NULL,
    display_label TEXT        CHECK (display_label = btrim(display_label) AND length(display_label) BETWEEN 1 AND 255),
    sort_order    INTEGER     NOT NULL DEFAULT 0,
    created_by    BIGINT      NOT NULL REFERENCES persons (id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- a target appears at most once per folder, but in any number of folders (BR-06, REQ-097)
CREATE UNIQUE INDEX tree_node_items_once_per_folder ON tree_node_items (tree_node_id, item_type, reference_id);

-- the root cannot be deleted (BR-02); deleting a folder deletes its subtree by the cascade (BR-05)
CREATE FUNCTION tree_nodes_keep_root() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = public, pg_catalog
AS $$
BEGIN
    IF OLD.parent_id IS NULL THEN
        RAISE EXCEPTION 'the root folder cannot be deleted';
    END IF;
    RETURN OLD;
END
$$;

CREATE TRIGGER tree_nodes_keep_root BEFORE DELETE ON tree_nodes
    FOR EACH ROW EXECUTE FUNCTION tree_nodes_keep_root();
REVOKE ALL ON FUNCTION tree_nodes_keep_root() FROM PUBLIC;

INSERT INTO tree_nodes (name) VALUES ('Repository');

REVOKE ALL ON tree_nodes, tree_node_items FROM PUBLIC;
GRANT SELECT, INSERT, UPDATE, DELETE ON tree_nodes, tree_node_items TO oea_app;
