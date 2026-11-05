package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.PermissionResolver;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
 * The metamodel configurations: the instance configuration and the extensions of solutions, their
 * edit mode and the check that every content write passes (design D1, D2 of
 * us-037-solution-extensions-and-lock-mode; US-035, US-037, REQ-035, REQ-037).
 */
@Service
public class MetamodelConfigurations {

	static final String METAMODEL_WRITE = "metamodel:write";

	static final String SOLUTION_METAMODEL_WRITE = "solution-metamodel:write";

	public static final String GUI_AND_IMPORT = "gui-and-import";

	public static final String IMPORT_ONLY = "import-only";

	static final int NAME_MAX_LENGTH = 100;

	private static final String COLUMNS = """
			c.id, c.scope, c.parent_id, c.solution_id, c.name, c.edit_mode, c.version, c.last_modified_at,
			(SELECT count(*) FROM metatypes m WHERE m.config_id = c.id)""";

	private final JdbcTemplate jdbc;

	private final MetamodelEffects effects;

	private final MetamodelAudit audit;

	private final MetamodelVersions versions;

	private final PermissionResolver permissions;

	public MetamodelConfigurations(JdbcTemplate jdbc, MetamodelEffects effects, MetamodelAudit audit,
			MetamodelVersions versions, PermissionResolver permissions) {
		this.jdbc = jdbc;
		this.effects = effects;
		this.audit = audit;
		this.versions = versions;
		this.permissions = permissions;
	}

	/** A configuration with the number of its own metatypes. */
	public record Configuration(UUID id, String scope, UUID parentId, UUID solutionId, String name, String editMode,
			int version, Instant lastModifiedAt, int metatypeCount) {

		public boolean instance() {
			return "instance".equals(this.scope);
		}

	}

	/** A new extension; {@code editMode} {@code null} means {@code gui-and-import}. */
	public record NewExtension(UUID solutionId, String name, String editMode) {
	}

	/** The changeable fields of a configuration. */
	public record Change(String name, String editMode) {
	}

	/** The instance configuration first, then the extensions by name. */
	@Transactional(readOnly = true)
	public List<Configuration> list() {
		return this.jdbc.query("SELECT " + COLUMNS
				+ " FROM metamodel_configurations c ORDER BY c.scope <> 'instance', lower(c.name), c.id",
				MetamodelConfigurations::configuration);
	}

	/** @throws NoSuchElementException if the configuration does not exist (404) */
	@Transactional(readOnly = true)
	public Configuration get(UUID id) {
		return find(id).orElseThrow(() -> new NoSuchElementException("no metamodel configuration " + id));
	}

