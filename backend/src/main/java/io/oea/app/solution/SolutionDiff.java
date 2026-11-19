package io.oea.app.solution;

import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.solution.SolutionDeltas.Change;
import io.oea.app.solution.SolutionDeltas.Delta;
import io.oea.app.solution.SolutionDeltas.Type;
import io.oea.app.solution.SolutionDeltas.Typed;
import io.oea.app.solution.SolutionDeltas.Value;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.PropertyValueStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The diff of a solution (US-043, REQ-041; design D2 of us-043-diff-view): per delta, the current
 * state of its entity in the catalog next to the target state after the solution, computed on each
 * read from one page of the delta list and not stored. For a solution of a plateau with the basis
 * {@code predecessor}, the current state is the entity in that basis (design D5 of
 * us-086-plateau-go-live).
 */
@Service
public class SolutionDiff {

	private static final String NAME = "name";

	private static final String DESCRIPTION = "description";

	private static final String PROPERTY = "property";

	private final EntityStore entities;

	private final PropertyValueStore valueStore;

	private final SolutionDeltas deltas;

	private final PropertyAccess access;

	private final SolutionLandscape landscape;

	SolutionDiff(EntityStore entities, PropertyValueStore valueStore, SolutionDeltas deltas, PropertyAccess access, SolutionLandscape landscape) {
		this.entities = entities;
		this.valueStore = valueStore;
		this.deltas = deltas;
		this.access = access;
		this.landscape = landscape;
	}

	/**
	 * One field of an entry; {@code current} and {@code target} are {@code null} for no value or no
	 * state on that side, and both are {@code null} when {@code redacted}.
	 */
	public record Field(String field, UUID propertyDefId, String propertyName, String dataType, boolean redacted,
			Typed current, Typed target, boolean changed, boolean catalogChanged) {
	}

	/** One delta with the current and target state of its entity, and its conflicts (US-044). */
	public record Entry(UUID deltaId, Type type, Integer entityId, UUID metatypeId, String metatypeName, String name,
			boolean inCatalog, List<Field> fields, List<SolutionConflicts.Conflict> conflicts) {
	}

	/** One page of the diff, with the paging and counts of the delta list. */
	public record Page(List<Entry> entries, SolutionDeltas.Page deltas) {
	}

	/** An entity of the baseline with its values, keyed by property. */
	private record Catalog(String name, String description, Map<UUID, Field> values) {
	}

	/**
	 * One page of the diff of the solution, in the order and with the paging of the delta list.
	 *
	 * @throws NoSuchElementException for an unknown solution (404)
	 * @throws InvalidSolutionQueryException naming {@code cursor} for a cursor of another query
	 */
	@Transactional(readOnly = true)
	public Page read(OeaPrincipal principal, UUID solutionId, SolutionDeltas.PageQuery query) {
		PropertyAccess.Snapshot snapshot = this.access.of(principal);
		SolutionDeltas.Page page = this.deltas.list(snapshot, solutionId, query);
		Integer[] ids = page.items()
			.stream()
			.map(Delta::entityId)
			.filter(Objects::nonNull)
			.distinct()
			.toArray(Integer[]::new);
		Map<Integer, Catalog> catalog = this.landscape.onBasis(solutionId) ? basis(principal, solutionId, ids)
				: catalog(ids, snapshot);
		List<Entry> entries = page.items().stream().map((delta) -> entry(delta, catalog.get(delta.entityId()))).toList();
		return new Page(entries, page);
	}

	private static Entry entry(Delta delta, Catalog current) {
		List<Field> fields = switch (delta.type()) {
			case NEW -> created(delta);
			case RETIRING -> retiring(current);
			case MODIFIED -> modified(delta, current);
		};
		return new Entry(delta.id(), delta.type(), delta.entityId(), delta.metatypeId(), delta.metatypeName(),
				delta.name(), current != null, fields, delta.conflicts());
	}

	/** A delta {@code new}: no current state; every field changes. */
	private static List<Field> created(Delta delta) {
		List<Field> fields = new ArrayList<>();
		fields.add(system(NAME, null, text(delta.name()), true, false));
		Typed description = text(delta.description());
		if (description != null) {
			fields.add(system(DESCRIPTION, null, description, true, false));
		}
		for (Value value : delta.properties()) {
			fields.add(new Field(PROPERTY, value.propertyDefId(), value.propertyName(), value.dataType(), value.redacted(),
					null, value.typed(), true, false));
		}
		return fields;
	}

	/** A delta {@code retiring}: the current state, no target state. */
	private static List<Field> retiring(Catalog current) {
		if (current == null) {
			return List.of();
		}
		List<Field> fields = new ArrayList<>();
		fields.add(system(NAME, text(current.name()), null, true, false));
		Typed description = text(current.description());
		if (description != null) {
			fields.add(system(DESCRIPTION, description, null, true, false));
		}
		for (Field value : sorted(current.values().values())) {
			fields.add(new Field(PROPERTY, value.propertyDefId(), value.propertyName(), value.dataType(), value.redacted(),
					value.current(), null, true, false));
		}
		return fields;
	}

