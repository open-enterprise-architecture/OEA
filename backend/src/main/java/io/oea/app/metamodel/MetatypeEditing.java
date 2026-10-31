package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating, changing and deleting the organization's own metatypes and their base types (design
 * D3, D4 of us-032-metatype-editing; US-032, REQ-032, ADR-002). Built-in and imported metatypes
 * are read-only.
 */
@Service
public class MetatypeEditing {

	static final int NAME_MAX_LENGTH = 100;

	static final int ABBREVIATION_MAX_LENGTH = 10;

	private final JdbcTemplate jdbc;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	public MetatypeEditing(JdbcTemplate jdbc, MetamodelEffects effects, MetamodelAudit audit) {
		this.jdbc = jdbc;
		this.effects = effects;
		this.audit = audit;
	}

	/** The writable fields of a metatype. */
	public record Input(String name, String abbreviation, boolean connection, String architectureLayerId,
			int sortOrder, UUID configId, UUID baseMetatypeId) {
	}

	private record Stored(String name, String abbreviation, boolean connection, String architectureLayerId,
			int sortOrder, String scope, UUID baseMetatypeId) {
	}

	@Transactional
	public UUID create(OeaPrincipal principal, Input input) {
		this.effects.lock();
		String name = validate(input, null);
		if (input.connection()) {
			throw MetamodelChangeException.invalid("isConnection", "Connection metatypes are not supported yet.");
		}
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO metatypes (id, config_id, name, abbreviation, is_connection, architecture_layer_id, sort_order,
				    scope, created_by, base_metatype_id) VALUES (?, ?, ?, ?, false, ?, ?, 'organization', ?, ?)""", id,
				PackageImporter.INSTANCE_CONFIGURATION, name, abbreviation(input), input.architectureLayerId(),
				input.sortOrder(), principal.personId(), input.baseMetatypeId());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", name);
		if (input.baseMetatypeId() != null) {
			details.put("baseMetatypeId", input.baseMetatypeId().toString());
		}
		this.audit.changed("metamodel.metatype-created", principal, "metatype", id, details);
		return id;
	}

	@Transactional
	public void update(OeaPrincipal principal, UUID id, Input input) {
		this.effects.lock();
		Stored stored = stored(id);
		if (!"organization".equals(stored.scope())) {
			throw MetamodelChangeException.readOnly("metatype");
		}
		String name = validate(input, id);
		if (input.connection() != stored.connection()) {
			throw MetamodelChangeException.invalid("isConnection", "Whether a metatype is a connection cannot change.");
		}
		MetamodelEffects.Snapshot before = this.effects.snapshot();
		this.jdbc.update("""
				UPDATE metatypes SET name = ?, abbreviation = ?, architecture_layer_id = ?, sort_order = ?,
				       base_metatype_id = ? WHERE id = ?""", name, abbreviation(input), input.architectureLayerId(),
				input.sortOrder(), input.baseMetatypeId(), id);
		List<String> changed = new ArrayList<>();
		compare(changed, "name", stored.name(), name);
		compare(changed, "abbreviation", stored.abbreviation(), abbreviation(input));
		compare(changed, "architectureLayerId", stored.architectureLayerId(), input.architectureLayerId());
		compare(changed, "sortOrder", stored.sortOrder(), input.sortOrder());
		compare(changed, "baseMetatypeId", stored.baseMetatypeId(), input.baseMetatypeId());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("changedFields", changed);
		if (changed.contains("baseMetatypeId")) {
			// removed and added inherited properties take effect as for a mapping (design D4)
			MetamodelEffects.Effects effects = this.effects.apply(before, principal);
			details.put("entitiesWithRemovedValues", effects.removed().values().stream().mapToInt(Integer::intValue).sum());
			details.put("entitiesMarked", effects.marked().values().stream().mapToInt(Integer::intValue).sum());
		}
		this.audit.changed("metamodel.metatype-updated", principal, "metatype", id, details);
	}

	@Transactional
	public void delete(OeaPrincipal principal, UUID id) {
		this.effects.lock();
		Stored stored = stored(id);
		if (!"organization".equals(stored.scope())) {
			throw MetamodelChangeException.readOnly("metatype");
		}
		long entities = count("SELECT count(*) FROM entities WHERE metatype_id = ?", id);
		long catalogs = count("SELECT count(*) FROM catalogs WHERE primary_metatype_id = ?", id);
		long subtypes = count("SELECT count(*) FROM metatypes WHERE base_metatype_id = ?", id);
		if (entities > 0 || catalogs > 0 || subtypes > 0) {
			throw MetamodelChangeException.inUse("The metatype has " + entities + " entities, " + catalogs
					+ " catalogs and " + subtypes + " subtypes.", entities);
		}
		this.jdbc.update("DELETE FROM metatype_property_mappings WHERE metatype_id = ?", id);
		this.jdbc.update("DELETE FROM metatypes WHERE id = ?", id);
		this.audit.changed("metamodel.metatype-deleted", principal, "metatype", id, Map.of("name", stored.name()));
	}

	/** Checks name, abbreviation and base type; returns the stripped name. */
	private String validate(Input input, UUID id) {
		if (!PackageImporter.INSTANCE_CONFIGURATION.equals(input.configId())) {
			throw MetamodelChangeException.invalid("configId", "Only the instance configuration exists.");
		}
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
			throw MetamodelChangeException.invalid("name", "The name must have 1 to 100 characters.");
		}
		String abbreviation = abbreviation(input);
		if (abbreviation != null && abbreviation.length() > ABBREVIATION_MAX_LENGTH) {
			throw MetamodelChangeException.invalid("abbreviation", "The abbreviation has more than 10 characters.");
		}
		if (count("SELECT count(*) FROM metatypes WHERE config_id = ? AND lower(name) = lower(?) AND id IS DISTINCT FROM ?::uuid",
				PackageImporter.INSTANCE_CONFIGURATION, name, id) > 0) {
			throw MetamodelChangeException.nameTaken("name");
		}
		UUID base = input.baseMetatypeId();
		if (base != null) {
			if (count("SELECT count(*) FROM metatypes WHERE id = ? AND NOT is_connection", base) == 0) {
				throw MetamodelChangeException.invalid("baseMetatypeId", "The base type does not exist.");
			}
			Map<UUID, UUID> bases = this.effects.bases();
			if (id != null && MetamodelEffects.chain(base, bases).contains(id)) {
				throw MetamodelChangeException.invalid("baseMetatypeId", "The base type would form a cycle.");
			}
			if (id != null) {
				checkUniqueInChain(id, base, bases);
			}
		}
		return name;
	}

	/** A property may be mapped once in the chain of the new base types and the subtree of the metatype. */
	private void checkUniqueInChain(UUID id, UUID base, Map<UUID, UUID> bases) {
		List<UUID> above = MetamodelEffects.chain(base, bases);
		Set<UUID> below = MetamodelEffects.subtree(id, bases);
		Integer duplicates = this.jdbc.queryForObject("""
				SELECT count(*) FROM metatype_property_mappings a JOIN metatype_property_mappings b
				    ON a.property_def_id = b.property_def_id
				WHERE a.metatype_id = ANY (?::uuid[]) AND b.metatype_id = ANY (?::uuid[])""", Integer.class,
				MetamodelEffects.ids(above), MetamodelEffects.ids(below));
		if (duplicates != null && duplicates > 0) {
			throw MetamodelChangeException.conflict("A property would be mapped twice in the base type chain.");
		}
	}

	private Stored stored(UUID id) {
		return this.jdbc.query("""
				SELECT name, abbreviation, is_connection, architecture_layer_id, sort_order, scope, base_metatype_id
				FROM metatypes WHERE id = ?""", (rs, n) -> new Stored(rs.getString(1), rs.getString(2), rs.getBoolean(3),
				rs.getString(4), rs.getInt(5), rs.getString(6), rs.getObject(7, UUID.class)), id)
			.stream()
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("no metatype " + id));
	}

	private long count(String sql, Object... arguments) {
		Long count = this.jdbc.queryForObject(sql, Long.class, arguments);
		return (count != null) ? count : 0;
	}

	private static String abbreviation(Input input) {
		return (input.abbreviation() != null && !input.abbreviation().isBlank()) ? input.abbreviation().strip() : null;
	}

	private static void compare(List<String> changed, String field, Object before, Object after) {
		if (!Objects.equals(before, after)) {
			changed.add(field);
		}
	}

}
