package io.oea.app.diagramview;

import io.oea.app.diagram.Diagrams;
import io.oea.app.entity.AtomicBatchFailedException;
import io.oea.app.entity.EntityBatch;
import io.oea.app.entity.EntityInput;
import io.oea.app.entity.Issue;
import io.oea.app.entity.MetatypeRules;
import io.oea.app.metamodel.Viewpoints;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.diagram.DiagramShapeStore;
import io.oea.core.diagram.DiagramShapeStore.EntityRef;
import io.oea.core.diagram.DiagramShapeStore.ShapeWrite;
import io.oea.core.diagram.DiagramShapeStore.ShownShape;
import io.oea.core.diagram.DiagramShapeStore.StoredShape;
import io.oea.core.entity.EntitySearchStore;
import io.oea.core.entity.MetatypeRuleStore;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The canvas of a diagram (US-074, UC-10; design D2 to D4 of us-074-diagram-canvas): the view with
 * its shapes in drawing order, the search of the entities the viewpoint allows, and the atomic save
 * of the layout with the entities created on the canvas. The permissions are checked from the
 * contract. A save locks the diagram, validates everything, creates the new entities through the
 * entity write of {@code app.entity} in the same transaction, replaces the shapes, raises the
 * version and audits {@code diagram.layout-saved}; a failure changes nothing.
 */
@Service
public class DiagramViews {

	/** The most decimals of a position or size, in pt. */
	static final int MAX_DECIMALS = 2;

	private static final Pattern INDEXED = Pattern.compile("^entities\\[(\\d+)]");

	private final Diagrams diagrams;

	private final Viewpoints viewpoints;

	private final EntityBatch entities;

	private final DiagramShapeStore shapes;

	private final EntitySearchStore search;

	private final MetatypeRuleStore ruleStore;

	private final AuditWriter audit;

	private final Clock clock;

	DiagramViews(Diagrams diagrams, Viewpoints viewpoints, EntityBatch entities, DiagramShapeStore shapes,
			EntitySearchStore search, MetatypeRuleStore ruleStore, AuditWriter audit, Clock clock) {
		this.diagrams = diagrams;
		this.viewpoints = viewpoints;
		this.entities = entities;
		this.shapes = shapes;
		this.search = search;
		this.ruleStore = ruleStore;
		this.audit = audit;
		this.clock = clock;
	}

	/** A shape in the view; {@code missingMandatory} names the mandatory properties without a value. */
	public record Shape(UUID id, int entityId, String entityName, UUID metatypeId, BigDecimal x, BigDecimal y,
			BigDecimal width, BigDecimal height, List<String> missingMandatory) {
	}

	/** The diagram with its viewpoint and its shapes in drawing order. */
	public record View(Diagrams.Diagram diagram, Viewpoints.Viewpoint viewpoint, List<Shape> shapes) {
	}

	/** An entity the viewpoint allows. */
	public record Found(int id, String name, Viewpoints.Type metatype) {
	}

	/** One page of {@link Found}. */
	public record EntityPage(List<Found> items, int page, int pageSize, long totalElements, int totalPages) {
	}

	/** A shape to save: exactly one of {@code entityId} and {@code newEntityKey} is set (checked). */
	public record ShapeInput(UUID id, Integer entityId, UUID newEntityKey, BigDecimal x, BigDecimal y,
			BigDecimal width, BigDecimal height) {
	}

	/** A new entity to create with the save. */
	public record NewEntityInput(UUID key, UUID metatypeId, String name) {
	}

	/**
	 * The view of a diagram.
	 *
	 * @throws NoSuchElementException for an unknown or deleted diagram (404)
	 */
	@Transactional(readOnly = true)
	public View view(UUID id) {
		return buildView(this.diagrams.get(id));
	}

