package io.oea.app.diagramview;

import io.oea.app.deletion.EntityDeletions;
import io.oea.app.diagram.Diagrams;
import io.oea.app.entity.AtomicBatchFailedException;
import io.oea.app.entity.EntityBatch;
import io.oea.app.entity.ConnectionInput;
import io.oea.app.entity.EntityInput;
import io.oea.app.entity.Issue;
import io.oea.app.hierarchy.EntityHierarchy;
import io.oea.app.hierarchy.InvalidHierarchyException;
import io.oea.app.entity.MetatypeRules;
import io.oea.app.entity.PropertyWriteAccess;
import io.oea.app.metamodel.DiagramSettings;
import io.oea.app.metamodel.OrganizationModels;
import io.oea.app.metamodel.OverrideAccessAudit;
import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.metamodel.Viewpoints;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.diagram.DiagramAssignmentStore;
import io.oea.core.diagram.DiagramEdgeStore;
import io.oea.core.diagram.DiagramEdgeStore.EdgeWrite;
import io.oea.core.diagram.DiagramEdgeStore.ShownEdge;
import io.oea.core.diagram.DiagramEdgeStore.StoredEdge;
import io.oea.core.diagram.DiagramHierarchyLineStore;
import io.oea.core.diagram.DiagramShapeStore;
import io.oea.core.diagram.DiagramShapeStore.EntityRef;
import io.oea.core.diagram.DiagramShapeStore.ShapeWrite;
import io.oea.core.diagram.DiagramShapeStore.ShownShape;
import io.oea.core.diagram.DiagramShapeStore.StoredShape;
import io.oea.core.entity.EntityHierarchyStore;
import io.oea.core.entity.EntitySearchStore;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.LabeledValue;
import io.oea.core.entity.MetatypeRuleStore;
import io.oea.core.entity.PropertyValueStore;
import io.oea.core.entity.TypedValue;
import io.oea.core.metamodel.MetatypeStore;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The canvas of a diagram (US-074, UC-10; design D2 to D4 of us-074-diagram-canvas and D2 to D4 of
 * us-074-diagram-connections): the view with its shapes in drawing order and its edges, the search
 * of the entities the viewpoint allows, the connections to offer, and the atomic save of the
 * layout with the entities and connections created on the canvas. The permissions are checked from the
 * contract. A save locks the diagram, validates everything, creates the new entities through the
 * entity write of {@code app.entity} in the same transaction, replaces the shapes, raises the
 * version and audits {@code diagram.layout-saved}; a failure changes nothing.
 */
@Service
public class DiagramViews {

	/** The most decimals of a position or size, in pt. */
	static final int MAX_DECIMALS = 2;

	/** The most direct children that {@link #children} returns. */
	public static final int MAX_CHILDREN = 500;

	/** The deepest nesting of shapes; a shape on the canvas is on level 1. */
	public static final int MAX_NESTING = 10;

	/** The most connections that {@link #connections} returns. */
	public static final int MAX_OFFERED_CONNECTIONS = 5000;

	private static final String SUB_PROCESS = "bpmn:SubProcess";

	private static final String POOL = "bpmn:Pool";

	private static final String LANE = "bpmn:Lane";

	/** The notation keys of the shapes that carry assignments besides lanes and pools (design D3). */
	private static final Set<String> TASKS = Set.of("bpmn:Task", "bpmn:UserTask", "bpmn:ServiceTask",
			"bpmn:SendTask", "bpmn:ReceiveTask", "bpmn:BusinessRuleTask", "bpmn:ScriptTask");

	/** The most connections of a save that remove assignments. */
	public static final int MAX_REMOVED_CONNECTIONS = 500;

	private static final String HORIZONTAL = "horizontal";

	private static final String VERTICAL = "vertical";

	private static final Pattern INDEXED = Pattern.compile("^entities\\[(\\d+)]");

	private static final Pattern INDEXED_CONNECTION = Pattern.compile("^connections\\[(\\d+)](?:\\.(\\w+))?");

	private final Diagrams diagrams;

	private final Viewpoints viewpoints;

	private final EntityBatch entities;

	private final DiagramShapeStore shapes;

	private final DiagramEdgeStore edges;

	private final EntitySearchStore search;

	private final MetatypeRuleStore ruleStore;

	private final AuditWriter audit;

	private final Clock clock;

	private final EntityStore entityStore;

	private final PropertyValueStore valueStore;

	private final MetatypeStore metatypes;

	private final PropertyAccess access;

	private final PermissionResolver permissions;

	private final OverrideAccessAudit overrideAudit;

	private final DiagramHierarchyLineStore lines;

	private final EntityHierarchy hierarchy;

	private final EntityHierarchyStore hierarchyStore;

	private final DiagramSettings settings;

	private final OrganizationModels organizationModels;

	private final DiagramAssignmentStore assignmentStore;

	private final EntityDeletions deletions;

	DiagramViews(Diagrams diagrams, Viewpoints viewpoints, EntityBatch entities, DiagramShapeStore shapes,
			DiagramEdgeStore edges, EntitySearchStore search, MetatypeRuleStore ruleStore, AuditWriter audit, Clock clock,
			EntityStore entityStore, PropertyValueStore valueStore, MetatypeStore metatypes, PropertyAccess access,
			PermissionResolver permissions, OverrideAccessAudit overrideAudit, DiagramHierarchyLineStore lines,
			EntityHierarchy hierarchy, EntityHierarchyStore hierarchyStore, DiagramSettings settings,
			OrganizationModels organizationModels, DiagramAssignmentStore assignmentStore,
			EntityDeletions deletions) {
		this.organizationModels = organizationModels;
		this.assignmentStore = assignmentStore;
		this.deletions = deletions;
		this.lines = lines;
		this.hierarchy = hierarchy;
		this.hierarchyStore = hierarchyStore;
		this.settings = settings;
		this.entityStore = entityStore;
		this.valueStore = valueStore;
		this.metatypes = metatypes;
		this.access = access;
		this.permissions = permissions;
		this.overrideAudit = overrideAudit;
		this.diagrams = diagrams;
		this.viewpoints = viewpoints;
		this.entities = entities;
		this.shapes = shapes;
		this.edges = edges;
		this.search = search;
		this.ruleStore = ruleStore;
		this.audit = audit;
		this.clock = clock;
	}

	/**
	 * A shape in the view; {@code missingMandatory} names the mandatory properties without a value.
	 * {@code parentShapeId} is the container, only when it is shown, earlier in the drawing order and
	 * contains the shape completely; {@code childCount} and {@code parentEntityId} are those of the entity.
	 */
	public record Shape(UUID id, int entityId, String entityName, UUID metatypeId, BigDecimal x, BigDecimal y,
			BigDecimal width, BigDecimal height, UUID parentShapeId, boolean collapsed, String orientation,
			int childCount, Integer parentEntityId, List<String> missingMandatory, boolean conflict,
			Assignments assignments) {
	}

	/** An assignment: the connection with its version and the organizational unit, role or person. */
	public record Assignment(int connectionId, int connectionVersion, int entityId, String name) {
	}

	/**
	 * The assignments of a shape (design D3 of us-075-lane-assignments): organizational units by
	 * belongs-to, roles by performs-role and persons by responsible.
	 */
	public record Assignments(List<Assignment> orgUnits, List<Assignment> roles, List<Assignment> persons) {
	}

	/** A hierarchy line from a container shape to the shape of a child placed elsewhere. */
	public record HierarchyLine(UUID id, UUID parentShapeId, UUID childShapeId) {
	}

	/** A bend point of an edge in pt. */
	public record Point(BigDecimal x, BigDecimal y) {
	}

	/**
	 * An edge in the view: a connection from a shape of its source to a shape of its target entity;
	 * {@code missingMandatory} names the mandatory properties of the connection without a value.
	 */
	public record Edge(UUID id, int connectionId, String connectionName, UUID metatypeId, UUID sourceShapeId,
			UUID targetShapeId, List<Point> points, List<String> missingMandatory, boolean conflict) {
	}

	/**
	 * The diagram with its viewpoint, its shapes in drawing order, its edges, its hierarchy lines and
	 * the effective setting "Ask for a new parent"; {@code conflictCount} is the number of shapes and
	 * edges that are conflicts (their type is not allowed by the viewpoint).
	 */
	public record View(Diagrams.Diagram diagram, Viewpoints.Viewpoint viewpoint, List<Shape> shapes,
			List<Edge> edges, List<HierarchyLine> hierarchyLines, boolean askNewParent, int conflictCount) {
	}

	/** A connection to offer when an entity is placed. */
	public record Offer(int connectionId, String name, UUID metatypeId, int sourceEntityId, int targetEntityId) {
	}

	/** The connections to offer; {@code truncated} when more than {@link #MAX_OFFERED_CONNECTIONS} matched. */
	public record Offers(List<Offer> items, boolean truncated) {
	}

	/**
	 * An edge to save: exactly one of {@code connectionId} and {@code newConnectionKey} is set
	 * (checked).
	 */
	public record EdgeInput(UUID id, Integer connectionId, UUID newConnectionKey, UUID sourceShapeId,
			UUID targetShapeId, List<Point> points) {

		public EdgeInput {
			points = (points != null) ? List.copyOf(points) : List.of();
		}

	}

	/** A new connection to create with the save; its endpoints are those of the shapes of its first edge. */
	public record NewConnectionInput(UUID key, UUID metatypeId, String name, String description,
			List<EntityInput.PropertyInput> properties, UUID sourceShapeId, Integer targetEntityId,
			UUID targetNewEntityKey) {

		public NewConnectionInput {
			properties = (properties != null) ? List.copyOf(properties) : List.of();
		}

		/** Whether the request names its own endpoints, which an assignment does and an edge's connection does not. */
		boolean hasEndpoints() {
			return this.sourceShapeId != null || this.targetEntityId != null || this.targetNewEntityKey != null;
		}

	}

