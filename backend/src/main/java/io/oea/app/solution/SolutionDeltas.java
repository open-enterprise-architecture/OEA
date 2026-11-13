package io.oea.app.solution;

import io.oea.app.entity.EntityInput;
import io.oea.app.entity.EntityValidator;
import io.oea.app.entity.Issue;
import io.oea.app.entity.MetatypeRules;
import io.oea.app.entity.PropertyWriteAccess;
import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.core.entity.TypedValue;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The entity deltas of a solution in project mode (US-040, US-041, US-042, REQ-040; design D2–D8 of
 * us-040-entity-deltas, D2–D5 of us-041-modified-delta). A delta {@code new} is checked like the
 * write of a new entity and writes no entity before the go-live (S14); {@code modified} changes an
 * entity of the baseline, checked like its update, with the before values recorded from the
 * catalog; {@code retiring} names an entity of the baseline. The permissions are checked from the
 * contract.
 */
@Service
public class SolutionDeltas {

	/** At most this many deltas per solution (design D7 of us-040-entity-deltas). */
	public static final int MAX_DELTAS = 1000;

	static final String DUPLICATE = "A delta already exists for this entity in this solution";

	static final String NOT_IN_LANDSCAPE = "Entity not found in the current landscape";

	static final String NOTHING_CHANGES = "The delta changes nothing.";

	private static final Set<String> COMPLETED = Set.of("implemented", "archived");

	private static final String NAME = "name";

	private static final String DESCRIPTION = "description";

	private final JdbcTemplate jdbc;

	private final EntityStore store;

	private final PropertyWriteAccess writeAccess;

	private final PropertyAccess readAccess;

	private final AuditWriter audit;

	private final Clock clock;

	SolutionDeltas(JdbcTemplate jdbc, EntityStore store, PropertyWriteAccess writeAccess, PropertyAccess readAccess,
			AuditWriter audit, Clock clock) {
		this.jdbc = jdbc;
		this.store = store;
		this.writeAccess = writeAccess;
		this.readAccess = readAccess;
		this.audit = audit;
		this.clock = clock;
	}

	/** The type of a delta. */
	public enum Type {

		NEW("new"), MODIFIED("modified"), RETIRING("retiring");

		private final String value;

		Type(String value) {
			this.value = value;
		}

		public String value() {
			return this.value;
		}

		static Type of(String value) {
			for (Type type : values()) {
				if (type.value.equals(value)) {
					return type;
				}
			}
			throw new IllegalArgumentException(value);
		}

	}

	/** A delta to capture; the fields that do not belong to its type must be absent. */
	public record NewDelta(Type type, Integer entityId, UUID metatypeId, String name, String description,
			List<EntityInput.PropertyInput> properties) {

		public NewDelta {
			properties = (properties != null) ? List.copyOf(properties) : List.of();
		}

	}

	/** The intended changes of a delta {@code modified} (replace semantics on an edit). */
	public record Modification(String name, String description, List<EntityInput.PropertyInput> properties) {

		public Modification {
			properties = (properties != null) ? List.copyOf(properties) : List.of();
		}

	}

	/** A typed value in the app layer: exactly one component is set. */
	public record Typed(String text, Long integer, Boolean bool, LocalDate date, String enumValue,
			Reference reference) {
	}

	/**
	 * A value of a delta {@code new} in its typed component; {@code redacted} without a value if the
	 * reader may not read it.
	 */
	public record Value(UUID propertyDefId, String propertyName, String dataType, boolean redacted, String text,
			Long integer, Boolean bool, LocalDate date, String enumValue, Reference reference) {
	}

	/**
	 * A change of a delta {@code modified}: {@code field} is {@code name}, {@code description} or
	 * {@code property}; {@code before} and {@code after} are {@code null} for no value and when
	 * {@code redacted}.
	 */
	public record Change(String field, UUID propertyDefId, String propertyName, String dataType, boolean redacted,
			Typed before, Typed after) {
	}

