-- Audit hash chain (ADR-033 §5; canonical serialization in docs/audit-hash-chain.md).
-- Runs as oea_owner. Additive only. The trigger function is SECURITY DEFINER so that
-- the runtime role can append links without any right on audit.chain_head.

-- F(value): 4-byte big-endian length || value bytes; NULL = FF FF FF FF
CREATE FUNCTION audit.chain_field(value text) RETURNS bytea
    LANGUAGE sql IMMUTABLE
    SET search_path = pg_catalog
AS $$
    SELECT CASE WHEN value IS NULL THEN '\xffffffff'::bytea
                ELSE int4send(octet_length(convert_to(value, 'UTF8'))) || convert_to(value, 'UTF8') END
$$;

CREATE FUNCTION audit.chain_field(value bytea) RETURNS bytea
    LANGUAGE sql IMMUTABLE
    SET search_path = pg_catalog
AS $$
    SELECT CASE WHEN value IS NULL THEN '\xffffffff'::bytea ELSE int4send(length(value)) || value END
$$;

-- Text forms of docs/audit-hash-chain.md
CREATE FUNCTION audit.chain_text(value timestamptz) RETURNS text
    LANGUAGE sql IMMUTABLE
    SET search_path = pg_catalog
AS $$
    SELECT to_char(value AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US"Z"')
$$;

CREATE FUNCTION audit.chain_text(value inet) RETURNS text
    LANGUAGE sql IMMUTABLE
    SET search_path = pg_catalog
AS $$
    -- output function form: no prefix length for host addresses (inet::text would add it)
    SELECT CASE WHEN value IS NULL THEN NULL ELSE format('%s', value) END
$$;

CREATE FUNCTION audit.append_to_chain() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = audit, pg_catalog
AS $$
DECLARE
    head  audit.chain_head%ROWTYPE;
    input bytea;
BEGIN
    IF current_setting('transaction_isolation') <> 'read committed' THEN
        RAISE EXCEPTION 'audit records must be written in READ COMMITTED transactions (ADR-033 §5)';
    END IF;

    SELECT * INTO head FROM audit.chain_head WHERE id = 1 FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'audit.chain_head is missing';
    END IF;

    -- Values supplied by the application are overwritten.
    NEW.chain_seq    := head.last_seq + 1;
    NEW.hash_version := 1;
    NEW.prev_hash    := head.last_hash;

    input := chain_field(NEW.hash_version::text) || chain_field(NEW.chain_seq::text)
          || chain_field(NEW.prev_hash) || chain_field(TG_ARGV[0]);

    IF TG_ARGV[0] = 'events' THEN
        input := input
            || chain_field(NEW.id::text) || chain_field(NEW.event_type) || chain_field(NEW.event_category)
            || chain_field(NEW.severity) || chain_field(NEW.outcome) || chain_field(chain_text(NEW.occurred_at))
            || chain_field(NEW.source) || chain_field(chain_text(NEW.source_ip)) || chain_field(NEW.request_id)
            || chain_field(NEW.actor_type) || chain_field(NEW.actor_person_id::text)
            || chain_field(NEW.actor_admin_id::text) || chain_field(NEW.target_type) || chain_field(NEW.target_id)
            || chain_field(NEW.entity_id::text) || chain_field(NEW.entity_name) || chain_field(NEW.details::text)
            || chain_field(NEW.agent_id) || chain_field(NEW.agent_label) || chain_field(NEW.agent_run);
    ELSIF TG_ARGV[0] = 'event_changes' THEN
        input := input
            || chain_field(NEW.id::text) || chain_field(NEW.event_id::text) || chain_field(NEW.property_def_id::text)
            || chain_field(NEW.property_name) || chain_field(NEW.system_field::text)
            || chain_field(NEW.value_protection) || chain_field(NEW.old_value) || chain_field(NEW.new_value)
            || chain_field(NEW.old_value_enc) || chain_field(NEW.new_value_enc) || chain_field(NEW.key_id);
    ELSIF TG_ARGV[0] = 'retention_settings' THEN
        input := input
            || chain_field(NEW.id::text) || chain_field(NEW.retention_days::text)
            || chain_field(chain_text(NEW.recorded_at)) || chain_field(chain_text(NEW.effective_from))
            || chain_field(NEW.recorded_by_person_id::text) || chain_field(NEW.recorded_by_admin_id::text);
    ELSE
        RAISE EXCEPTION 'unknown audit chain table tag %', TG_ARGV[0];
    END IF;

    NEW.record_hash := sha256(input);

    UPDATE audit.chain_head
       SET last_seq = NEW.chain_seq, last_hash = NEW.record_hash, updated_at = now()
     WHERE id = 1;
    RETURN NEW;
END
$$;

REVOKE ALL ON FUNCTION audit.chain_field(text), audit.chain_field(bytea), audit.chain_text(timestamptz),
    audit.chain_text(inet), audit.append_to_chain() FROM PUBLIC;

CREATE TRIGGER events_append_to_chain BEFORE INSERT ON audit.events
    FOR EACH ROW EXECUTE FUNCTION audit.append_to_chain('events');
CREATE TRIGGER event_changes_append_to_chain BEFORE INSERT ON audit.event_changes
    FOR EACH ROW EXECUTE FUNCTION audit.append_to_chain('event_changes');
CREATE TRIGGER retention_settings_append_to_chain BEFORE INSERT ON audit.retention_settings
    FOR EACH ROW EXECUTE FUNCTION audit.append_to_chain('retention_settings');

-- Genesis: prev_hash of chain_seq 1 is 32 zero bytes.
INSERT INTO audit.chain_head (id, last_seq, last_hash, updated_at)
VALUES (1, 0, '\x0000000000000000000000000000000000000000000000000000000000000000'::bytea, now());

-- Retention seed (ADR-033 §4, REQ-083): 7 years, first link of the chain.
INSERT INTO audit.retention_settings (retention_days, effective_from) VALUES (2555, now());