	/**
	 * One page of the active element entities whose metatype the viewpoint of the diagram allows,
	 * by name ignoring case, then ID; {@code q} filters by a name part (wildcards literal) and
	 * {@code metatypeId} narrows to one allowed metatype.
	 *
	 * @throws NoSuchElementException for an unknown or deleted diagram (404)
	 * @throws DiagramViewException naming {@code metatypeId} for a metatype the viewpoint does not allow (422)
	 */
	@Transactional(readOnly = true)
	public EntityPage search(UUID id, String q, UUID metatypeId, int page, int pageSize) {
		Diagrams.Diagram diagram = this.diagrams.get(id);
		Viewpoints.Viewpoint viewpoint = this.viewpoints.get(diagram.viewpoint().id());
		Map<UUID, Viewpoints.Type> allowed = new LinkedHashMap<>();
		viewpoint.metatypes().forEach((type) -> allowed.put(type.id(), type));
		List<UUID> types = (metatypeId == null) ? List.copyOf(allowed.keySet()) : List.of(metatypeId);
		if (metatypeId != null && !allowed.containsKey(metatypeId)) {
			throw new DiagramViewException(DiagramViewException.Reason.INVALID,
					"The viewpoint of the diagram does not allow this metatype.",
					List.of(new Issue("metatypeId", "METATYPE_NOT_ALLOWED",
							"The viewpoint of the diagram does not allow this metatype.")));
		}
		String like = (q != null && !q.isBlank()) ? "%" + escape(q) + "%" : null;
		long total = this.search.count(types, like);
		List<Found> items = this.search.page(types, like, pageSize, (long) page * pageSize)
			.stream()
			.map((found) -> new Found(found.id(), found.name(), allowed.get(found.metatypeId())))
			.toList();
		int totalPages = Math.toIntExact((total + pageSize - 1) / pageSize);
		return new EntityPage(items, page, pageSize, total, totalPages);
	}

	/**
	 * Saves the layout of the diagram with the expected version and returns the new view.
	 *
	 * @throws PermissionDeniedException for a principal without a person (403)
	 * @throws NoSuchElementException for an unknown or deleted diagram (404)
	 * @throws io.oea.app.diagram.DiagramChangeException for a missing (428) or stale (412) version
	 * @throws DiagramViewException for a value with more than two decimals (400) or shapes and new
	 *         entities that violate a rule, the faulty entity writes included (422)
	 */
	@Transactional
	public View save(OeaPrincipal principal, UUID id, Long expectedVersion, List<ShapeInput> shapeInputs,
			List<NewEntityInput> newEntities, AuditEvent.Agent agent) {
		if (principal.personId() == null) {
			throw new PermissionDeniedException();
		}
		checkDecimals(shapeInputs);
		Diagrams.Diagram diagram = this.diagrams.lockForLayout(id, expectedVersion);
		Viewpoints.Viewpoint viewpoint = this.viewpoints.get(diagram.viewpoint().id());
		Set<UUID> allowed = new HashSet<>();
		viewpoint.metatypes().forEach((type) -> allowed.add(type.id()));
		List<StoredShape> stored = this.shapes.stored(id);
		List<Issue> errors = validate(id, shapeInputs, newEntities, allowed, stored);
		if (!errors.isEmpty()) {
			throw invalid(errors);
		}
		Map<UUID, Integer> created = create(principal, newEntities, agent);
		List<ShapeWrite> writes = new ArrayList<>();
		for (ShapeInput shape : shapeInputs) {
			int entityId = (shape.entityId() != null) ? shape.entityId() : created.get(shape.newEntityKey());
			writes.add(new ShapeWrite(shape.id(), entityId, shape.x(), shape.y(), shape.width(), shape.height()));
		}
		this.shapes.replace(id, writes);
		int version = this.diagrams.layoutSaved(principal, id);
		audit(principal, id, diagram.name(), version, count(stored, writes), created.size());
		return buildView(this.diagrams.get(id));
	}

	private static void checkDecimals(List<ShapeInput> shapes) {
		List<Issue> errors = new ArrayList<>();
		for (int i = 0; i < shapes.size(); i++) {
			ShapeInput shape = shapes.get(i);
			decimals(errors, i, "x", shape.x());
			decimals(errors, i, "y", shape.y());
			decimals(errors, i, "width", shape.width());
			decimals(errors, i, "height", shape.height());
		}
		if (!errors.isEmpty()) {
			throw new DiagramViewException(DiagramViewException.Reason.INVALID_REQUEST,
					"A position or size has more than " + MAX_DECIMALS + " decimals.", errors);
		}
	}

	private static void decimals(List<Issue> errors, int index, String field, BigDecimal value) {
		if (value.stripTrailingZeros().scale() > MAX_DECIMALS) {
			errors.add(new Issue("shapes[" + index + "]." + field, "TOO_MANY_DECIMALS",
					"The value has at most " + MAX_DECIMALS + " decimals."));
		}
	}