	/**
	 * A delta {@code modified}: the catalog with the after values applied; a recorded before value
	 * that differs from the catalog marks drift. Without a current state, only the changes remain.
	 */
	private static List<Field> modified(Delta delta, Catalog current) {
		Map<String, Change> changes = new HashMap<>();
		for (Change change : delta.changes()) {
			changes.put(PROPERTY.equals(change.field()) ? change.propertyDefId().toString() : change.field(), change);
		}
		List<Field> fields = new ArrayList<>();
		boolean inCatalog = current != null;
		fields.add(system(changes.get(NAME), inCatalog ? text(current.name()) : null, NAME, inCatalog));
		Field description = system(changes.get(DESCRIPTION), inCatalog ? text(current.description()) : null,
				DESCRIPTION, inCatalog);
		if (description.current() != null || description.target() != null) {
			fields.add(description);
		}
		Map<UUID, Field> properties = new LinkedHashMap<>();
		if (inCatalog) {
			for (Field value : current.values().values()) {
				properties.put(value.propertyDefId(), new Field(PROPERTY, value.propertyDefId(), value.propertyName(),
						value.dataType(), value.redacted(), value.current(), value.current(), false, false));
			}
		}
		for (Change change : delta.changes()) {
			if (!PROPERTY.equals(change.field())) {
				continue;
			}
			Field known = properties.get(change.propertyDefId());
			Typed now = (known != null) ? known.current() : null;
			boolean redacted = change.redacted() || (known != null && known.redacted());
			properties.put(change.propertyDefId(),
					new Field(PROPERTY, change.propertyDefId(), change.propertyName(), change.dataType(), redacted,
							redacted ? null : now, redacted ? null : change.after(), true,
							!redacted && inCatalog && !same(change.before(), now)));
		}
		fields.addAll(sorted(properties.values()));
		return fields;
	}

	/** A system field of a delta {@code modified}, changed by {@code change} if it is not {@code null}. */
	private static Field system(Change change, Typed now, String field, boolean inCatalog) {
		if (change == null) {
			return system(field, now, now, false, false);
		}
		return system(field, now, text(change.after()), true, inCatalog && !same(text(change.before()), now));
	}

	private static Field system(String field, Typed current, Typed target, boolean changed, boolean drift) {
		return new Field(field, null, null, null, false, current, target, changed, drift);
	}

	private static List<Field> sorted(java.util.Collection<Field> fields) {
		return fields.stream()
			.sorted(Comparator.comparing((Field field) -> field.propertyName().toLowerCase(Locale.ROOT))
				.thenComparing((field) -> field.propertyDefId().toString()))
			.toList();
	}

	/** Two values are the same when their typed components are; a reference compares by its ID. */
	private static boolean same(Typed a, Typed b) {
		if (a == null || b == null) {
			return a == b;
		}
		return Objects.equals(a.text(), b.text()) && Objects.equals(a.integer(), b.integer())
				&& Objects.equals(a.bool(), b.bool()) && Objects.equals(a.date(), b.date())
				&& Objects.equals(a.enumValue(), b.enumValue()) && Objects.equals(referenceId(a), referenceId(b));
	}

	private static Integer referenceId(Typed value) {
		return (value.reference() != null) ? value.reference().id() : null;
	}

	/** A text value, or {@code null} for no text; an empty text is no value. */
	private static Typed text(String text) {
		return (text == null || text.isEmpty()) ? null : new Typed(text, null, null, null, null, null);
	}

	private static Typed text(Typed value) {
		return (value != null) ? text(value.text()) : null;
	}

	/**
	 * The entities of the baseline among {@code ids} with their values; a value the reader may not
	 * read for the entity is redacted.
	 */
	/** The entities in the baseline of the solution, the basis of its plateau, redacted for the reader. */
	private Map<Integer, Catalog> basis(OeaPrincipal principal, UUID solutionId, Integer[] ids) {
		Map<Integer, Catalog> basis = new HashMap<>();
		for (Integer id : ids) {
			SolutionLandscape.EntityEntry entry;
			try {
				entry = this.landscape.entry(principal, solutionId, id);
			}
			catch (NoSuchElementException ex) {
				// deleted meanwhile: no current state, as for the catalog
				continue;
			}
			Map<UUID, Field> values = new LinkedHashMap<>();
			for (Value value : entry.properties()) {
				values.put(value.propertyDefId(), new Field(PROPERTY, value.propertyDefId(), value.propertyName(),
						value.dataType(), value.redacted(), value.typed(), null, false, false));
			}
			basis.put(id, new Catalog(entry.entry().name(), entry.description(), values));
		}
		return basis;
	}

	private Map<Integer, Catalog> catalog(Integer[] ids, PropertyAccess.Snapshot snapshot) {
		Map<Integer, Catalog> catalog = new HashMap<>();
		if (ids.length == 0) {
			return catalog;
		}
		this.entities.activeElementNames(Arrays.asList(ids))
			.forEach((id, row) -> catalog.put(id, new Catalog(row.name(), row.description(), new LinkedHashMap<>())));
		this.valueStore.labeledValues(catalog.keySet()).forEach((entityId, values) -> {
			Catalog entity = catalog.get(entityId);
			for (PropertyValueStore.NamedValue named : values) {
				Value value = Value.of(named);
				boolean redacted = !snapshot.readableFor(named.propertyDefId(), entityId);
				Typed typed = redacted ? null : value.typed();
				entity.values()
					.put(named.propertyDefId(), new Field(PROPERTY, named.propertyDefId(), named.propertyName(),
							named.dataType(), redacted, typed, null, false, false));
			}
		});
		return catalog;
	}

}