	/** The target of a reference value. */
	public record Reference(int id, String name, String metatypeAbbreviation) {
	}

	/** A captured delta; for {@code modified} and {@code retiring} the current name and metatype. */
	public record Delta(UUID id, Type type, Integer entityId, String name, UUID metatypeId, String metatypeName,
			String description, List<Value> properties, List<Change> changes, Instant createdAt, Instant updatedAt) {
	}

	/** A computed change before it is stored: a system field or a property, with typed values. */
	private record Computed(String systemField, UUID propertyDefId, String label, TypedValue before,
			TypedValue after) {

		String key() {
			return (this.systemField != null) ? this.systemField : this.propertyDefId.toString();
		}

	}

	/**
	 * Captures a delta and audits {@code solution.delta-added}.
	 *
	 * @throws NoSuchElementException for an unknown solution (404)
	 * @throws SolutionCompletedException for an implemented or archived solution (409)
	 * @throws PermissionDeniedException for a restricted value without write access, or an account
	 * that is not a person (403)
	 * @throws InvalidSolutionException for a delta that violates a rule (422)
	 */
	@Transactional
	public Delta create(OeaPrincipal principal, UUID solutionId, NewDelta input) {
		requireOpen(solutionId);
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		Map<UUID, TypedValue> values = Map.of();
		List<Computed> changes = List.of();
		switch (input.type()) {
			case NEW -> values = checkNew(principal, input);
			case RETIRING -> checkRetiring(input);
			case MODIFIED -> {
				if (input.metatypeId() != null) {
					throw new InvalidSolutionException(List.of(new Issue("metatypeId", "NOT_SUPPORTED",
							"A modified delta keeps the metatype of its entity.")));
				}
				changes = computeChanges(principal, input.entityId(),
						new Modification(input.name(), input.description(), input.properties()), Map.of(), false);
			}
		}
		Integer count = this.jdbc.queryForObject("SELECT count(*) FROM entity_deltas WHERE solution_id = ?",
				Integer.class, solutionId);
		if (count != null && count >= MAX_DELTAS) {
			throw new InvalidSolutionException("deltas", "A solution holds at most " + MAX_DELTAS + " deltas.");
		}
		UUID id = UUID.randomUUID();
		boolean isNew = input.type() == Type.NEW;
		try {
			this.jdbc.update("""
					INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, metatype_id, name, description, created_by)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", id, solutionId, input.type().value(), input.entityId(),
					isNew ? input.metatypeId() : null, isNew ? input.name() : null,
					isNew ? ((input.description() != null) ? input.description() : "") : null, person);
		}
		catch (DuplicateKeyException ex) {
			throw new InvalidSolutionException(isNew ? NAME : "entityId", DUPLICATE);
		}
		values.forEach((definition, value) -> insertValue(id, null, definition, null, value));
		changes.forEach((change) -> insertValue(id, change.systemField(), change.propertyDefId(), change.before(),
				change.after()));
		Delta delta = read(solutionId, id, this.readAccess.of(principal)).get(0);
		this.audit.append(event("solution.delta-added", principal, solutionId, delta, changes));
		return delta;
	}

	/**
	 * Replaces the changes of a delta {@code modified} and audits {@code solution.delta-updated}
	 * (design D3 of us-041-modified-delta).
	 *
	 * @throws NoSuchElementException for an unknown solution or a delta of another solution (404)
	 * @throws SolutionCompletedException for an implemented or archived solution (409)
	 * @throws PermissionDeniedException for a restricted value without write access (403)
	 * @throws InvalidSolutionException for a delta of another type or a change that violates a rule
	 * (422)
	 */
	@Transactional
	public Delta update(OeaPrincipal principal, UUID solutionId, UUID deltaId, Modification input) {
		List<Map<String, Object>> locked = this.jdbc.queryForList(
				"SELECT delta_type, entity_id FROM entity_deltas WHERE id = ? AND solution_id = ? FOR UPDATE", deltaId,
				solutionId);
		if (locked.isEmpty()) {
			status(solutionId);
			throw new NoSuchElementException("delta " + deltaId);
		}
		requireOpen(solutionId);
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		if (!Type.MODIFIED.value().equals(locked.get(0).get("delta_type"))) {
			throw new InvalidSolutionException(List.of(new Issue("deltaType", "NOT_SUPPORTED",
					"Only a modified delta can be edited.")));
		}
		int entityId = ((Number) locked.get(0).get("entity_id")).intValue();
		Map<String, TypedValue> recorded = new HashMap<>();
		List<Computed> hidden = new ArrayList<>();
		PropertyAccess.Snapshot reader = this.readAccess.of(principal);
		Set<UUID> sent = new HashSet<>();
		input.properties().forEach((property) -> sent.add(property.propertyDefId()));
		this.jdbc.query("""
				SELECT system_field, property_def_id, before_value_text, before_value_int, before_value_bool,
				       before_value_date, before_value_ref, before_value_enum, after_value_text, after_value_int,
				       after_value_bool, after_value_date, after_value_ref, after_value_enum, p.name
				FROM entity_delta_property_values v LEFT JOIN property_definitions p ON p.id = v.property_def_id
				WHERE delta_id = ?""", (ResultSet rs) -> {
			String field = rs.getString(1);
			UUID property = rs.getObject(2, UUID.class);
			recorded.put((field != null) ? field : property.toString(), typed(rs, 3));
			// a change the editor may not read cannot be resent, so it stays as recorded
			if (property != null && !sent.contains(property) && !reader.readableFor(property, entityId)) {
				hidden.add(new Computed(null, property, rs.getString(15), typed(rs, 3), typed(rs, 9)));
			}
		}, deltaId);
		List<Computed> changes = new ArrayList<>(computeChanges(principal, entityId, input, recorded, !hidden.isEmpty()));
		changes.addAll(hidden);
		this.jdbc.update("DELETE FROM entity_delta_property_values WHERE delta_id = ?", deltaId);
		changes.forEach((change) -> insertValue(deltaId, change.systemField(), change.propertyDefId(),
				change.before(), change.after()));
		this.jdbc.update("UPDATE entity_deltas SET updated_at = now(), updated_by = ? WHERE id = ?", person, deltaId);
		Delta delta = read(solutionId, deltaId, this.readAccess.of(principal)).get(0);
		this.audit.append(event("solution.delta-updated", principal, solutionId, delta, changes));
		return delta;
	}

	/**
	 * The deltas of a solution, {@code new}, then {@code modified}, then {@code retiring}, each by
	 * name.
	 *
	 * @throws NoSuchElementException for an unknown solution (404)
	 */
	@Transactional(readOnly = true)
	public List<Delta> list(OeaPrincipal principal, UUID solutionId) {
		status(solutionId);
		return read(solutionId, null, this.readAccess.of(principal));
	}

	/**
	 * Removes a delta with its values and audits {@code solution.delta-removed}.
	 *
	 * @throws NoSuchElementException for an unknown solution or a delta of another solution (404)
	 * @throws SolutionCompletedException for an implemented or archived solution (409)
	 */
	@Transactional
	public void delete(OeaPrincipal principal, UUID solutionId, UUID deltaId) {
		requireOpen(solutionId);
		List<Delta> found = read(solutionId, deltaId, this.readAccess.of(principal));
		if (found.isEmpty()) {
			throw new NoSuchElementException("delta " + deltaId);
		}
		this.jdbc.update("DELETE FROM entity_deltas WHERE id = ? AND solution_id = ?", deltaId, solutionId);
		this.audit.append(event("solution.delta-removed", principal, solutionId, found.get(0), List.of()));
	}

	private void requireOpen(UUID solutionId) {
		if (COMPLETED.contains(status(solutionId))) {
			throw new SolutionCompletedException();
		}
	}

	private String status(UUID solutionId) {
		return this.jdbc.queryForList("SELECT status FROM solutions WHERE id = ?", String.class, solutionId)
			.stream()
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("solution " + solutionId));
	}

	/** The checks of an entity write (design D2 of us-040-entity-deltas); the values after defaults. */
	private Map<UUID, TypedValue> checkNew(OeaPrincipal principal, NewDelta input) {
		EntityInput entity = new EntityInput(input.name(), input.metatypeId(), null, input.description(), null, null,
				null, input.properties());
		PropertyAccess.Snapshot access = this.writeAccess.require(principal, List.of(entity));
		List<Issue> errors = new ArrayList<>();
		if (input.entityId() != null) {
			errors.add(new Issue("entityId", "NOT_SUPPORTED", "A new entity has no entity ID."));
		}
		if (input.metatypeId() == null) {
			errors.add(new Issue("metatypeId", "REQUIRED", "The metatype is required."));
			EntityValidator.Checked named = EntityValidator.check(
					new EntityInput(input.name(), null, null, input.description(), null, null, null, List.of()),
					MetatypeRules.load(this.jdbc, LocalDate.now(this.clock)), null, Map.of(), this.store::isActive);
			named.errors().stream().filter((issue) -> !"metatypeId".equals(issue.field())).forEach(errors::add);
			throw new InvalidSolutionException(errors);
		}
		MetatypeRules rules = MetatypeRules.load(this.jdbc, LocalDate.now(this.clock))
			.forWriter(PropertyWriteAccess.writer(access));
		EntityValidator.Checked checked = EntityValidator.check(entity, rules, null, Map.of(), this.store::isActive);
		errors.addAll(checked.errors());
		if (!errors.isEmpty()) {
			throw new InvalidSolutionException(errors);
		}
		return checked.values();
	}

	/** An entity of the baseline and nothing else (design D3 of us-040-entity-deltas). */
	private void checkRetiring(NewDelta input) {
		List<Issue> errors = new ArrayList<>();
		if (input.metatypeId() != null) {
			errors.add(new Issue("metatypeId", "NOT_SUPPORTED", "A retiring delta names only the entity."));
		}
		if (input.name() != null) {
			errors.add(new Issue(NAME, "NOT_SUPPORTED", "A retiring delta names only the entity."));
		}
		if (input.description() != null) {
			errors.add(new Issue(DESCRIPTION, "NOT_SUPPORTED", "A retiring delta names only the entity."));
		}
		if (!input.properties().isEmpty()) {
			errors.add(new Issue("properties", "NOT_SUPPORTED", "A retiring delta names only the entity."));
		}
		if (input.entityId() == null || !inBaseline(input.entityId())) {
			errors.add(new Issue("entityId", "NOT_IN_LANDSCAPE", NOT_IN_LANDSCAPE));
		}
		if (!errors.isEmpty()) {
			throw new InvalidSolutionException(errors);
		}
	}

	private boolean inBaseline(int entityId) {
		return Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM entities e JOIN metatypes m ON m.id = e.metatype_id
				               WHERE e.id = ? AND e.deleted_at IS NULL AND NOT m.is_connection)""", Boolean.class,
				entityId));
	}

