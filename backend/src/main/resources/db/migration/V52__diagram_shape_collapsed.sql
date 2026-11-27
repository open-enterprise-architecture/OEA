-- A sub-process shape can be shown collapsed and a pool shape can lie vertical, per diagram (change
-- us-074-bpmn-pools-and-lanes; docs/data-model.puml, business-objects/diagram.md). The stored size of a collapsed
-- shape stays the expanded one. The service accepts collapsed = true only on a shape whose metatype the viewpoint
-- maps to bpmn:SubProcess and orientation = 'vertical' only on one it maps to bpmn:Pool. Runs as oea_owner.
ALTER TABLE diagram_shapes ADD COLUMN collapsed BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE diagram_shapes ADD COLUMN orientation TEXT NOT NULL DEFAULT 'horizontal'
    CHECK (orientation IN ('horizontal', 'vertical'));
