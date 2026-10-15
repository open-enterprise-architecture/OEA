package io.oea.audit.chain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

/** The chain tables, their table tags and ordered hashed columns (hash_version 1). */
public enum ChainTable {

	EVENTS("events", "audit.events", columns("id", ColumnKind.INTEGER, "event_type", ColumnKind.TEXT, "event_category",
			ColumnKind.TEXT, "severity", ColumnKind.TEXT, "outcome", ColumnKind.TEXT, "occurred_at",
			ColumnKind.TIMESTAMP, "source", ColumnKind.TEXT, "source_ip", ColumnKind.INET, "request_id", ColumnKind.TEXT,
			"actor_type", ColumnKind.TEXT, "actor_person_id", ColumnKind.INTEGER, "actor_admin_id", ColumnKind.UUID,
			"target_type", ColumnKind.TEXT, "target_id", ColumnKind.TEXT, "entity_id", ColumnKind.INTEGER,
			"entity_name", ColumnKind.TEXT, "details", ColumnKind.JSONB, "agent_id", ColumnKind.TEXT, "agent_label",
			ColumnKind.TEXT, "agent_run", ColumnKind.TEXT)),

	EVENT_CHANGES("event_changes", "audit.event_changes",
			columns("id", ColumnKind.INTEGER, "event_id", ColumnKind.INTEGER, "property_def_id", ColumnKind.UUID,
					"property_name", ColumnKind.TEXT, "system_field", ColumnKind.BOOLEAN, "value_protection",
					ColumnKind.TEXT, "old_value", ColumnKind.TEXT, "new_value", ColumnKind.TEXT, "old_value_enc",
					ColumnKind.BYTEA, "new_value_enc", ColumnKind.BYTEA, "key_id", ColumnKind.TEXT)),

	RETENTION_SETTINGS("retention_settings", "audit.retention_settings",
			columns("id", ColumnKind.INTEGER, "retention_days", ColumnKind.INTEGER, "recorded_at", ColumnKind.TIMESTAMP,
					"effective_from", ColumnKind.TIMESTAMP, "recorded_by_person_id", ColumnKind.INTEGER,
					"recorded_by_admin_id", ColumnKind.UUID));

	private final String tag;

	private final String qualifiedName;

	private final SequencedMap<String, ColumnKind> columns;

	ChainTable(String tag, String qualifiedName, SequencedMap<String, ColumnKind> columns) {
		this.tag = tag;
		this.qualifiedName = qualifiedName;
		this.columns = columns;
	}

	public String tag() {
		return this.tag;
	}

	public String qualifiedName() {
		return this.qualifiedName;
	}

	public SequencedMap<String, ColumnKind> columns() {
		return this.columns;
	}

	public static ChainTable byTag(String tag) {
		for (ChainTable table : values()) {
			if (table.tag.equals(tag)) {
				return table;
			}
		}
		throw new IllegalArgumentException("Unknown chain table tag " + tag);
	}

	private static SequencedMap<String, ColumnKind> columns(Object... pairs) {
		SequencedMap<String, ColumnKind> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (ColumnKind) pairs[i + 1]);
		}
		return Collections.unmodifiableSequencedMap(map);
	}

}