	/**
	 * The changes of a modification against the entity, checked like its update (design D2, D3 of
	 * us-041-modified-delta). The before value of a field is its {@code recorded} one if the delta
	 * already holds it, else the current value; a field whose new value equals that before value
	 * is no change.
	 */
	private List<Computed> computeChanges(OeaPrincipal principal, Integer entityId, Modification input,
			Map<String, TypedValue> recorded, boolean othersRemain) {
		StoredEntity stored = (entityId != null) ? this.store.lockForShare(entityId)
			.filter((entity) -> !entity.deleted() && inBaseline(entity.id()))
			.orElse(null) : null;
		if (stored == null) {
			throw new InvalidSolutionException(List.of(new Issue("entityId", "NOT_IN_LANDSCAPE", NOT_IN_LANDSCAPE)));
		}
		PropertyAccess.Snapshot access = this.writeAccess.require(principal, input.properties(), stored.id());
		Map<UUID, TypedValue> current = this.store.values(stored.id());
		EntityInput entity = new EntityInput((input.name() != null) ? input.name() : stored.name(), stored.metatypeId(),
				null, input.description(), null, null, null, input.properties());
		MetatypeRules rules = MetatypeRules.load(this.jdbc, LocalDate.now(this.clock))
			.forWriter(PropertyWriteAccess.writer(access));
		EntityValidator.Checked checked = EntityValidator.check(entity, rules, stored, current, this.store::isActive);
		if (!checked.valid()) {
			throw new InvalidSolutionException(checked.errors());
		}
		List<Computed> changes = new ArrayList<>();
		if (input.name() != null) {
			add(changes, recorded, new Computed(NAME, null, NAME, TypedValue.ofText(stored.name()),
					TypedValue.ofText(input.name())));
		}
		if (input.description() != null) {
			add(changes, recorded, new Computed(DESCRIPTION, null, DESCRIPTION,
					(stored.description() != null) ? TypedValue.ofText(stored.description()) : null,
					TypedValue.ofText(input.description())));
		}
		Set<UUID> seen = new HashSet<>();
		for (EntityInput.PropertyInput property : input.properties()) {
			UUID definition = property.propertyDefId();
			MetatypeRules.Mapping mapping = checked.metatype().mappings().get(definition);
			if (mapping == null || !seen.add(definition)) {
				// an unmapped property is ignored, as in the batch write (REQ-149)
				continue;
			}
			add(changes, recorded, new Computed(null, definition, mapping.definition().name(), current.get(definition),
					checked.values().get(definition)));
		}
		if (changes.isEmpty() && !othersRemain) {
			throw new InvalidSolutionException("properties", NOTHING_CHANGES);
		}
		return changes;
	}