	/** An assignment connection to delete with the save, with the connection version read. */
	public record RemovedConnectionInput(int connectionId, int expectedVersion) {
	}

	/** An entity the viewpoint allows, with its number of children and its parent. */
	public record Found(int id, String name, Viewpoints.Type metatype, int childCount, Integer parentEntityId) {
	}

	/** A direct child of an entity; {@code allowed} says whether the viewpoint allows its metatype. */
	public record Child(int id, String name, UUID metatypeId, int childCount, Integer parentEntityId, boolean allowed,
			ChildConnection compositionConnection) {
	}

	/** The composition connection from a parent to a child; {@code allowed} for the viewpoint. */
	public record ChildConnection(int id, String name, UUID metatypeId, boolean allowed) {
	}

	/** The direct children of an entity; {@code truncated} when more than {@link #MAX_CHILDREN} exist. */
	public record Children(List<Child> items, boolean truncated) {
	}

	/** A hierarchy line to save, between two shapes of the request. */
	public record LineInput(UUID id, UUID parentShapeId, UUID childShapeId) {
	}

	/**
	 * A change of the parent of a shown entity with the version read; at most one of
	 * {@code parentEntityId} and {@code parentNewEntityKey} is set (checked), none clears the parent.
	 */
	public record ParentChangeInput(int entityId, int expectedVersion, Integer parentEntityId,
			UUID parentNewEntityKey) {
	}

	/** One page of {@link Found}. */
	public record EntityPage(List<Found> items, int page, int pageSize, long totalElements, int totalPages) {
	}

	/** A shape to save: exactly one of {@code entityId} and {@code newEntityKey} is set (checked). */
	public record ShapeInput(UUID id, Integer entityId, UUID newEntityKey, BigDecimal x, BigDecimal y,
			BigDecimal width, BigDecimal height, UUID parentShapeId, boolean collapsed,
			String orientation) {
	}

	/**
	 * A new entity to create with the save, with an optional description and values, and optionally
	 * its parent: at most one of {@code parentEntityId} and {@code parentNewEntityKey} (checked).
	 */
	public record NewEntityInput(UUID key, UUID metatypeId, String name, String description,
			List<EntityInput.PropertyInput> properties, Integer parentEntityId, UUID parentNewEntityKey) {

		public NewEntityInput {
			properties = (properties != null) ? List.copyOf(properties) : List.of();
		}

	}

	/** A change of a shown entity with the entity version read; see {@link EntityBatch.EntityUpdate}. */
	public record EntityUpdateInput(int entityId, int expectedVersion, String name, String description,
			List<EntityInput.PropertyInput> properties) {

		public EntityUpdateInput {
			properties = (properties != null) ? List.copyOf(properties) : List.of();
		}

	}

	/** A reference to an entity in a value. */
	public record Reference(int id, String name, String metatypeAbbreviation) {
	}

	/** A typed value: at most one component is set. */
	public record Typed(String text, Long integer, Boolean bool, LocalDate date, String enumValue,
			Reference reference) {
	}

	/** A value of the entity; {@code typed} is {@code null} when {@code redacted}. */
	public record Value(UUID propertyDefId, String propertyName, String dataType, boolean redacted, Typed typed) {
	}

	/** Whether the caller may read and change the value of a mapped property of the entity. */
	public record Field(UUID propertyDefId, boolean readable, boolean writable) {
	}

	/** The details of an entity shown in the diagram. */
	public record Details(int id, String name, String description, UUID metatypeId, String metatypeName,
			String metatypeAbbreviation, int version, List<Value> properties, List<Field> fields,
			List<String> missingMandatory, Assignments assignments) {
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
			.map((found) -> new Found(found.id(), found.name(), allowed.get(found.metatypeId()), found.childCount(),
					found.parentEntityId()))
			.toList();
		int totalPages = Math.toIntExact((total + pageSize - 1) / pageSize);
		return new EntityPage(items, page, pageSize, total, totalPages);
	}

	/**
	 * The direct children of an element entity that are not deleted, by name ignoring case, then ID, at
	 * most {@link #MAX_CHILDREN} (design D2 of us-074-diagram-nesting), each with whether the viewpoint
	 * allows its metatype and the composition connection of the pair.
	 *
	 * @throws NoSuchElementException for an unknown or deleted diagram, and an unknown, deleted or
	 *         connection entity (404)
	 */
	@Transactional(readOnly = true)
	public Children children(UUID id, int entityId) {
		Diagrams.Diagram diagram = this.diagrams.get(id);
		if (!this.shapes.activeElement(entityId)) {
			throw new NoSuchElementException("entity " + entityId);
		}
		Viewpoints.Viewpoint viewpoint = this.viewpoints.get(diagram.viewpoint().id());
		Set<UUID> allowed = new HashSet<>();
		viewpoint.metatypes().forEach((type) -> allowed.add(type.id()));
		Set<UUID> allowedConnections = new HashSet<>();
		viewpoint.connectionTypes().forEach((type) -> allowedConnections.add(type.id()));
		List<DiagramShapeStore.Child> found = this.shapes.children(entityId, MAX_CHILDREN + 1);
		boolean truncated = found.size() > MAX_CHILDREN;
		List<DiagramShapeStore.Child> kept = truncated ? found.subList(0, MAX_CHILDREN) : found;
		Map<Integer, DiagramShapeStore.CompositionConnection> connections = this.shapes
			.compositionConnections(entityId, kept.stream().map(DiagramShapeStore.Child::id).toList());
		List<Child> items = kept.stream().map((child) -> {
			DiagramShapeStore.CompositionConnection connection = connections.get(child.id());
			return new Child(child.id(), child.name(), child.metatypeId(), child.childCount(), child.parentEntityId(),
					allowed.contains(child.metatypeId()),
					(connection != null) ? new ChildConnection(connection.connectionId(), connection.name(),
							connection.metatypeId(), allowedConnections.contains(connection.metatypeId())) : null);
		}).toList();
		return new Children(items, truncated);
	}

	/**
	 * The details of an entity that a shape of the diagram shows (design D1 of
	 * us-074-diagram-entity-details): system fields, the values with the redaction of the other
	 * reads, per mapped property whether the caller may read and change it, and the missing
	 * mandatory properties.
	 *
	 * @throws NoSuchElementException for an unknown or deleted diagram, an unknown or deleted entity,
	 *         and an entity without a shape and a connection without a shown edge in the diagram (404)
	 */
	@Transactional(readOnly = true)
	public Details details(OeaPrincipal principal, UUID id, int entityId) {
		Diagrams.Diagram diagram = this.diagrams.get(id);
		List<ShownShape> shownShapes = this.shapes.shown(id);
		boolean byShape = shownShapes.stream().anyMatch((shape) -> shape.entityId() == entityId);
		if (!byShape && !this.edges.shownConnections(id, List.of(entityId)).contains(entityId)) {
			throw new NoSuchElementException("entity " + entityId);
		}
		EntityStore.StoredEntity entity = this.entityStore.find(entityId)
			.filter((found) -> !found.deleted())
			.orElseThrow(() -> new NoSuchElementException("entity " + entityId));
		PropertyAccess.Snapshot snapshot = this.access.of(principal);
		boolean mayWrite = this.permissions.permissions(principal).contains("entity:write");
		MetatypeRules rules = MetatypeRules.load(this.ruleStore, LocalDate.now(this.clock))
			.forWriter(PropertyWriteAccess.writer(snapshot));
		MetatypeRules.Metatype metatype = rules.metatype(entity.metatypeId());
		List<Value> values = new ArrayList<>();
		Map<UUID, String> overridden = new LinkedHashMap<>();
		for (PropertyValueStore.NamedValue named : this.valueStore.labeledValues(List.of(entityId))
			.getOrDefault(entityId, List.of())) {
			if (snapshot.readableFor(named.propertyDefId(), entityId)) {
				values.add(new Value(named.propertyDefId(), named.propertyName(), named.dataType(), false,
						typed(named.value())));
				if (snapshot.throughOverrideFor(named.propertyDefId(), entityId)) {
					overridden.put(named.propertyDefId(), named.propertyName());
				}
			}
			else {
				values.add(new Value(named.propertyDefId(), named.propertyName(), named.dataType(), true, null));
			}
		}
		List<Field> fields = new ArrayList<>();
		if (metatype != null) {
			metatype.mappings().keySet().forEach((definition) -> fields.add(new Field(definition,
					snapshot.readableFor(definition, entityId),
					mayWrite && PropertyWriteAccess.writer(snapshot).test(definition, entityId))));
		}
		MetatypeStore.NameRow name = this.metatypes.nameAndAbbreviation(entity.metatypeId());
		List<String> missing = missingMandatory(Map.of(entityId, entity.metatypeId()))
			.getOrDefault(entityId, List.of());
		if (!overridden.isEmpty()) {
			Map<UUID, List<Integer>> entities = new LinkedHashMap<>();
			overridden.keySet().forEach((definition) -> entities.put(definition, List.of(entityId)));
			this.overrideAudit.append(principal, "diagram", 0, "diagram", id.toString(), overridden, entities);
		}
		Assignments assignments = null;
		if (byShape) {
			Map<UUID, Assignments> carried = assignmentsOf(this.viewpoints.get(diagram.viewpoint().id()), shownShapes);
			assignments = shownShapes.stream()
				.filter((shape) -> shape.entityId() == entityId && carried.containsKey(shape.id()))
				.map((shape) -> carried.get(shape.id()))
				.findFirst()
				.orElse(null);
		}
		return new Details(entity.id(), entity.name(), entity.description(), entity.metatypeId(), name.name(),
				name.abbreviation(), entity.version(), values, fields, missing, assignments);
	}