	private List<Issue> validate(UUID diagramId, List<ShapeInput> shapeInputs, List<NewEntityInput> newEntities,
			Set<UUID> allowed, List<StoredShape> stored) {
		List<Issue> errors = new ArrayList<>();
		Map<UUID, Integer> keys = new HashMap<>();
		for (int i = 0; i < newEntities.size(); i++) {
			NewEntityInput entity = newEntities.get(i);
			if (keys.putIfAbsent(entity.key(), i) != null) {
				errors.add(new Issue("newEntities[" + i + "].key", "DUPLICATE_KEY",
						"The key occurs earlier in the new entities."));
			}
			if (!allowed.contains(entity.metatypeId())) {
				errors.add(new Issue("newEntities[" + i + "].metatypeId", "METATYPE_NOT_ALLOWED",
						"The viewpoint of the diagram does not allow this metatype."));
			}
		}
		Map<UUID, StoredShape> storedById = new HashMap<>();
		stored.forEach((shape) -> storedById.put(shape.id(), shape));
		Set<UUID> ids = new HashSet<>();
		List<UUID> shapeIds = shapeInputs.stream().map(ShapeInput::id).toList();
		Set<UUID> foreign = this.shapes.idsOfOtherDiagrams(diagramId, shapeIds);
		Map<Integer, EntityRef> entities = this.shapes
			.entities(shapeInputs.stream().map(ShapeInput::entityId).filter((e) -> e != null).distinct().toList());
		Set<UUID> usedKeys = new HashSet<>();
		for (int i = 0; i < shapeInputs.size(); i++) {
			ShapeInput shape = shapeInputs.get(i);
			String path = "shapes[" + i + "]";
			if (!ids.add(shape.id())) {
				errors.add(new Issue(path + ".id", "DUPLICATE_SHAPE_ID", "The shape ID occurs earlier in the request."));
			}
			if (foreign.contains(shape.id())) {
				errors.add(new Issue(path + ".id", "SHAPE_ID_IN_USE", "The shape ID belongs to another diagram."));
			}
			if ((shape.entityId() == null) == (shape.newEntityKey() == null)) {
				errors.add(new Issue(path, "ENTITY_REFERENCE_INVALID",
						"A shape names exactly one of entityId and newEntityKey."));
			}
			else if (shape.newEntityKey() != null) {
				if (keys.containsKey(shape.newEntityKey())) {
					usedKeys.add(shape.newEntityKey());
				}
				else {
					errors.add(new Issue(path + ".newEntityKey", "NEW_ENTITY_UNKNOWN",
							"The key names no entity of newEntities."));
				}
			}
			else {
				EntityRef entity = entities.get(shape.entityId());
				StoredShape kept = storedById.get(shape.id());
				if (entity == null || entity.deleted()) {
					errors.add(new Issue(path + ".entityId", "ENTITY_NOT_FOUND",
							"The entity does not exist or is deleted."));
				}
				else if (entity.connection()) {
					errors.add(new Issue(path + ".entityId", "ENTITY_IS_CONNECTION",
							"A shape shows an element entity, not a connection."));
				}
				else if (!allowed.contains(entity.metatypeId())
						&& !(kept != null && kept.entityId() == shape.entityId())) {
					// a stored shape that keeps its entity is accepted when the viewpoint changed (design D4)
					errors.add(new Issue(path + ".entityId", "METATYPE_NOT_ALLOWED",
							"The viewpoint of the diagram does not allow the metatype of the entity."));
				}
			}
		}
		for (int i = 0; i < newEntities.size(); i++) {
			if (!usedKeys.contains(newEntities.get(i).key())) {
				errors.add(new Issue("newEntities[" + i + "]", "NEW_ENTITY_UNUSED",
						"No shape shows the new entity."));
			}
		}
		return errors;
	}

	private Map<UUID, Integer> create(OeaPrincipal principal, List<NewEntityInput> newEntities,
			AuditEvent.Agent agent) {
		Map<UUID, Integer> created = new HashMap<>();
		if (newEntities.isEmpty()) {
			return created;
		}
		List<EntityInput> inputs = newEntities.stream()
			.map((entity) -> new EntityInput(entity.name(), entity.metatypeId(), null, null, null, null, null,
					List.of()))
			.toList();
		try {
			EntityBatch.Result result = this.entities.createInTransaction(principal, inputs, agent);
			for (int i = 0; i < newEntities.size(); i++) {
				created.put(newEntities.get(i).key(), result.items().get(i).entityId());
			}
		}
		catch (AtomicBatchFailedException ex) {
			// "entities[i].name" of the entity write is "newEntities[i].name" of this request
			throw invalid(ex.getErrors()
				.stream()
				.map((issue) -> new Issue(INDEXED.matcher(issue.field()).replaceFirst("newEntities[$1]"),
						issue.code(), issue.message()))
				.toList());
		}
		return created;
	}

