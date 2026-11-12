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
import io.oea.core.entity.TypedValue;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The entity deltas {@code new} and {@code retiring} of a solution in project mode (US-040,
 * US-042, REQ-040; design D2–D8 of us-040-entity-deltas). A delta {@code new} is checked like the
 * write of a new entity and writes no entity before the go-live (S14); {@code retiring} names an
 * entity of the baseline. The permissions are checked from the contract.
 */
@Service
public class SolutionDeltas {

	/** At most this many deltas per solution (design D7). */
	public static final int MAX_DELTAS = 1000;

	static final String DUPLICATE = "A delta already exists for this entity in this solution";

	static final String NOT_IN_LANDSCAPE = "Entity not found in the current landscape";

	private static final Set<String> COMPLETED = Set.of("implemented", "archived");

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

	/** The type of a delta; {@code modified} follows with US-041. */
	public enum Type {

		NEW("new"), RETIRING("retiring");

		private final String value;

		Type(String value) {
			this.value = value;
		}

		public String value() {
			return this.value;
		}

		static Type of(String value) {
			return "new".equals(value) ? NEW : RETIRING;
		}

	}

	/** A delta to capture; the fields that do not belong to its type must be absent. */
	public record NewDelta(Type type, Integer entityId, UUID metatypeId, String name, String description,
			List<EntityInput.PropertyInput> properties) {

		public NewDelta {
			properties = (properties != null) ? List.copyOf(properties) : List.of();
		}

	}

	/**
	 * A value of a delta {@code new} in its typed component; {@code redacted} without a value if the
	 * reader may not read it.
	 */
	public record Value(UUID propertyDefId, String propertyName, String dataType, boolean redacted, String text,
			Long integer, Boolean bool, LocalDate date, String enumValue, Reference reference) {
	}

	/** The target of a reference value. */
	public record Reference(int id, String name, String metatypeAbbreviation) {
	}