	/**
	 * The connections to offer when entities are placed (design D2 of us-074-diagram-connections):
	 * not deleted, of a type the viewpoint allows, with one end among {@code entityIds} and the
	 * other among {@code entityIds} or {@code shownEntityIds}, by connection ID, at most
	 * {@link #MAX_OFFERED_CONNECTIONS}.
	 *
	 * @throws NoSuchElementException for an unknown or deleted diagram (404)
	 */
	@Transactional(readOnly = true)
	public Offers connections(UUID id, List<Integer> entityIds, List<Integer> shownEntityIds) {
		Diagrams.Diagram diagram = this.diagrams.get(id);
		Viewpoints.Viewpoint viewpoint = this.viewpoints.get(diagram.viewpoint().id());
		List<UUID> types = viewpoint.connectionTypes().stream().map(Viewpoints.Type::id).toList();
		DiagramEdgeStore.FoundPage found = this.edges.find(entityIds, shownEntityIds, types,
				MAX_OFFERED_CONNECTIONS);
		return new Offers(found.items()
			.stream()
			.map((item) -> new Offer(item.connectionId(), item.name(), item.metatypeId(), item.sourceId(),
					item.targetId()))
			.toList(), found.truncated());
	}

	private static Typed typed(LabeledValue labeled) {
		TypedValue value = (labeled != null) ? labeled.value() : null;
		if (value == null) {
			return null;
		}
		Reference reference = (value.reference() != null)
				? new Reference(value.reference(), labeled.referenceName(), labeled.referenceAbbreviation()) : null;
		return new Typed(value.text(), value.integer(), value.bool(), value.date(), value.enumValue(), reference);
	}

	/**
	 * One page of the active element entities of exactly the metatype that the reference property
	 * references, by name ignoring case, then ID; {@code q} filters by a name part (wildcards literal).
	 *
	 * @throws NoSuchElementException for an unknown or deleted diagram (404)
	 * @throws DiagramViewException naming {@code propertyDefId} for an unknown property definition or
	 *         one that is not a reference property (422)
	 */
	@Transactional(readOnly = true)
	public EntityPage referenceTargets(UUID id, UUID propertyDefId, String part, String q, int page, int pageSize) {
		this.diagrams.get(id);
		if ((propertyDefId == null) == (part == null)) {
			throw new DiagramViewException(DiagramViewException.Reason.INVALID,
					"Exactly one of propertyDefId and part is needed.",
					List.of(new Issue("part", "PART_OR_PROPERTY", "Exactly one of propertyDefId and part is needed.")));
		}
		List<UUID> types;
		if (part != null) {
			types = partTypes(part);
		}
		else {
			MetatypeRules.Definition definition = MetatypeRules.load(this.ruleStore, LocalDate.now(this.clock))
				.definition(propertyDefId);
			if (definition == null || !"reference".equals(definition.dataType()) || definition.refMetatypeId() == null) {
				throw new DiagramViewException(DiagramViewException.Reason.INVALID,
						"The property definition is unknown or not a reference property.",
						List.of(new Issue("propertyDefId", "NOT_A_REFERENCE_PROPERTY",
								"The property definition is unknown or not a reference property.")));
			}
			types = List.of(definition.refMetatypeId());
		}
		String like = (q != null && !q.isBlank()) ? "%" + escape(q) + "%" : null;
		long total = this.search.count(types, like);
		List<Found> items = this.search.page(types, like, pageSize, (long) page * pageSize)
			.stream()
			.map((found) -> new Found(found.id(), found.name(),
					new Viewpoints.Type(found.metatypeId(), found.metatypeName(), found.metatypeAbbreviation()),
					found.childCount(), found.parentEntityId()))
			.toList();
		int totalPages = Math.toIntExact((total + pageSize - 1) / pageSize);
		return new EntityPage(items, page, pageSize, total, totalPages);
	}

	/** The metatype of an element part of the organization model and its subtypes; 422 naming {@code part} if unset. */
	private List<UUID> partTypes(String part) {
		OrganizationModels.Parts parts = this.organizationModels.get().parts();
		UUID root = switch (part) {
			case "org-unit" -> parts.orgUnit();
			case "role" -> parts.role();
			case "person" -> parts.person();
			default -> null;
		};
		if (root == null) {
			throw new DiagramViewException(DiagramViewException.Reason.INVALID,
					"The part is unknown or not set in the organization model.",
					List.of(new Issue("part", "PART_NOT_SET", "The part is unknown or not set in the organization model.")));
		}
		Map<UUID, UUID> bases = this.metatypes.bases();
		List<UUID> types = new ArrayList<>(List.of(root));
		this.metatypes.ids().stream().filter((type) -> !type.equals(root) && isOf(type, root, bases)).forEach(types::add);
		return types;
	}

	/**
	 * Saves the layout of the diagram with the expected version and returns the new view.
	 *
	 * @throws PermissionDeniedException for a principal without a person (403)
	 * @throws NoSuchElementException for an unknown or deleted diagram (404)
	 * @throws io.oea.app.diagram.DiagramChangeException for a missing (428) or stale (412) version
	 * @throws DiagramViewException for a value with more than two decimals (400) or shapes, new
	 *         entities, entity changes, parents and hierarchy lines that violate a rule, the faulty
	 *         entity writes included (422)
	 * @throws io.oea.app.entity.EntityVersionConflictException for a changed entity or an entity with a
	 *         parent change with another version than expected (409)
	 * @throws io.oea.app.entity.EntityBlockedException for such an entity with an open issue (409)
	 */
	@Transactional
	public View save(OeaPrincipal principal, UUID id, Long expectedVersion, List<ShapeInput> shapeInputs,
			List<NewEntityInput> newEntities, List<EntityUpdateInput> updates, List<EdgeInput> edgeInputs,
			List<NewConnectionInput> newConnections, List<LineInput> lineInputs,
			List<ParentChangeInput> parentChanges, List<RemovedConnectionInput> removed, AuditEvent.Agent agent) {
		if (principal.personId() == null) {
			throw new PermissionDeniedException();
		}
		checkNumbers(shapeInputs, edgeInputs);
		requireRemoval(principal, removed);
		Diagrams.Diagram diagram = this.diagrams.lockForLayout(id, expectedVersion);
		// the hierarchy lock comes before any entity lock, as in setEntityParent (design D3 step 1);
		// the removal of connections takes it as the deletion does
		boolean parents = !parentChanges.isEmpty() || !removed.isEmpty()
				|| newEntities.stream().anyMatch((entity) -> entity.parentEntityId() != null || entity.parentNewEntityKey() != null);
		if (parents) {
			this.hierarchyStore.lock();
		}
		Viewpoints.Viewpoint viewpoint = this.viewpoints.get(diagram.viewpoint().id());
		Set<UUID> allowed = new HashSet<>();
		viewpoint.metatypes().forEach((type) -> allowed.add(type.id()));
		Set<UUID> allowedConnections = new HashSet<>();
		viewpoint.connectionTypes().forEach((type) -> allowedConnections.add(type.id()));
		List<StoredShape> stored = this.shapes.stored(id);
		List<StoredEdge> storedEdges = this.edges.stored(id);
		List<String> conflicts = new ArrayList<>();
		AssignmentRules rules = new AssignmentRules(this.organizationModels.get().parts(), this.metatypes.bases());
		List<Issue> errors = validate(id, shapeInputs, newEntities, newConnections, allowed, rules,
				typesMappedTo(viewpoint, SUB_PROCESS), typesMappedTo(viewpoint, POOL), stored, conflicts);
		errors.addAll(validateNesting(shapeInputs));
		errors.addAll(validateEdges(id, shapeInputs, edgeInputs, newConnections, newEntities, allowedConnections,
				rules, storedEdges, conflicts));
		errors.addAll(validateRemovals(removed, shapeInputs, stored, edgeInputs, rules));
		errors.addAll(validateUpdates(updates, shapeInputs, edgeInputs));
		errors.addAll(validateLines(id, shapeInputs, lineInputs));
		errors.addAll(validateParentChanges(parentChanges, shapeInputs, newEntities));
		if (!errors.isEmpty()) {
			throw invalid(errors);
		}
		if (!conflicts.isEmpty()) {
			// after the 422 of the layout, before the entity writes and their 403 (design D3)
			throw new DiagramViewException(DiagramViewException.Reason.CONFLICTS,
					conflicts.size() + " shape(s) or edge(s) of the layout are conflicts: the viewpoint does not allow "
							+ "their type; nothing was saved.",
					conflicts.stream()
						.map((path) -> new Issue(path, "DIAGRAM_CONFLICT",
								"The viewpoint of the diagram does not allow the type."))
						.toList());
		}
		update(principal, updates, parentChanges, agent);
		Map<UUID, Integer> created = create(principal, newEntities, agent);
		int parentsChanged = writeParents(principal, newEntities, parentChanges, created, agent);
		checkLines(shapeInputs, lineInputs, created);
		removeConnections(principal, removed);
		Map<UUID, Integer> createdConnections = createConnections(principal, shapeInputs, edgeInputs, newConnections,
				created, agent);
		checkOrgUnitLimit(shapeInputs, newEntities, newConnections, created, rules, viewpoint);
		List<ShapeWrite> writes = new ArrayList<>();
		for (ShapeInput shape : shapeInputs) {
			int entityId = (shape.entityId() != null) ? shape.entityId() : created.get(shape.newEntityKey());
			writes.add(new ShapeWrite(shape.id(), entityId, shape.x(), shape.y(), shape.width(), shape.height(),
					shape.parentShapeId(), shape.collapsed(), shape.orientation()));
		}
		List<EdgeWrite> edgeWrites = new ArrayList<>();
		for (EdgeInput edge : edgeInputs) {
			int connectionId = (edge.connectionId() != null) ? edge.connectionId()
					: createdConnections.get(edge.newConnectionKey());
			edgeWrites.add(new EdgeWrite(edge.id(), connectionId, edge.sourceShapeId(), edge.targetShapeId(),
					edge.points().stream().map((point) -> new DiagramEdgeStore.Point(point.x(), point.y())).toList()));
		}
		this.edges.deleteExcept(id, keptEdges(stored, storedEdges, writes, edgeWrites));
		this.shapes.replace(id, writes);
		this.lines.replace(id, lineInputs.stream()
			.map((line) -> new DiagramHierarchyLineStore.Line(line.id(), line.parentShapeId(), line.childShapeId()))
			.toList());
		this.edges.upsert(id, edgeWrites);
		int version = this.diagrams.layoutSaved(principal, id);
		audit(principal, id, diagram.name(), version, count(stored, writes), countEdges(storedEdges, edgeWrites),
				created.size(), createdConnections.size(), parentsChanged, removed.size());
		return buildView(this.diagrams.get(id));
	}