	private static DiagramViewException invalid(List<Issue> errors) {
		return new DiagramViewException(DiagramViewException.Reason.INVALID,
				errors.size() + " shape(s) or new entity(ies) are invalid; nothing was saved.", errors);
	}

	private record Counts(int added, int changed, int removed) {
	}

	/** The added, changed and removed shapes; the hidden stored shapes are not part of the view. */
	private static Counts count(List<StoredShape> stored, List<ShapeWrite> writes) {
		Map<UUID, StoredShape> before = new LinkedHashMap<>();
		stored.stream().filter((shape) -> !shape.hidden()).forEach((shape) -> before.put(shape.id(), shape));
		Set<UUID> after = new HashSet<>();
		writes.forEach((write) -> after.add(write.id()));
		// the relative drawing order of the shapes that stay
		List<UUID> orderBefore = before.keySet().stream().filter(after::contains).toList();
		List<UUID> orderAfter = writes.stream().map(ShapeWrite::id).filter(before::containsKey).toList();
		int added = 0;
		int changed = 0;
		for (ShapeWrite write : writes) {
			StoredShape old = before.get(write.id());
			if (old == null) {
				added++;
			}
			else if (old.entityId() != write.entityId() || old.x().compareTo(write.x()) != 0
					|| old.y().compareTo(write.y()) != 0 || old.width().compareTo(write.width()) != 0
					|| old.height().compareTo(write.height()) != 0
					|| orderBefore.indexOf(write.id()) != orderAfter.indexOf(write.id())) {
				changed++;
			}
		}
		int removed = (int) before.keySet().stream().filter((id) -> !after.contains(id)).count();
		return new Counts(added, changed, removed);
	}

	private View buildView(Diagrams.Diagram diagram) {
		Viewpoints.Viewpoint viewpoint = this.viewpoints.get(diagram.viewpoint().id());
		List<ShownShape> shown = this.shapes.shown(diagram.id());
		Map<Integer, List<String>> missing = missingMandatory(shown);
		List<Shape> view = shown.stream()
			.map((shape) -> new Shape(shape.id(), shape.entityId(), shape.entityName(), shape.metatypeId(), shape.x(),
					shape.y(), shape.width(), shape.height(), missing.getOrDefault(shape.entityId(), List.of())))
			.toList();
		return new View(diagram, viewpoint, view);
	}

	/**
	 * The names of the mandatory properties of the current metatype of each entity that have no
	 * value, ordered by name ignoring case. Incompleteness is computed, not stored (design D3).
	 */
	private Map<Integer, List<String>> missingMandatory(List<ShownShape> shown) {
		Map<Integer, List<String>> missing = new HashMap<>();
		if (shown.isEmpty()) {
			return missing;
		}
		MetatypeRules rules = MetatypeRules.load(this.ruleStore, LocalDate.now(this.clock));
		Map<Integer, UUID> metatypes = new HashMap<>();
		shown.forEach((shape) -> metatypes.put(shape.entityId(), shape.metatypeId()));
		Map<Integer, Set<UUID>> values = this.shapes.valueProperties(metatypes.keySet());
		metatypes.forEach((entityId, metatypeId) -> {
			MetatypeRules.Metatype metatype = rules.metatype(metatypeId);
			if (metatype == null) {
				return;
			}
			Set<UUID> have = values.getOrDefault(entityId, Set.of());
			List<String> names = metatype.mappings()
				.values()
				.stream()
				.filter((mapping) -> "mandatory".equals(mapping.necessity()) && !have.contains(mapping.definition().id()))
				.map((mapping) -> mapping.definition().name())
				.sorted(Comparator.comparing((String name) -> name.toLowerCase(java.util.Locale.ROOT))
					.thenComparing(Comparator.naturalOrder()))
				.toList();
			if (!names.isEmpty()) {
				missing.put(entityId, names);
			}
		});
		return missing;
	}

	/** The pattern with {@code \}, {@code %} and {@code _} escaped, so that they match literally. */
	static String escape(String search) {
		return search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
	}

	private void audit(OeaPrincipal principal, UUID id, String name, int version, Counts counts, int entitiesCreated) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", name);
		details.put("version", version);
		details.put("added", counts.added());
		details.put("changed", counts.changed());
		details.put("removed", counts.removed());
		details.put("entitiesCreated", entitiesCreated);
		this.audit.append(new AuditEvent("diagram.layout-saved", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "diagram", id.toString(),
				details));
	}

}