	/** A captured delta; for {@code retiring} the current name and metatype of the entity. */
	public record Delta(UUID id, Type type, Integer entityId, String name, UUID metatypeId, String metatypeName,
			String description, List<Value> properties, Instant createdAt) {
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
		Map<UUID, TypedValue> values = (input.type() == Type.NEW) ? checkNew(principal, input) : Map.of();
		if (input.type() == Type.RETIRING) {
			checkRetiring(input);
		}
		Integer count = this.jdbc.queryForObject("SELECT count(*) FROM entity_deltas WHERE solution_id = ?",
				Integer.class, solutionId);
		if (count != null && count >= MAX_DELTAS) {
			throw new InvalidSolutionException("deltas",
					"A solution holds at most " + MAX_DELTAS + " deltas.");
		}
		UUID id = UUID.randomUUID();
		try {
			this.jdbc.update("""
					INSERT INTO entity_deltas (id, solution_id, delta_type, entity_id, metatype_id, name, description, created_by)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", id, solutionId, input.type().value(), input.entityId(),
					input.metatypeId(), (input.type() == Type.NEW) ? input.name() : null,
					(input.type() == Type.NEW) ? ((input.description() != null) ? input.description() : "") : null,
					person);
		}
		catch (DuplicateKeyException ex) {
			throw new InvalidSolutionException((input.type() == Type.NEW) ? "name" : "entityId", DUPLICATE);
		}
		values.forEach((definition, value) -> this.jdbc.update("""
				INSERT INTO entity_delta_property_values (id, delta_id, property_def_id, after_value_text, after_value_int,
				    after_value_bool, after_value_date, after_value_ref, after_value_enum)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""", UUID.randomUUID(), id, definition, value.text(), value.integer(),
				value.bool(), (value.date() != null) ? java.sql.Date.valueOf(value.date()) : null, value.reference(),
				value.enumValue()));
		Delta delta = read(solutionId, id, this.readAccess.of(principal)).get(0);
		this.audit.append(event("solution.delta-added", principal, solutionId, delta));
		return delta;
	}

	/**
	 * The deltas of a solution, {@code new} before {@code retiring}, each by name.
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
		this.audit.append(event("solution.delta-removed", principal, solutionId, found.get(0)));
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

	/** The checks of an entity write (design D2); the values after defaults, if valid. */
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

	/** An entity of the baseline and nothing else (design D3). */
	private void checkRetiring(NewDelta input) {
		List<Issue> errors = new ArrayList<>();
		if (input.metatypeId() != null) {
			errors.add(new Issue("metatypeId", "NOT_SUPPORTED", "A retiring delta names only the entity."));
		}
		if (input.name() != null) {
			errors.add(new Issue("name", "NOT_SUPPORTED", "A retiring delta names only the entity."));
		}
		if (input.description() != null) {
			errors.add(new Issue("description", "NOT_SUPPORTED", "A retiring delta names only the entity."));
		}
		if (!input.properties().isEmpty()) {
			errors.add(new Issue("properties", "NOT_SUPPORTED", "A retiring delta names only the entity."));
		}
		if (input.entityId() == null || !Boolean.TRUE.equals(this.jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM entities e JOIN metatypes m ON m.id = e.metatype_id
				               WHERE e.id = ? AND e.deleted_at IS NULL AND NOT m.is_connection)""", Boolean.class,
				input.entityId()))) {
			errors.add(new Issue("entityId", "NOT_IN_LANDSCAPE", NOT_IN_LANDSCAPE));
		}
		if (!errors.isEmpty()) {
			throw new InvalidSolutionException(errors);
		}
	}

	/** The deltas of the solution, or the one with {@code deltaId} (design D5). */
	private List<Delta> read(UUID solutionId, UUID deltaId, PropertyAccess.Snapshot access) {
		List<Object> arguments = new ArrayList<>(List.of(solutionId));
		String only = "";
		if (deltaId != null) {
			only = " AND d.id = ?";
			arguments.add(deltaId);
		}
		List<Delta> deltas = this.jdbc.query("""
				SELECT d.id, d.delta_type, d.entity_id, coalesce(d.name, e.name), m.id, m.name, d.description, d.created_at
				FROM entity_deltas d
				LEFT JOIN entities e ON e.id = d.entity_id
				JOIN metatypes m ON m.id = coalesce(d.metatype_id, e.metatype_id)
				WHERE d.solution_id = ?""" + only
				+ " ORDER BY d.delta_type = 'retiring', lower(coalesce(d.name, e.name)), d.id",
				SolutionDeltas::delta, arguments.toArray());
		if (deltas.stream().noneMatch((delta) -> delta.type() == Type.NEW)) {
			return deltas;
		}
		Map<UUID, List<Value>> values = values(solutionId, deltaId, access);
		return deltas.stream()
			.map((delta) -> new Delta(delta.id(), delta.type(), delta.entityId(), delta.name(), delta.metatypeId(),
					delta.metatypeName(), delta.description(), values.getOrDefault(delta.id(), List.of()),
					delta.createdAt()))
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
				WHERE d.solution_id = ?""" + only + " ORDER BY lower(p.name), p.id", (ResultSet rs) -> {
			UUID property = rs.getObject(2, UUID.class);
			Value value;
			if (!access.readable(property)) {
				value = new Value(property, rs.getString(3), rs.getString(4), true, null, null, null, null, null, null);
			}
			else {
				java.sql.Date date = rs.getDate(8);
				Integer reference = rs.getObject(9, Integer.class);
				value = new Value(property, rs.getString(3), rs.getString(4), false, rs.getString(5),
						rs.getObject(6, Long.class), rs.getObject(7, Boolean.class),
						(date != null) ? date.toLocalDate() : null, rs.getString(10),
						(reference != null) ? new Reference(reference, rs.getString(11), rs.getString(12)) : null);
			}
			values.computeIfAbsent(rs.getObject(1, UUID.class), (k) -> new ArrayList<>()).add(value);
		}, arguments.toArray());
		return values;
	}

	private static Delta delta(ResultSet rs, int row) throws SQLException {
		return new Delta(rs.getObject(1, UUID.class), Type.of(rs.getString(2)), rs.getObject(3, Integer.class),
				rs.getString(4), rs.getObject(5, UUID.class), rs.getString(6), rs.getString(7), List.of(),
				rs.getTimestamp(8).toInstant());
	}

	/** The audit event of a delta, without property values (design D8). */
	private static AuditEvent event(String type, OeaPrincipal principal, UUID solutionId, Delta delta) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("solutionId", solutionId.toString());
		details.put("deltaId", delta.id().toString());
		details.put("deltaType", delta.type().value());
		if (delta.type() == Type.RETIRING) {
			details.put("entityId", delta.entityId());
		}
		else {
			details.put("metatypeId", delta.metatypeId().toString());
			details.put("name", delta.name());
		}
		return new AuditEvent(type, AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS,
				AuditEvent.Source.API, Sessions.actor(principal), "solution", solutionId.toString(), details);
	}

}