	/**
	 * Requires {@code entity:delete} when the save removes connections (design D4 of
	 * us-075-lane-assignments); the controller calls it with the scoping check, so that the 403 comes before
	 * the version check and every 422.
	 *
	 * @throws PermissionDeniedException without the permission (403)
	 */
	public void requireRemoval(OeaPrincipal principal, List<RemovedConnectionInput> removed) {
		if (!removed.isEmpty() && !this.permissions.permissions(principal).contains("entity:delete")) {
			throw new PermissionDeniedException();
		}
	}

	/** The organization model as the rules of assignments need it, with the base types of the metatypes. */
	private record AssignmentRules(OrganizationModels.Parts parts, Map<UUID, UUID> bases) {

		boolean isAssignmentConnection(UUID metatype) {
			return isOf(metatype, this.parts.belongsTo(), this.bases)
					|| isOf(metatype, this.parts.performsRole(), this.bases)
					|| isOf(metatype, this.parts.responsible(), this.bases);
		}

		boolean isPartElement(UUID metatype) {
			return isOf(metatype, this.parts.orgUnit(), this.bases) || isOf(metatype, this.parts.role(), this.bases)
					|| isOf(metatype, this.parts.person(), this.bases);
		}

	}

	/**
	 * Checks the numbers of the request that the contract cannot: at most two decimals of a position,
	 * size or bend point. The controller calls it before the scoping check, so that the 400 comes
	 * first (design D3 step 0).
	 *
	 * @throws DiagramViewException for a value with more than two decimals (400)
	 */
	public void checkNumbers(List<ShapeInput> shapes, List<EdgeInput> edges) {
		List<Issue> errors = new ArrayList<>();
		for (int i = 0; i < shapes.size(); i++) {
			ShapeInput shape = shapes.get(i);
			decimals(errors, "shapes[" + i + "].x", shape.x());
			decimals(errors, "shapes[" + i + "].y", shape.y());
			decimals(errors, "shapes[" + i + "].width", shape.width());
			decimals(errors, "shapes[" + i + "].height", shape.height());
		}
		for (int i = 0; i < edges.size(); i++) {
			List<Point> points = edges.get(i).points();
			for (int j = 0; j < points.size(); j++) {
				decimals(errors, "edges[" + i + "].points[" + j + "].x", points.get(j).x());
				decimals(errors, "edges[" + i + "].points[" + j + "].y", points.get(j).y());
			}
		}
		if (!errors.isEmpty()) {
			throw new DiagramViewException(DiagramViewException.Reason.INVALID_REQUEST,
					"A position, size or bend point has more than " + MAX_DECIMALS + " decimals.", errors);
		}
	}

	private static void decimals(List<Issue> errors, String field, BigDecimal value) {
		if (value.stripTrailingZeros().scale() > MAX_DECIMALS) {
			errors.add(new Issue(field, "TOO_MANY_DECIMALS", "The value has at most " + MAX_DECIMALS + " decimals."));
		}
	}

