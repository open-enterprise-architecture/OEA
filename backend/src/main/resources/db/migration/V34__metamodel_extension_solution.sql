-- A metamodel extension names an existing solution (change s13-issue-fixes D6; issue #237, US-037,
-- US-038; docs/data-model.puml). Runs as oea_owner.
--
-- Extensions written before solutions existed (V20) may name a solution that does not exist. The
-- migration does not delete or invent data: it aborts and lists those extensions, and the operator
-- resolves them (owner decision 2026-11-15). Without such extensions it adds the foreign key.

DO $$
DECLARE
    orphans TEXT;
BEGIN
    SELECT string_agg(format('%s (%s, solution %s)', c.name, c.id, c.solution_id), '; ' ORDER BY c.name)
    INTO orphans
    FROM metamodel_configurations c
    WHERE c.solution_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM solutions s WHERE s.id = c.solution_id);
    IF orphans IS NOT NULL THEN
        RAISE EXCEPTION 'Metamodel extensions name solutions that do not exist: %', orphans
            USING HINT = 'Create the solutions or remove the extensions, then start OEA again (issue #237).';
    END IF;
END
$$;

ALTER TABLE metamodel_configurations
    ADD CONSTRAINT metamodel_configurations_solution_fk FOREIGN KEY (solution_id) REFERENCES solutions (id);