	private static void add(List<Computed> changes, Map<String, TypedValue> recorded, Computed change) {
		TypedValue before = recorded.containsKey(change.key()) ? recorded.get(change.key()) : change.before();
		if (!Objects.equals(before, change.after())) {
			changes.add(new Computed(change.systemField(), change.propertyDefId(), change.label(), before,
					change.after()));
		}
	}

	private void insertValue(UUID deltaId, String systemField, UUID definition, TypedValue before, TypedValue after) {
		this.jdbc.update("""
				INSERT INTO entity_delta_property_values (id, delta_id, system_field, property_def_id,
				    before_value_text, before_value_int, before_value_bool, before_value_date, before_value_ref, before_value_enum,
				    after_value_text, after_value_int, after_value_bool, after_value_date, after_value_ref, after_value_enum)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), deltaId, systemField,
				definition, text(before), integer(before), bool(before), date(before), reference(before),
				enumValue(before), text(after), integer(after), bool(after), date(after), reference(after),
				enumValue(after));
	}

	/** The deltas of the solution, or the one with {@code deltaId} (design D5, D4 of us-041). */
	private List<Delta> read(UUID solutionId, UUID deltaId, PropertyAccess.Snapshot access) {
		List<Object> arguments = new ArrayList<>(List.of(solutionId));
		String only = "";
		if (deltaId != null) {
			only = " AND d.id = ?";
			arguments.add(deltaId);
		}
		List<Delta> deltas = this.jdbc.query("""
				SELECT d.id, d.delta_type, d.entity_id, coalesce(d.name, e.name), m.id, m.name, d.description, d.created_at,
				       d.updated_at
				FROM entity_deltas d
				LEFT JOIN entities e ON e.id = d.entity_id
				JOIN metatypes m ON m.id = coalesce(d.metatype_id, e.metatype_id)
				WHERE d.solution_id = ?""" + only
				+ " ORDER BY CASE d.delta_type WHEN 'new' THEN 0 WHEN 'modified' THEN 1 ELSE 2 END,"
				+ " lower(coalesce(d.name, e.name)), d.id", SolutionDeltas::delta, arguments.toArray());
		if (deltas.stream().allMatch((delta) -> delta.type() == Type.RETIRING)) {
			return deltas;
		}
		Map<UUID, List<Value>> values = values(solutionId, deltaId, access);
		Map<UUID, List<Change>> changes = changes(solutionId, deltaId, access);
		return deltas.stream()
			.map((delta) -> new Delta(delta.id(), delta.type(), delta.entityId(), delta.name(), delta.metatypeId(),
					delta.metatypeName(), delta.description(), values.getOrDefault(delta.id(), List.of()),
					changes.getOrDefault(delta.id(), List.of()), delta.createdAt(), delta.updatedAt()))
			.toList();
	}

	private Map<UUID, List<Value>> values(UUID solutionId, UUID deltaId, PropertyAccess.Snapshot access) {
		List<Object> arguments = new ArrayList<>(List.of(solutionId));
		String only = "";
		if (deltaId != null) {
			only = " AND d.id = ?";
			arguments.add(deltaId);
		}
		Map<UUID, List<Value>> values = new HashMap<>();
		this.jdbc.query("""
				SELECT v.delta_id, p.id, p.name, p.data_type, v.after_value_text, v.after_value_int, v.after_value_bool,
				       v.after_value_date, v.after_value_ref, v.after_value_enum, r.name, rm.abbreviation
				FROM entity_delta_property_values v
				JOIN entity_deltas d ON d.id = v.delta_id
				JOIN property_definitions p ON p.id = v.property_def_id
				LEFT JOIN entities r ON r.id = v.after_value_ref
				LEFT JOIN metatypes rm ON rm.id = r.metatype_id
				WHERE d.delta_type = 'new' AND d.solution_id = ?""" + only + " ORDER BY lower(p.name), p.id",
				(ResultSet rs) -> {
					UUID property = rs.getObject(2, UUID.class);
					Value value;
					if (!access.readable(property)) {
						value = new Value(property, rs.getString(3), rs.getString(4), true, null, null, null, null, null,
								null);
					}
					else {
						Typed typed = typed(rs, 5, 11);
						value = new Value(property, rs.getString(3), rs.getString(4), false, typed.text(),
								typed.integer(), typed.bool(), typed.date(), typed.enumValue(), typed.reference());
					}
					values.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new ArrayList<>()).add(value);
				}, arguments.toArray());
		return values;
	}

	/** The changes of the deltas {@code modified}: name, description, then properties by name. */
	private Map<UUID, List<Change>> changes(UUID solutionId, UUID deltaId, PropertyAccess.Snapshot access) {
		List<Object> arguments = new ArrayList<>(List.of(solutionId));
		String only = "";
		if (deltaId != null) {
			only = " AND d.id = ?";
			arguments.add(deltaId);
		}
		Map<UUID, List<Change>> changes = new HashMap<>();
		this.jdbc.query("""
				SELECT v.delta_id, d.entity_id, v.system_field, p.id, p.name, p.data_type,
				       v.before_value_text, v.before_value_int, v.before_value_bool, v.before_value_date, v.before_value_ref,
				       v.before_value_enum, br.name, brm.abbreviation,
				       v.after_value_text, v.after_value_int, v.after_value_bool, v.after_value_date, v.after_value_ref,
				       v.after_value_enum, ar.name, arm.abbreviation
				FROM entity_delta_property_values v
				JOIN entity_deltas d ON d.id = v.delta_id
				LEFT JOIN property_definitions p ON p.id = v.property_def_id
				LEFT JOIN entities br ON br.id = v.before_value_ref
				LEFT JOIN metatypes brm ON brm.id = br.metatype_id
				LEFT JOIN entities ar ON ar.id = v.after_value_ref
				LEFT JOIN metatypes arm ON arm.id = ar.metatype_id
				WHERE d.delta_type = 'modified' AND d.solution_id = ?""" + only
				+ " ORDER BY v.system_field IS NULL, v.system_field = 'description', lower(p.name), p.id",
				(ResultSet rs) -> {
					String systemField = rs.getString(3);
					Change change;
					if (systemField != null) {
						change = new Change(systemField, null, null, null, false, typed(rs, 7, 13), typed(rs, 15, 21));
					}
					else {
						UUID property = rs.getObject(4, UUID.class);
						boolean readable = access.readableFor(property, rs.getInt(2));
						change = new Change("property", property, rs.getString(5), rs.getString(6), !readable,
								readable ? typed(rs, 7, 13) : null, readable ? typed(rs, 15, 21) : null);
					}
					changes.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new ArrayList<>()).add(change);
				}, arguments.toArray());
		return changes;
	}

	/** The typed value in the six columns from {@code first}, or {@code null} for no value. */
	private static TypedValue typed(ResultSet rs, int first) throws SQLException {
		java.sql.Date date = rs.getDate(first + 3);
		TypedValue value = new TypedValue(rs.getString(first), rs.getObject(first + 1, Long.class),
				rs.getObject(first + 2, Boolean.class), (date != null) ? date.toLocalDate() : null,
				rs.getObject(first + 4, Integer.class), rs.getString(first + 5));
		return (value.asText() != null) ? value : null;
	}

	/** The app value in the six columns from {@code first}, with the reference target from {@code ref}. */
	private static Typed typed(ResultSet rs, int first, int ref) throws SQLException {
		TypedValue value = typed(rs, first);
		if (value == null) {
			return null;
		}
		Reference reference = (value.reference() != null)
				? new Reference(value.reference(), rs.getString(ref), rs.getString(ref + 1)) : null;
		return new Typed(value.text(), value.integer(), value.bool(), value.date(), value.enumValue(), reference);
	}

	private static String text(TypedValue value) {
		return (value != null) ? value.text() : null;
	}

	private static Long integer(TypedValue value) {
		return (value != null) ? value.integer() : null;
	}

	private static Boolean bool(TypedValue value) {
		return (value != null) ? value.bool() : null;
	}

	private static java.sql.Date date(TypedValue value) {
		return (value != null && value.date() != null) ? java.sql.Date.valueOf(value.date()) : null;
	}

	private static Integer reference(TypedValue value) {
		return (value != null) ? value.reference() : null;
	}

	private static String enumValue(TypedValue value) {
		return (value != null) ? value.enumValue() : null;
	}

	private static Delta delta(ResultSet rs, int row) throws SQLException {
		java.sql.Timestamp updated = rs.getTimestamp(9);
		return new Delta(rs.getObject(1, UUID.class), Type.of(rs.getString(2)), rs.getObject(3, Integer.class),
				rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7), List.of(), List.of(),
				rs.getTimestamp(8).toInstant(), (updated != null) ? updated.toInstant() : null);
	}

	/**
	 * The audit event of a delta, without values (design D8 of us-040-entity-deltas, D5 of
	 * us-041-modified-delta): a delta {@code modified} names its changed fields.
	 */
	private static AuditEvent event(String type, OeaPrincipal principal, UUID solutionId, Delta delta,
			List<Computed> changes) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("solutionId", solutionId.toString());
		details.put("deltaId", delta.id().toString());
		details.put("deltaType", delta.type().value());
		if (delta.type() == Type.NEW) {
			details.put("metatypeId", delta.metatypeId().toString());
			details.put("name", delta.name());
		}
		else {
			details.put("entityId", delta.entityId());
		}
		if (delta.type() == Type.MODIFIED && !changes.isEmpty()) {
			details.put("fields", changes.stream().map(Computed::label).toList());
		}
		return new AuditEvent(type, AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS,
				AuditEvent.Source.API, Sessions.actor(principal), "solution", solutionId.toString(), details);
	}

}