	private List<Issue> validate(UUID diagramId, List<ShapeInput> shapeInputs, List<NewEntityInput> newEntities,
			List<NewConnectionInput> newConnections, Set<UUID> allowed, AssignmentRules rules, Set<UUID> subProcesses,
			Set<UUID> pools, List<StoredShape> stored, List<String> conflicts) {
		List<Issue> errors = new ArrayList<>();
		Map<UUID, Integer> keys = new HashMap<>();
		Set<UUID> shownKeys = new HashSet<>();
		shapeInputs.stream().filter((shape) -> shape.newEntityKey() != null).forEach((shape) -> shownKeys.add(shape.newEntityKey()));
		Set<UUID> targetKeys = new HashSet<>();
		newConnections.stream()
			.filter((connection) -> connection.targetNewEntityKey() != null)
			.forEach((connection) -> targetKeys.add(connection.targetNewEntityKey()));
		for (int i = 0; i < newEntities.size(); i++) {
			NewEntityInput entity = newEntities.get(i);
			if (keys.putIfAbsent(entity.key(), i) != null) {
				errors.add(new Issue("newEntities[" + i + "].key", "DUPLICATE_KEY",
						"The key occurs earlier in the new entities."));
			}
			if (!shownKeys.contains(entity.key()) && targetKeys.contains(entity.key())) {
				// no shape shows it: an organizational unit, a role or a person, instead of a type the viewpoint allows
				if (!rules.isPartElement(entity.metatypeId())) {
					errors.add(new Issue("newEntities[" + i + "].metatypeId", "METATYPE_NOT_ALLOWED",
							"An entity without a shape is an organizational unit, a role or a person of the organization model."));
				}
			}
			else if (!allowed.contains(entity.metatypeId())) {
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
					if (shape.collapsed()
							&& !subProcesses.contains(newEntities.get(keys.get(shape.newEntityKey())).metatypeId())) {
						collapsedIssue(errors, path);
					}
					if (VERTICAL.equals(shape.orientation())
							&& !pools.contains(newEntities.get(keys.get(shape.newEntityKey())).metatypeId())) {
						orientationIssue(errors, path);
					}
				}
				else {
					errors.add(new Issue(path + ".newEntityKey", "NEW_ENTITY_UNKNOWN",
							"The key names no entity of newEntities."));
				}
			}
			else {
				EntityRef entity = entities.get(shape.entityId());
				if (shape.collapsed() && entity != null && !subProcesses.contains(entity.metatypeId())) {
					collapsedIssue(errors, path);
				}
				if (VERTICAL.equals(shape.orientation()) && entity != null && !pools.contains(entity.metatypeId())) {
					orientationIssue(errors, path);
				}
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
					errors.add(new Issue(path + ".entityId", "METATYPE_NOT_ALLOWED",
							"The viewpoint of the diagram does not allow the metatype of the entity."));
				}
				else if (!allowed.contains(entity.metatypeId())) {
					// a stored shape that keeps its entity is no 422 but a conflict (design D3)
					conflicts.add(path);
				}
			}
		}
		for (int i = 0; i < newEntities.size(); i++) {
			if (!usedKeys.contains(newEntities.get(i).key()) && !targetKeys.contains(newEntities.get(i).key())) {
				errors.add(new Issue("newEntities[" + i + "]", "NEW_ENTITY_UNUSED",
						"No shape shows the new entity and no new connection has it as target."));
			}
		}
		return errors;
	}

	private static void orientationIssue(List<Issue> errors, String path) {
		errors.add(new Issue(path + ".orientation", "ORIENTATION_NOT_POOL",
				"Only the shape of a pool can be vertical: the viewpoint must map its metatype to bpmn:Pool."));
	}

	private static void collapsedIssue(List<Issue> errors, String path) {
		errors.add(new Issue(path + ".collapsed", "COLLAPSED_NOT_SUB_PROCESS",
				"Only the shape of a sub-process can be collapsed: the viewpoint must map its metatype to bpmn:SubProcess."));
	}

	/** The entity a shape shows: an existing one by ID, or a new one by key. */
	private record Shown(Integer entityId, UUID newEntityKey) {
	}

	private List<Issue> validateEdges(UUID diagramId, List<ShapeInput> shapeInputs, List<EdgeInput> edgeInputs,
			List<NewConnectionInput> newConnections, List<NewEntityInput> newEntities, Set<UUID> allowedConnections,
			AssignmentRules rules, List<StoredEdge> storedEdges, List<String> conflicts) {
		List<Issue> errors = new ArrayList<>();
		Map<UUID, Shown> shapes = new HashMap<>();
		shapeInputs.forEach((shape) -> shapes.put(shape.id(), new Shown(shape.entityId(), shape.newEntityKey())));
		Map<UUID, StoredEdge> storedById = new HashMap<>();
		storedEdges.forEach((edge) -> storedById.put(edge.id(), edge));
		Map<UUID, Integer> keys = new HashMap<>();
		Set<UUID> referencedByEdge = new HashSet<>();
		edgeInputs.stream().filter((edge) -> edge.newConnectionKey() != null).forEach((edge) -> referencedByEdge.add(edge.newConnectionKey()));
		Set<UUID> newEntityKeys = new HashSet<>();
		newEntities.forEach((entity) -> newEntityKeys.add(entity.key()));
		for (int i = 0; i < newConnections.size(); i++) {
			NewConnectionInput connection = newConnections.get(i);
			if (keys.putIfAbsent(connection.key(), i) != null) {
				errors.add(new Issue("newConnections[" + i + "].key", "DUPLICATE_KEY",
						"The key occurs earlier in the new connections."));
			}
			String path = "newConnections[" + i + "]";
			if (connection.hasEndpoints() && !referencedByEdge.contains(connection.key())) {
				assignmentIssues(errors, path, connection, rules, shapes, newEntityKeys);
			}
			else if (connection.hasEndpoints()) {
				errors.add(new Issue(path, "ENDPOINTS_WITH_EDGE",
						"A new connection that an edge shows takes its endpoints from the edge and names none."));
			}
			if ((!connection.hasEndpoints() || referencedByEdge.contains(connection.key()))
					&& !allowedConnections.contains(connection.metatypeId())) {
				errors.add(new Issue(path + ".metatypeId", "METATYPE_NOT_ALLOWED",
						"The viewpoint of the diagram does not allow this connection metatype."));
			}
		}
		List<Integer> connectionIds = edgeInputs.stream().map(EdgeInput::connectionId).filter((c) -> c != null).distinct().toList();
		Map<Integer, EntityRef> refs = this.shapes.entities(connectionIds);
		Map<Integer, DiagramEdgeStore.Endpoints> endpoints = this.edges.endpoints(connectionIds);
		Set<UUID> foreign = this.edges.idsOfOtherDiagrams(diagramId, edgeInputs.stream().map(EdgeInput::id).toList());
		Set<UUID> ids = new HashSet<>();
		// the endpoints of the first edge of each new connection
		Map<UUID, Shown[]> firstEnds = new HashMap<>();
		for (int i = 0; i < edgeInputs.size(); i++) {
			EdgeInput edge = edgeInputs.get(i);
			String path = "edges[" + i + "]";
			if (!ids.add(edge.id())) {
				errors.add(new Issue(path + ".id", "DUPLICATE_EDGE_ID", "The edge ID occurs earlier in the request."));
			}
			if (foreign.contains(edge.id())) {
				errors.add(new Issue(path + ".id", "EDGE_ID_IN_USE", "The edge ID belongs to another diagram."));
			}
			Shown source = shapes.get(edge.sourceShapeId());
			Shown target = shapes.get(edge.targetShapeId());
			if (source == null) {
				errors.add(new Issue(path + ".sourceShapeId", "SHAPE_NOT_IN_LAYOUT",
						"The source shape is not a shape of the saved layout."));
			}
			if (target == null) {
				errors.add(new Issue(path + ".targetShapeId", "SHAPE_NOT_IN_LAYOUT",
						"The target shape is not a shape of the saved layout."));
			}
			if ((edge.connectionId() == null) == (edge.newConnectionKey() == null)) {
				errors.add(new Issue(path, "CONNECTION_REFERENCE_INVALID",
						"An edge names exactly one of connectionId and newConnectionKey."));
			}
			else if (edge.newConnectionKey() != null) {
				if (!keys.containsKey(edge.newConnectionKey())) {
					errors.add(new Issue(path + ".newConnectionKey", "NEW_CONNECTION_UNKNOWN",
							"The key names no connection of newConnections."));
				}
				else if (source != null && target != null) {
					Shown[] first = firstEnds.putIfAbsent(edge.newConnectionKey(), new Shown[] { source, target });
					if (first != null && (!first[0].equals(source) || !first[1].equals(target))) {
						errors.add(new Issue(path, "NEW_CONNECTION_ENDPOINTS_DIFFER",
								"All edges of a new connection join shapes of the same source and target entity."));
					}
				}
			}
			else {
				EntityRef ref = refs.get(edge.connectionId());
				DiagramEdgeStore.Endpoints ends = endpoints.get(edge.connectionId());
				StoredEdge kept = storedById.get(edge.id());
				if (ref == null || ref.deleted()) {
					errors.add(new Issue(path + ".connectionId", "CONNECTION_NOT_FOUND",
							"The connection does not exist or is deleted."));
				}
				else if (!ref.connection() || ends == null) {
					errors.add(new Issue(path + ".connectionId", "ENTITY_NOT_CONNECTION",
							"An edge shows a connection, not an element entity."));
				}
				else {
					if (source != null && !Integer.valueOf(ends.sourceId()).equals(source.entityId())) {
						errors.add(new Issue(path + ".sourceShapeId", "SOURCE_SHAPE_MISMATCH",
								"The source shape does not show the source entity of the connection."));
					}
					if (target != null && !Integer.valueOf(ends.targetId()).equals(target.entityId())) {
						errors.add(new Issue(path + ".targetShapeId", "TARGET_SHAPE_MISMATCH",
								"The target shape does not show the target entity of the connection."));
					}
					if (!allowedConnections.contains(ref.metatypeId())
							&& !(kept != null && kept.connectionId() == edge.connectionId())) {
						errors.add(new Issue(path + ".connectionId", "METATYPE_NOT_ALLOWED",
								"The viewpoint of the diagram does not allow the metatype of the connection."));
					}
					else if (!allowedConnections.contains(ref.metatypeId())) {
						// a stored edge that keeps its connection is no 422 but a conflict (design D3)
						conflicts.add(path);
					}
				}
			}
		}
		Set<UUID> referenced = new HashSet<>();
		edgeInputs.stream().filter((edge) -> edge.newConnectionKey() != null).forEach((edge) -> referenced.add(edge.newConnectionKey()));
		for (int i = 0; i < newConnections.size(); i++) {
			if (!referenced.contains(newConnections.get(i).key()) && !newConnections.get(i).hasEndpoints()) {
				errors.add(new Issue("newConnections[" + i + "]", "NEW_CONNECTION_UNUSED",
						"No edge shows the new connection and it names no endpoints."));
			}
		}
		return errors;
	}

	/**
	 * A new connection without an edge is an assignment (design D4 of us-075-lane-assignments): its
	 * metatype is the belongs-to, performs-role or responsible type of the organization model or a
	 * subtype, its source a shape of the request and its target exactly one of an entity and a new entity.
	 */
	private static void assignmentIssues(List<Issue> errors, String path, NewConnectionInput connection,
			AssignmentRules rules, Map<UUID, Shown> shapes, Set<UUID> newEntityKeys) {
		if (!rules.isAssignmentConnection(connection.metatypeId())) {
			errors.add(new Issue(path + ".metatypeId", "METATYPE_NOT_ALLOWED",
					"A connection without an edge is an assignment of the organization model."));
		}
		if (connection.sourceShapeId() == null || !shapes.containsKey(connection.sourceShapeId())) {
			errors.add(new Issue(path + ".sourceShapeId", "SHAPE_NOT_IN_LAYOUT",
					"The source shape is missing or not a shape of the saved layout."));
		}
		if ((connection.targetEntityId() == null) == (connection.targetNewEntityKey() == null)) {
			errors.add(new Issue(path, "ASSIGNMENT_TARGET_INVALID",
					"A connection without an edge names exactly one of targetEntityId and targetNewEntityKey."));
		}
		else if (connection.targetNewEntityKey() != null && !newEntityKeys.contains(connection.targetNewEntityKey())) {
			errors.add(new Issue(path + ".targetNewEntityKey", "NEW_ENTITY_UNKNOWN",
					"The key names no entity of newEntities."));
		}
	}

	/**
	 * The removed connections (design D4): each once, not deleted, an assignment, from an entity of a
	 * shape of the request or the stored layout, and shown by no edge of the request.
	 */
	private List<Issue> validateRemovals(List<RemovedConnectionInput> removed, List<ShapeInput> shapeInputs,
			List<StoredShape> stored, List<EdgeInput> edgeInputs, AssignmentRules rules) {
		List<Issue> errors = new ArrayList<>();
		if (removed.isEmpty()) {
			return errors;
		}
		List<Integer> ids = removed.stream().map(RemovedConnectionInput::connectionId).distinct().toList();
		Map<Integer, EntityRef> refs = this.shapes.entities(ids);
		Map<Integer, Integer> sources = this.assignmentStore.sources(ids);
		Set<Integer> onDiagram = new HashSet<>();
		shapeInputs.stream().filter((shape) -> shape.entityId() != null).forEach((shape) -> onDiagram.add(shape.entityId()));
		stored.forEach((shape) -> onDiagram.add(shape.entityId()));
		Set<Integer> shownByEdge = new HashSet<>();
		edgeInputs.stream().filter((edge) -> edge.connectionId() != null).forEach((edge) -> shownByEdge.add(edge.connectionId()));
		Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < removed.size(); i++) {
			int id = removed.get(i).connectionId();
			String path = "removedConnections[" + i + "].connectionId";
			EntityRef ref = refs.get(id);
			if (!seen.add(id)) {
				errors.add(new Issue(path, "DUPLICATE_ENTITY", "The connection is removed earlier in the request."));
			}
			else if (ref == null || ref.deleted() || !ref.connection()) {
				errors.add(new Issue(path, "CONNECTION_NOT_FOUND", "The connection does not exist or is deleted."));
			}
			else if (!rules.isAssignmentConnection(ref.metatypeId())) {
				errors.add(new Issue(path, "NOT_AN_ASSIGNMENT",
						"Only the connections of belongs-to, performs-role and responsible are removed this way."));
			}
			else if (!onDiagram.contains(sources.get(id))) {
				errors.add(new Issue(path, "SOURCE_NOT_ON_DIAGRAM",
						"No shape of the request or of the stored layout shows the source of the connection."));
			}
			else if (shownByEdge.contains(id)) {
				errors.add(new Issue(path, "REMOVED_CONNECTION_SHOWN", "An edge of the request shows the connection."));
			}
		}
		return errors;
	}

	private void removeConnections(OeaPrincipal principal, List<RemovedConnectionInput> removed) {
		if (removed.isEmpty()) {
			return;
		}
		List<EntityDeletions.Removal> removals = new ArrayList<>();
		for (int i = 0; i < removed.size(); i++) {
			removals.add(new EntityDeletions.Removal(removed.get(i).connectionId(), removed.get(i).expectedVersion(),
					"removedConnections[" + i + "]"));
		}
		this.deletions.deleteConnections(principal, removals);
	}

	/**
	 * A lane or pool has one organizational unit at most (BR-03, design D4): each shape drawn as lane
	 * or pool that is the source of a new belongs-to connection must end with one active belongs-to
	 * connection at most; checked in the diagram save only.
	 */
	private void checkOrgUnitLimit(List<ShapeInput> shapeInputs, List<NewEntityInput> newEntities,
			List<NewConnectionInput> newConnections, Map<UUID, Integer> created, AssignmentRules rules,
			Viewpoints.Viewpoint viewpoint) {
		Set<UUID> containers = new HashSet<>(typesMappedTo(viewpoint, LANE));
		containers.addAll(typesMappedTo(viewpoint, POOL));
		Map<UUID, UUID> newTypes = new HashMap<>();
		newEntities.forEach((entity) -> newTypes.put(entity.key(), entity.metatypeId()));
		Map<Integer, EntityRef> existing = this.shapes
			.entities(shapeInputs.stream().map(ShapeInput::entityId).filter(Objects::nonNull).distinct().toList());
		Set<Integer> checked = new HashSet<>();
		List<Issue> errors = new ArrayList<>();
		for (NewConnectionInput connection : newConnections) {
			if (connection.sourceShapeId() == null || !isOf(connection.metatypeId(), rules.parts().belongsTo(), rules.bases())) {
				continue;
			}
			for (int i = 0; i < shapeInputs.size(); i++) {
				ShapeInput shape = shapeInputs.get(i);
				if (!shape.id().equals(connection.sourceShapeId())) {
					continue;
				}
				UUID type = (shape.entityId() != null) ? existing.get(shape.entityId()).metatypeId()
						: newTypes.get(shape.newEntityKey());
				int entityId = entityOf(shape, created);
				if (containers.contains(type) && checked.add(entityId)
						&& this.assignmentStore.countFrom(entityId, rules.parts().belongsTo()) > 1) {
					errors.add(new Issue("shapes[" + i + "]", "ORG_UNIT_LIMIT",
							"A lane or a pool belongs to one organizational unit at most."));
				}
			}
		}
		if (!errors.isEmpty()) {
			throw invalid(errors);
		}
	}

	/** Each change names an entity that a shape or a connection that an edge of the request shows, once. */
	private static List<Issue> validateUpdates(List<EntityUpdateInput> updates, List<ShapeInput> shapeInputs,
			List<EdgeInput> edgeInputs) {
		List<Issue> errors = new ArrayList<>();
		Set<Integer> shown = new HashSet<>();
		shapeInputs.stream().filter((shape) -> shape.entityId() != null).forEach((shape) -> shown.add(shape.entityId()));
		edgeInputs.stream().filter((edge) -> edge.connectionId() != null).forEach((edge) -> shown.add(edge.connectionId()));
		Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < updates.size(); i++) {
			int entityId = updates.get(i).entityId();
			String path = "entityUpdates[" + i + "].entityId";
			if (!shown.contains(entityId)) {
				errors.add(new Issue(path, "ENTITY_NOT_SHOWN", "No shape or edge of the request shows the entity."));
			}
			else if (!seen.add(entityId)) {
				errors.add(new Issue(path, "DUPLICATE_ENTITY", "The entity is changed earlier in the request."));
			}
		}
		return errors;
	}

	/**
	 * The containers of the shapes (design D1, D3 of us-074-diagram-nesting): each names a shape of the
	 * request earlier in the drawing order (so never itself and never in a cycle), at most
	 * {@link #MAX_NESTING} levels deep, that contains it completely; positions are absolute.
	 */
	private static List<Issue> validateNesting(List<ShapeInput> shapeInputs) {
		List<Issue> errors = new ArrayList<>();
		Map<UUID, Integer> index = new HashMap<>();
		for (int i = 0; i < shapeInputs.size(); i++) {
			index.putIfAbsent(shapeInputs.get(i).id(), i);
		}
		int[] level = new int[shapeInputs.size()];
		for (int i = 0; i < shapeInputs.size(); i++) {
			ShapeInput shape = shapeInputs.get(i);
			level[i] = 1;
			if (shape.parentShapeId() == null) {
				continue;
			}
			String path = "shapes[" + i + "].parentShapeId";
			Integer parent = index.get(shape.parentShapeId());
			if (parent == null) {
				errors.add(new Issue(path, "PARENT_SHAPE_UNKNOWN", "The container is not a shape of the saved layout."));
			}
			else if (parent >= i) {
				errors.add(new Issue(path, "PARENT_SHAPE_NOT_EARLIER",
						"The container is the shape itself or comes later in the drawing order."));
			}
			else {
				ShapeInput container = shapeInputs.get(parent);
				level[i] = level[parent] + 1;
				if (level[i] > MAX_NESTING) {
					errors.add(new Issue(path, "NESTING_TOO_DEEP",
							"Shapes are nested at most " + MAX_NESTING + " levels deep."));
				}
				else if (!contains(container, shape)) {
					errors.add(new Issue(path, "OUTSIDE_PARENT", "The shape does not lie completely within its container."));
				}
			}
		}
		return errors;
	}

	/** Whether the container's rectangle contains the shape's, the edges included. */
	static boolean contains(ShapeInput container, ShapeInput shape) {
		return contains(container.x(), container.y(), container.width(), container.height(), shape.x(), shape.y(),
				shape.width(), shape.height());
	}

	private static boolean contains(BigDecimal cx, BigDecimal cy, BigDecimal cw, BigDecimal ch, BigDecimal x,
			BigDecimal y, BigDecimal w, BigDecimal h) {
		return x.compareTo(cx) >= 0 && y.compareTo(cy) >= 0 && x.add(w).compareTo(cx.add(cw)) <= 0
				&& y.add(h).compareTo(cy.add(ch)) <= 0;
	}

	/** Each line joins two different shapes of the request, each pair once (the hierarchy is checked after the parent writes). */
	private List<Issue> validateLines(UUID diagramId, List<ShapeInput> shapeInputs, List<LineInput> lineInputs) {
		List<Issue> errors = new ArrayList<>();
		Set<UUID> shapeIds = new HashSet<>();
		shapeInputs.forEach((shape) -> shapeIds.add(shape.id()));
		Set<UUID> foreign = this.lines.idsOfOtherDiagrams(diagramId, lineInputs.stream().map(LineInput::id).toList());
		Set<UUID> ids = new HashSet<>();
		Set<List<UUID>> pairs = new HashSet<>();
		for (int i = 0; i < lineInputs.size(); i++) {
			LineInput line = lineInputs.get(i);
			String path = "hierarchyLines[" + i + "]";
			if (!ids.add(line.id())) {
				errors.add(new Issue(path + ".id", "DUPLICATE_LINE_ID", "The line ID occurs earlier in the request."));
			}
			if (foreign.contains(line.id())) {
				errors.add(new Issue(path + ".id", "LINE_ID_IN_USE", "The line ID belongs to another diagram."));
			}
			boolean known = true;
			if (!shapeIds.contains(line.parentShapeId())) {
				errors.add(new Issue(path + ".parentShapeId", "SHAPE_NOT_IN_LAYOUT",
						"The container shape is not a shape of the saved layout."));
				known = false;
			}
			if (!shapeIds.contains(line.childShapeId())) {
				errors.add(new Issue(path + ".childShapeId", "SHAPE_NOT_IN_LAYOUT",
						"The child shape is not a shape of the saved layout."));
				known = false;
			}
			if (known && line.parentShapeId().equals(line.childShapeId())) {
				errors.add(new Issue(path, "LINE_SAME_SHAPE", "A line joins two different shapes."));
			}
			else if (known && !pairs.add(List.of(line.parentShapeId(), line.childShapeId()))) {
				errors.add(new Issue(path, "DUPLICATE_LINE", "The pair of shapes has a line earlier in the request."));
			}
		}
		return errors;
	}

	/**
	 * Each parent change names an element entity that a shape of the request shows, once, and at most one
	 * parent; the parents of new entities name at most one parent, not the entity itself, and a key of
	 * {@code newEntities}.
	 */
	private static List<Issue> validateParentChanges(List<ParentChangeInput> changes, List<ShapeInput> shapeInputs,
			List<NewEntityInput> newEntities) {
		List<Issue> errors = new ArrayList<>();
		Set<Integer> shown = new HashSet<>();
		shapeInputs.stream().filter((shape) -> shape.entityId() != null).forEach((shape) -> shown.add(shape.entityId()));
		Set<UUID> keys = new HashSet<>();
		newEntities.forEach((entity) -> keys.add(entity.key()));
		Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < changes.size(); i++) {
			ParentChangeInput change = changes.get(i);
			String path = "parentChanges[" + i + "]";
			if (!shown.contains(change.entityId())) {
				errors.add(new Issue(path + ".entityId", "ENTITY_NOT_SHOWN", "No shape of the request shows the entity."));
			}
			else if (!seen.add(change.entityId())) {
				errors.add(new Issue(path + ".entityId", "DUPLICATE_ENTITY",
						"The parent of the entity is changed earlier in the request."));
			}
			if (change.parentEntityId() != null && change.parentNewEntityKey() != null) {
				errors.add(new Issue(path, "PARENT_AMBIGUOUS",
						"A parent change names at most one of parentEntityId and parentNewEntityKey."));
			}
			else if (change.parentNewEntityKey() != null && !keys.contains(change.parentNewEntityKey())) {
				errors.add(new Issue(path + ".parentNewEntityKey", "NEW_ENTITY_UNKNOWN",
						"The key names no entity of newEntities."));
			}
		}
		for (int i = 0; i < newEntities.size(); i++) {
			NewEntityInput entity = newEntities.get(i);
			String path = "newEntities[" + i + "]";
			if (entity.parentEntityId() != null && entity.parentNewEntityKey() != null) {
				errors.add(new Issue(path, "PARENT_AMBIGUOUS",
						"A new entity names at most one of parentEntityId and parentNewEntityKey."));
			}
			else if (entity.parentNewEntityKey() != null) {
				if (entity.parentNewEntityKey().equals(entity.key())) {
					errors.add(new Issue(path + ".parentNewEntityKey", "PARENT_IS_SELF",
							"A new entity cannot be its own parent."));
				}
				else if (!keys.contains(entity.parentNewEntityKey())) {
					errors.add(new Issue(path + ".parentNewEntityKey", "NEW_ENTITY_UNKNOWN",
							"The key names no entity of newEntities."));
				}
			}
		}
		return errors;
	}

	/**
	 * Writes the parents in the transaction of the save (design D3 step 4): first the parents of the new
	 * entities in the order of {@code newEntities}, then {@code parentChanges} in request order, each
	 * with the rules of {@code setEntityParent}.
	 *
	 * @return the number of entities whose parent changed; a change to the current parent is not counted
	 */
	private int writeParents(OeaPrincipal principal, List<NewEntityInput> newEntities,
			List<ParentChangeInput> parentChanges, Map<UUID, Integer> created, AuditEvent.Agent agent) {
		int changed = 0;
		for (int i = 0; i < newEntities.size(); i++) {
			NewEntityInput entity = newEntities.get(i);
			Integer parent = (entity.parentEntityId() != null) ? entity.parentEntityId()
					: (entity.parentNewEntityKey() != null) ? created.get(entity.parentNewEntityKey()) : null;
			if (parent != null) {
				changed += writeParent(principal, created.get(entity.key()), parent, "newEntities[" + i + "]", agent);
			}
		}
		for (int i = 0; i < parentChanges.size(); i++) {
			ParentChangeInput change = parentChanges.get(i);
			Integer parent = (change.parentNewEntityKey() != null) ? created.get(change.parentNewEntityKey())
					: change.parentEntityId();
			changed += writeParent(principal, change.entityId(), parent, "parentChanges[" + i + "]", agent);
		}
		return changed;
	}

	private int writeParent(OeaPrincipal principal, int entityId, Integer parent, String path,
			AuditEvent.Agent agent) {
		try {
			EntityHierarchy.ParentResult result = this.hierarchy.setParentInTransaction(principal, entityId, parent,
					agent);
			return EntityHierarchy.UNCHANGED.equals(result.composition().reason()) ? 0 : 1;
		}
		catch (InvalidHierarchyException ex) {
			String field = ex.getInvalidFields().get(0);
			throw invalid(List.of(new Issue(path + "." + field, "PARENT_INVALID", ex.getMessage())));
		}
	}

	/** A line holds only while the entity of its child shape has the entity of its container shape as parent (after the parent writes). */
	private void checkLines(List<ShapeInput> shapeInputs, List<LineInput> lineInputs, Map<UUID, Integer> created) {
		if (lineInputs.isEmpty()) {
			return;
		}
		Map<UUID, Integer> entityOfShape = new HashMap<>();
		shapeInputs.forEach((shape) -> entityOfShape.put(shape.id(), entityOf(shape, created)));
		Map<Integer, Integer> parents = this.shapes.parents(new HashSet<>(entityOfShape.values()));
		List<Issue> errors = new ArrayList<>();
		for (int i = 0; i < lineInputs.size(); i++) {
			LineInput line = lineInputs.get(i);
			Integer container = entityOfShape.get(line.parentShapeId());
			Integer child = entityOfShape.get(line.childShapeId());
			if (!Objects.equals(parents.get(child), container)) {
				errors.add(new Issue("hierarchyLines[" + i + "]", "HIERARCHY_MISSING",
						"The entity of the child shape is not a child of the entity of the container shape."));
			}
		}
		if (!errors.isEmpty()) {
			throw invalid(errors);
		}
	}

	private void update(OeaPrincipal principal, List<EntityUpdateInput> updates,
			List<ParentChangeInput> parentChanges, AuditEvent.Agent agent) {
		if (updates.isEmpty() && parentChanges.isEmpty()) {
			return;
		}
		List<EntityBatch.EntityUpdate> inputs = updates.stream()
			.map((update) -> new EntityBatch.EntityUpdate(update.entityId(), update.expectedVersion(), update.name(),
					update.description(), update.properties()))
			.toList();
		List<EntityBatch.ParentTouch> touches = parentChanges.stream()
			.map((change) -> new EntityBatch.ParentTouch(change.entityId(), change.expectedVersion()))
			.toList();
		try {
			this.entities.updateInTransaction(principal, inputs, touches, agent);
		}
		catch (AtomicBatchFailedException ex) {
			throw invalid(ex.getErrors());
		}
	}

	private Map<UUID, Integer> create(OeaPrincipal principal, List<NewEntityInput> newEntities,
			AuditEvent.Agent agent) {
		Map<UUID, Integer> created = new HashMap<>();
		if (newEntities.isEmpty()) {
			return created;
		}
		List<EntityInput> inputs = newEntities.stream()
			.map((entity) -> new EntityInput(entity.name(), entity.metatypeId(), null, entity.description(), null,
					null, null, entity.properties()))
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

	/** Creates the new connections after the new entities; the endpoints are those of the first edge of each. */
	private Map<UUID, Integer> createConnections(OeaPrincipal principal, List<ShapeInput> shapeInputs,
			List<EdgeInput> edgeInputs, List<NewConnectionInput> newConnections, Map<UUID, Integer> createdEntities,
			AuditEvent.Agent agent) {
		Map<UUID, Integer> created = new HashMap<>();
		if (newConnections.isEmpty()) {
			return created;
		}
		Map<UUID, ShapeInput> shapes = new HashMap<>();
		shapeInputs.forEach((shape) -> shapes.put(shape.id(), shape));
		Map<UUID, EdgeInput> first = new HashMap<>();
		edgeInputs.stream()
			.filter((edge) -> edge.newConnectionKey() != null)
			.forEach((edge) -> first.putIfAbsent(edge.newConnectionKey(), edge));
		List<ConnectionInput> inputs = new ArrayList<>();
		for (NewConnectionInput connection : newConnections) {
			EntityInput entity = new EntityInput(connection.name(), connection.metatypeId(), null,
					connection.description(), null, null, null, connection.properties());
			if (connection.hasEndpoints()) {
				// an assignment: no edge, the endpoints are named (design D4 of us-075-lane-assignments)
				int target = (connection.targetEntityId() != null) ? connection.targetEntityId()
						: createdEntities.get(connection.targetNewEntityKey());
				inputs.add(new ConnectionInput(entity, entityOf(shapes.get(connection.sourceShapeId()), createdEntities),
						null, target, null));
				continue;
			}
			EdgeInput edge = first.get(connection.key());
			inputs.add(new ConnectionInput(entity, entityOf(shapes.get(edge.sourceShapeId()), createdEntities), null,
					entityOf(shapes.get(edge.targetShapeId()), createdEntities), null));
		}
		try {
			EntityBatch.Result result = this.entities.createConnectionsInTransaction(principal, inputs, agent);
			for (int i = 0; i < newConnections.size(); i++) {
				created.put(newConnections.get(i).key(), result.items().get(i).entityId());
			}
		}
		catch (AtomicBatchFailedException ex) {
			// "connections[i].sourceId" of the entity write is "newConnections[i]" with the batch code
			throw invalid(ex.getErrors().stream().map(DiagramViews::connectionIssue).toList());
		}
		return created;
	}

	private static int entityOf(ShapeInput shape, Map<UUID, Integer> createdEntities) {
		return (shape.entityId() != null) ? shape.entityId() : createdEntities.get(shape.newEntityKey());
	}

	private static Issue connectionIssue(Issue issue) {
		Matcher matcher = INDEXED_CONNECTION.matcher(issue.field());
		if (!matcher.find()) {
			return issue;
		}
		String base = "newConnections[" + matcher.group(1) + "]";
		String field = matcher.group(2);
		String rest = issue.field().substring(matcher.end());
		String path = (field == null || field.equals("sourceId") || field.equals("targetId")) ? base : base + "." + field + rest;
		return new Issue(path, issue.code(), issue.message());
	}

	/**
	 * The stored edges that stay besides those of the request: a hidden edge whose two shapes keep
	 * their entity (design D1, step 1). The others that the request does not contain are removed.
	 */
	private static Set<UUID> keptEdges(List<StoredShape> storedShapes, List<StoredEdge> storedEdges,
			List<ShapeWrite> writes, List<EdgeWrite> edgeWrites) {
		Set<UUID> keep = new HashSet<>();
		edgeWrites.forEach((edge) -> keep.add(edge.id()));
		Map<UUID, StoredShape> before = new HashMap<>();
		storedShapes.forEach((shape) -> before.put(shape.id(), shape));
		Map<UUID, ShapeWrite> after = new HashMap<>();
		writes.forEach((write) -> after.put(write.id(), write));
		for (StoredEdge edge : storedEdges) {
			if (edge.hidden() && keepsEntity(before, after, edge.sourceShapeId())
					&& keepsEntity(before, after, edge.targetShapeId())) {
				keep.add(edge.id());
			}
		}
		return keep;
	}

	private static boolean keepsEntity(Map<UUID, StoredShape> before, Map<UUID, ShapeWrite> after, UUID shapeId) {
		StoredShape old = before.get(shapeId);
		ShapeWrite write = after.get(shapeId);
		if (old == null) {
			return false;
		}
		// a shape that the request does not contain stays only while its entity is deleted
		return (write != null) ? write.entityId() == old.entityId() : old.hidden();
	}

	private record EdgeCounts(int added, int changed, int removed) {
	}

	/** The added, changed and removed edges; the hidden stored edges are not part of the view. */
	private static EdgeCounts countEdges(List<StoredEdge> stored, List<EdgeWrite> writes) {
		Map<UUID, StoredEdge> before = new LinkedHashMap<>();
		stored.stream().filter((edge) -> !edge.hidden()).forEach((edge) -> before.put(edge.id(), edge));
		Set<UUID> after = new HashSet<>();
		writes.forEach((write) -> after.add(write.id()));
		int added = 0;
		int changed = 0;
		for (EdgeWrite write : writes) {
			StoredEdge old = before.get(write.id());
			if (old == null) {
				added++;
			}
			else if (old.connectionId() != write.connectionId() || !old.sourceShapeId().equals(write.sourceShapeId())
					|| !old.targetShapeId().equals(write.targetShapeId()) || !samePoints(old.points(), write.points())) {
				changed++;
			}
		}
		int removed = (int) before.keySet().stream().filter((id) -> !after.contains(id)).count();
		return new EdgeCounts(added, changed, removed);
	}

	private static boolean samePoints(List<DiagramEdgeStore.Point> a, List<DiagramEdgeStore.Point> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (a.get(i).x().compareTo(b.get(i).x()) != 0 || a.get(i).y().compareTo(b.get(i).y()) != 0) {
				return false;
			}
		}
		return true;
	}

	private static DiagramViewException invalid(List<Issue> errors) {
		return new DiagramViewException(DiagramViewException.Reason.INVALID,
				errors.size() + " shape(s), edge(s), new entity(ies), new connection(s) or entity change(s) are invalid; "
						+ "nothing was saved.", errors);
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
					|| !Objects.equals(old.parentShapeId(), write.parentShapeId())
					|| old.collapsed() != write.collapsed() || !old.orientation().equals(write.orientation())
					|| orderBefore.indexOf(write.id()) != orderAfter.indexOf(write.id())) {
				changed++;
			}
		}
		int removed = (int) before.keySet().stream().filter((id) -> !after.contains(id)).count();
		return new Counts(added, changed, removed);
	}

	/** Whether the metatype is the part or one of its subtypes. */
	private static boolean isOf(UUID metatype, UUID part, Map<UUID, UUID> bases) {
		if (part == null) {
			return false;
		}
		UUID next = metatype;
		int guard = 0;
		while (next != null && guard++ < 64) {
			if (next.equals(part)) {
				return true;
			}
			next = bases.get(next);
		}
		return false;
	}

	/**
	 * The assignments of the shapes that carry them (design D3 of us-075-lane-assignments): shapes
	 * drawn as lane, pool, task or a task variant, and shapes lying in an assignment lane or pool,
	 * by shape ID. A part that is unset in the organization model gives no entries.
	 */
	private Map<UUID, Assignments> assignmentsOf(Viewpoints.Viewpoint viewpoint, List<ShownShape> shown) {
		Map<UUID, Assignments> result = new HashMap<>();
		if (shown.isEmpty()) {
			return result;
		}
		OrganizationModels.Parts parts = this.organizationModels.get().parts();
		Map<UUID, UUID> bases = this.metatypes.bases();
		Set<UUID> containers = new HashSet<>(typesMappedTo(viewpoint, LANE));
		containers.addAll(typesMappedTo(viewpoint, POOL));
		Set<UUID> carriers = new HashSet<>(containers);
		TASKS.forEach((key) -> carriers.addAll(typesMappedTo(viewpoint, key)));
		Map<UUID, Boolean> inAssignmentLane = new HashMap<>();
		Set<UUID> eligible = new HashSet<>();
		Set<Integer> entityIds = new HashSet<>();
		for (ShownShape shape : shown) {
			boolean inside = shape.parentShapeId() != null && inAssignmentLane.containsKey(shape.parentShapeId())
					&& inAssignmentLane.get(shape.parentShapeId());
			boolean assignmentLane = containers.contains(shape.metatypeId())
					&& (isOf(shape.metatypeId(), parts.orgUnit(), bases) || isOf(shape.metatypeId(), parts.role(), bases));
			inAssignmentLane.put(shape.id(), inside || assignmentLane);
			if (carriers.contains(shape.metatypeId()) || inside) {
				eligible.add(shape.id());
				entityIds.add(shape.entityId());
			}
		}
		Map<Integer, List<DiagramAssignmentStore.Assignment>> orgUnits = this.assignmentStore.assignments(entityIds,
				parts.belongsTo(), parts.orgUnit());
		Map<Integer, List<DiagramAssignmentStore.Assignment>> roles = this.assignmentStore.assignments(entityIds,
				parts.performsRole(), parts.role());
		Map<Integer, List<DiagramAssignmentStore.Assignment>> persons = this.assignmentStore.assignments(entityIds,
				parts.responsible(), parts.person());
		for (ShownShape shape : shown) {
			if (eligible.contains(shape.id())) {
				result.put(shape.id(), new Assignments(assignmentList(orgUnits, shape.entityId()),
						assignmentList(roles, shape.entityId()), assignmentList(persons, shape.entityId())));
			}
		}
		return result;
	}

	private static List<Assignment> assignmentList(Map<Integer, List<DiagramAssignmentStore.Assignment>> found,
			int entityId) {
		return found.getOrDefault(entityId, List.of())
			.stream()
			.map((assignment) -> new Assignment(assignment.connectionId(), assignment.connectionVersion(),
					assignment.entityId(), assignment.name()))
			.toList();
	}

	/** The allowed metatypes that the viewpoint renders as the notation element (the effective mapping). */
	private static Set<UUID> typesMappedTo(Viewpoints.Viewpoint viewpoint, String element) {
		Set<UUID> types = new HashSet<>();
		viewpoint.effectiveMappings()
			.stream()
			.filter((mapping) -> element.equals(mapping.notationElement()))
			.forEach((mapping) -> types.add(mapping.metatypeId()));
		return types;
	}

	private View buildView(Diagrams.Diagram diagram) {
		Viewpoints.Viewpoint viewpoint = this.viewpoints.get(diagram.viewpoint().id());
		List<ShownShape> shown = this.shapes.shown(diagram.id());
		List<ShownEdge> shownEdges = this.edges.shown(diagram.id());
		Map<Integer, UUID> metatypes = new HashMap<>();
		shown.forEach((shape) -> metatypes.put(shape.entityId(), shape.metatypeId()));
		shownEdges.forEach((edge) -> metatypes.put(edge.connectionId(), edge.metatypeId()));
		Map<Integer, List<String>> missing = missingMandatory(metatypes);
		// a container counts only when it is shown, earlier in the drawing order and contains the shape
		Set<UUID> allowed = new HashSet<>();
		viewpoint.metatypes().forEach((type) -> allowed.add(type.id()));
		Set<UUID> allowedConnections = new HashSet<>();
		viewpoint.connectionTypes().forEach((type) -> allowedConnections.add(type.id()));
		Set<UUID> subProcesses = typesMappedTo(viewpoint, SUB_PROCESS);
		Set<UUID> pools = typesMappedTo(viewpoint, POOL);
		Map<UUID, Assignments> assignments = assignmentsOf(viewpoint, shown);
		int conflicts = 0;
		Map<UUID, ShownShape> before = new HashMap<>();
		List<Shape> view = new ArrayList<>();
		for (ShownShape shape : shown) {
			// collapsed counts only while the viewpoint still maps the type to a sub-process (design D2)
			boolean collapsed = shape.collapsed() && subProcesses.contains(shape.metatypeId());
			String orientation = pools.contains(shape.metatypeId()) ? shape.orientation() : HORIZONTAL;
			ShownShape container = (shape.parentShapeId() != null) ? before.get(shape.parentShapeId()) : null;
			UUID parent = (container != null && contains(container.x(), container.y(), container.width(),
					container.height(), shape.x(), shape.y(), shape.width(), shape.height())) ? container.id() : null;
			view.add(new Shape(shape.id(), shape.entityId(), shape.entityName(), shape.metatypeId(), shape.x(),
					shape.y(), shape.width(), shape.height(), parent, collapsed, orientation, shape.childCount(),
					shape.parentEntityId(), missing.getOrDefault(shape.entityId(), List.of()),
					!allowed.contains(shape.metatypeId()), assignments.get(shape.id())));
			before.put(shape.id(), shape);
		}
		conflicts += (int) view.stream().filter(Shape::conflict).count();
		List<Edge> edgeView = shownEdges.stream()
			.map((edge) -> new Edge(edge.id(), edge.connectionId(), edge.connectionName(), edge.metatypeId(),
					edge.sourceShapeId(), edge.targetShapeId(),
					edge.points().stream().map((point) -> new Point(point.x(), point.y())).toList(),
					missing.getOrDefault(edge.connectionId(), List.of()),
					!allowedConnections.contains(edge.metatypeId())))
			.toList();
		conflicts += (int) edgeView.stream().filter(Edge::conflict).count();
		Set<UUID> shownIds = before.keySet();
		List<HierarchyLine> lineView = this.lines.shown(diagram.id())
			.stream()
			.filter((line) -> shownIds.contains(line.parentShapeId()) && shownIds.contains(line.childShapeId()))
			.map((line) -> new HierarchyLine(line.id(), line.parentShapeId(), line.childShapeId()))
			.toList();
		return new View(diagram, viewpoint, view, edgeView, lineView, this.settings.effective(viewpoint.askNewParent()),
				conflicts);
	}

	/**
	 * The names of the mandatory properties of the current metatype of each entity that have no
	 * value, ordered by name ignoring case. Incompleteness is computed, not stored (design D3).
	 */
	private Map<Integer, List<String>> missingMandatory(Map<Integer, UUID> metatypes) {
		Map<Integer, List<String>> missing = new HashMap<>();
		if (metatypes.isEmpty()) {
			return missing;
		}
		MetatypeRules rules = MetatypeRules.load(this.ruleStore, LocalDate.now(this.clock));
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

	private void audit(OeaPrincipal principal, UUID id, String name, int version, Counts counts,
			EdgeCounts edgeCounts, int entitiesCreated, int connectionsCreated, int parentsChanged,
			int connectionsRemoved) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", name);
		details.put("version", version);
		details.put("added", counts.added());
		details.put("changed", counts.changed());
		details.put("removed", counts.removed());
		details.put("entitiesCreated", entitiesCreated);
		details.put("edgesAdded", edgeCounts.added());
		details.put("edgesChanged", edgeCounts.changed());
		details.put("edgesRemoved", edgeCounts.removed());
		details.put("connectionsCreated", connectionsCreated);
		details.put("parentsChanged", parentsChanged);
		details.put("connectionsRemoved", connectionsRemoved);
		this.audit.append(new AuditEvent("diagram.layout-saved", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "diagram", id.toString(),
				details));
	}

}