	@Transactional
	public UUID create(OeaPrincipal principal, NewExtension input) {
		this.effects.lock();
		String name = name(input.name(), null);
		String editMode = (input.editMode() != null) ? editMode(input.editMode()) : GUI_AND_IMPORT;
		if (input.solutionId() == null) {
			throw MetamodelChangeException.invalid("solutionId", "An extension names its solution.");
		}
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO metamodel_configurations (id, scope, parent_id, solution_id, name, edit_mode, last_modified_at,
				    last_modified_by_person_id) VALUES (?, 'solution', ?, ?, ?, ?, ?, ?)""", id,
				PackageImporter.INSTANCE_CONFIGURATION, input.solutionId(), name, editMode,
				Timestamp.from(this.effects.now()), principal.personId());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", name);
		details.put("solutionId", input.solutionId().toString());
		details.put("editMode", editMode);
		this.audit.changed("metamodel.configuration-created", principal, "metamodel-configuration", id, details);
		return id;
	}

	/**
	 * Renames a configuration or switches its edit mode; possible in {@code import-only}, so that
	 * administrators can unlock (design D2).
	 *
	 * @param expectedVersion the version of {@code If-Match}, or {@code null}
	 */
	@Transactional
	public void update(OeaPrincipal principal, UUID id, Change change, Long expectedVersion) {
		this.effects.lock();
		Configuration current = get(id);
		requirePermission(principal, current.instance());
		this.versions.check(MetamodelVersions.Element.CONFIGURATION, id, expectedVersion);
		String name = name(change.name(), id);
		String editMode = editMode(change.editMode());
		this.jdbc.update("""
				UPDATE metamodel_configurations SET name = ?, edit_mode = ?, last_modified_at = ?,
				    last_modified_by_person_id = ?, last_modified_by_admin_id = NULL WHERE id = ?""", name, editMode,
				Timestamp.from(this.effects.now()), principal.personId(), id);
		this.versions.increment(MetamodelVersions.Element.CONFIGURATION, id);
		List<String> changed = new ArrayList<>();
		if (!Objects.equals(current.name(), name)) {
			changed.add("name");
		}
		if (!Objects.equals(current.editMode(), editMode)) {
			changed.add("editMode");
		}
		this.audit.changed("metamodel.configuration-updated", principal, "metamodel-configuration", id,
				Map.of("changedFields", changed));
		if (changed.contains("editMode")) {
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("configId", id.toString());
			details.put("before", current.editMode());
			details.put("after", editMode);
			this.audit.changed("metamodel.edit-mode-changed", principal, "metamodel-configuration", id, details);
		}
	}

	/** Deletes an extension without metatypes; the instance configuration stays (design D1). */
	@Transactional
	public void delete(OeaPrincipal principal, UUID id, Long expectedVersion) {
		this.effects.lock();
		Configuration current = get(id);
		if (current.instance()) {
			throw MetamodelChangeException.readOnly("instance configuration", "cannot be deleted.");
		}
		this.versions.check(MetamodelVersions.Element.CONFIGURATION, id, expectedVersion);
		if (current.metatypeCount() > 0) {
			throw MetamodelChangeException.inUse("The extension has " + current.metatypeCount() + " metatypes.",
					(long) current.metatypeCount());
		}
		this.jdbc.update("DELETE FROM metamodel_configurations WHERE id = ?", id);
		this.audit.changed("metamodel.configuration-deleted", principal, "metamodel-configuration", id,
				Map.of("name", current.name()));
	}

	/**
	 * The check of every content write, called after {@link MetamodelEffects#lock()}: the lock
	 * before the permission, because the lock applies whatever the permissions (design D2).
	 *
	 * @throws NoSuchElementException if the configuration does not exist (404)
	 * @throws MetamodelChangeException with {@code LOCKED} in {@code import-only} (403)
	 * @throws PermissionDeniedException without {@code metamodel:write} for the instance or
	 *     {@code solution-metamodel:write} for an extension (403)
	 */
	void requireWritable(OeaPrincipal principal, UUID configId) {
		Configuration configuration = get(configId);
		if (IMPORT_ONLY.equals(configuration.editMode())) {
			throw MetamodelChangeException.locked();
		}
		requirePermission(principal, configuration.instance());
	}

	/**
	 * {@link #requireWritable} for a write on a metatype; an unknown metatype counts as one of the
	 * instance, so that the later lookup answers 404 to metamodel administrators only.
	 */
	void requireWritableMetatype(OeaPrincipal principal, UUID metatypeId) {
		requireWritable(principal, configOf(metatypeId));
	}

	/** The configuration of a metatype, or the instance configuration for an unknown one. */
	UUID configOf(UUID metatypeId) {
		return this.jdbc.queryForList("SELECT config_id FROM metatypes WHERE id = ?", UUID.class, metatypeId)
			.stream()
			.findFirst()
			.orElse(PackageImporter.INSTANCE_CONFIGURATION);
	}

	/** Whether the configuration exists and is an extension. */
	boolean isExtension(UUID configId) {
		return find(configId).map((configuration) -> !configuration.instance()).orElse(false);
	}

	private void requirePermission(OeaPrincipal principal, boolean instance) {
		Set<String> held = this.permissions.permissions(principal);
		if (!held.contains(instance ? METAMODEL_WRITE : SOLUTION_METAMODEL_WRITE)) {
			throw new PermissionDeniedException();
		}
	}

	private java.util.Optional<Configuration> find(UUID id) {
		return this.jdbc.query("SELECT " + COLUMNS + " FROM metamodel_configurations c WHERE c.id = ?",
				MetamodelConfigurations::configuration, id)
			.stream()
			.findFirst();
	}

	/** Checks the name and that no other configuration uses it; returns it stripped. */
	private String name(String value, UUID id) {
		String name = (value != null) ? value.strip() : "";
		if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
			throw MetamodelChangeException.invalid("name", "The name must have 1 to 100 characters.");
		}
		Long taken = this.jdbc.queryForObject("""
				SELECT count(*) FROM metamodel_configurations WHERE lower(name) = lower(?) AND id IS DISTINCT FROM ?::uuid""",
				Long.class, name, id);
		if (taken != null && taken > 0) {
			throw MetamodelChangeException.nameTaken("name");
		}
		return name;
	}

	private static String editMode(String value) {
		if (!GUI_AND_IMPORT.equals(value) && !IMPORT_ONLY.equals(value)) {
			throw MetamodelChangeException.invalid("editMode", "The edit mode is gui-and-import or import-only.");
		}
		return value;
	}

	private static Configuration configuration(ResultSet rs, int row) throws SQLException {
		return new Configuration(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class),
				rs.getObject(4, UUID.class), rs.getString(5), rs.getString(6), rs.getInt(7),
				rs.getTimestamp(8).toInstant(), rs.getInt(9));
	}

}
