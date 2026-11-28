package io.oea.app.lineage;

import io.oea.app.entity.Issue;
import io.oea.app.metamodel.LineageModels;
import io.oea.app.metamodel.OverrideAccessAudit;
import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.security.OeaPrincipal;
import io.oea.core.entity.EntityStore;
import io.oea.core.lineage.LineageStore;
import io.oea.core.lineage.LineageStore.Carried;
import io.oea.core.lineage.LineageStore.CarryingFlow;
import io.oea.core.lineage.LineageStore.FlowRow;
import io.oea.core.metamodel.MetatypeStore;
import io.oea.core.metamodel.PropertyMappingStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The data lineage query (UC-08, US-065, REQ-062; design D4 of us-065-lineage-model-and-query): a
 * breadth-first walk over the active data flows from a data object or a system, one set-based
 * statement per hop, with a visited set in memory, so that a cycle ends and the work is bounded by
 * the depth and by the limits of 5000 nodes and 10000 edges.
 */
@Service
public class Lineages {

	/** The most nodes a result holds. */
	public static final int MAX_NODES = 5000;

	/** The most edges a result holds. */
	public static final int MAX_EDGES = 10000;

	/** The most paths a result holds. */
	public static final int MAX_PATHS = 100;

	/** The most carried data objects an edge names. */
	public static final int MAX_CARRIED = 50;

	/** The most steps the search for paths takes; it bounds the work on graphs with many branches. */
	private static final int PATH_STEPS = 200_000;

	/** The stable ID of the property "Transformation type" of {@code oea-starter-bpmn}. */
	public static final UUID TRANSFORMATION_TYPE = UUID.fromString("1b6e9540-fcfd-5cf4-afca-62dbc7785f25");

	/** The directions of a query. */
	public enum Direction {

		UPSTREAM("upstream"), DOWNSTREAM("downstream"), BOTH("both"), IMPACT("impact");

		private final String value;

		Direction(String value) {
			this.value = value;
		}

		/** The name in the API. */
		public String value() {
			return this.value;
		}

		boolean down() {
			return this != UPSTREAM;
		}

		boolean up() {
			return this == UPSTREAM || this == BOTH;
		}

	}

	/** A query; {@code metatypeIds} and {@code dataObjectId} may be empty or {@code null}. */
	public record Query(int startEntityId, Direction direction, int maxDepth, List<UUID> metatypeIds,
			Integer dataObjectId) {
	}

	/** The start of the lineage. */
	public record Start(int id, String name, UUID metatypeId) {
	}

	/** A system or a task reached by the walk. */
	public record Node(int id, String name, UUID metatypeId, List<String> roles, int depth, boolean impact) {
	}

	/** A data object a data flow carries, with the carrying connection. */
	public record CarriedBy(int connectionId, int dataObjectId, String dataObjectName) {
	}

	/** A data flow or a data association. */
	public record Edge(int id, String kind, UUID metatypeId, int sourceId, int targetId, List<CarriedBy> carriedBy,
			boolean carriedByTruncated, String transformationType, boolean transformationRestricted) {
	}

	/** The result of a query. */
	public record Result(Start start, Direction direction, boolean cycleDetected, boolean truncated, List<Node> nodes,
			List<Edge> edges, List<List<Integer>> paths) {
	}

	private final LineageModels models;

	private final LineageStore store;

	private final EntityStore entities;

	private final MetatypeStore metatypes;

	private final PropertyMappingStore mappings;

	private final PropertyAccess access;

	private final OverrideAccessAudit overrideAudit;

	private final Clock clock;

	Lineages(LineageModels models, LineageStore store, EntityStore entities, MetatypeStore metatypes,
			PropertyMappingStore mappings, PropertyAccess access, OverrideAccessAudit overrideAudit, Clock clock) {
		this.models = models;
		this.store = store;
		this.entities = entities;
		this.metatypes = metatypes;
		this.mappings = mappings;
		this.access = access;
		this.overrideAudit = overrideAudit;
		this.clock = clock;
	}

	/**
	 * Walks the lineage of the start entity.
	 *
	 * @throws LineageException 404 for an unknown or deleted start, 422 for a connection as start,
	 *     an incomplete lineage model, an unknown metatype or a data object that is none, 400 for a
	 *     data object with a data object start
	 */
	@Transactional(readOnly = true)
	public Result query(OeaPrincipal principal, Query query) {
		EntityStore.Endpoint start = this.entities.endpoint(query.startEntityId())
			.filter((found) -> !found.deleted())
			.orElseThrow(() -> new LineageException(LineageException.Reason.NOT_FOUND,
					"The start entity does not exist.", List.of()));
		if (this.entities.connectionEndpoints(start.id()).isPresent()) {
			throw invalid("startEntityId", "START_IS_CONNECTION", "The start must be an element, not a connection.");
		}
		LineageModels.Parts parts = this.models.get().parts();
		List<String> unset = new ArrayList<>();
		for (LineageModels.Part part : List.of(LineageModels.Part.DATA_OBJECT, LineageModels.Part.DATA_FLOW,
				LineageModels.Part.CARRIES_DATA)) {
			if (part.of(parts) == null) {
				unset.add(part.field());
			}
		}
		if (!unset.isEmpty()) {
			throw invalid("lineageModel", "LINEAGE_MODEL_INCOMPLETE",
					"The lineage model needs the parts " + String.join(", ", unset) + ".");
		}
		Map<UUID, UUID> bases = this.metatypes.bases();
		Set<UUID> known = new HashSet<>(this.metatypes.ids());
		List<UUID> filter = (query.metatypeIds() != null) ? query.metatypeIds() : List.of();
		if (!known.containsAll(filter)) {
			throw invalid("metatypeIds", "METATYPE_NOT_FOUND", "A metatype of the filter does not exist.");
		}
		Set<UUID> dataObjects = descendants(parts.dataObject(), bases);
		boolean dataObjectStart = dataObjects.contains(start.metatypeId());
		if (query.dataObjectId() != null) {
			if (dataObjectStart) {
				throw new LineageException(LineageException.Reason.INVALID_REQUEST,
						"A data object is only allowed with a start that is no data object.",
						List.of(new Issue("dataObjectId", "DATA_OBJECT_WITH_DATA_OBJECT_START",
								"A data object is only allowed with a start that is no data object.")));
			}
			this.entities.endpoint(query.dataObjectId())
				.filter((found) -> !found.deleted() && dataObjects.contains(found.metatypeId()))
				.orElseThrow(() -> invalid("dataObjectId", "NOT_A_DATA_OBJECT",
						"The data object is no active entity of the data object part."));
		}
		Context context = new Context(query, start, dataObjectStart, parts, bases);
		return context.run(principal);
	}

	private static LineageException invalid(String field, String code, String message) {
		return new LineageException(LineageException.Reason.INVALID, message, List.of(new Issue(field, code, message)));
	}

	/** The metatype and all its subtypes. */
	private static Set<UUID> descendants(UUID metatype, Map<UUID, UUID> bases) {
		Set<UUID> found = new HashSet<>();
		if (metatype == null) {
			return found;
		}
		found.add(metatype);
		for (UUID candidate : bases.keySet()) {
			if (chain(candidate, bases).contains(metatype)) {
				found.add(candidate);
			}
		}
		return found;
	}

	private static List<UUID> chain(UUID metatype, Map<UUID, UUID> bases) {
		List<UUID> chain = new ArrayList<>();
		UUID next = metatype;
		while (next != null && !chain.contains(next)) {
			chain.add(next);
			next = bases.get(next);
		}
		return chain;
	}

	/** A node under construction. */
	private static final class Acc {

		final int id;

		final String name;

		final UUID metatypeId;

		int depth;

		final Set<String> roles = new LinkedHashSet<>();

		Acc(int id, String name, UUID metatypeId, int depth) {
			this.id = id;
			this.name = name;
			this.metatypeId = metatypeId;
			this.depth = depth;
		}

	}

	/** An edge under construction. */
	private record EdgeAcc(int id, String kind, UUID metatypeId, int sourceId, int targetId) {
	}

	/** One query run. */
	private final class Context {

		private final Query query;

		private final EntityStore.Endpoint start;

		private final boolean dataObjectStart;

		private final LineageModels.Parts parts;

		private final Map<UUID, UUID> bases;

		private final Set<UUID> flowTypes;

		private final Set<UUID> carriesTypes;

		private final Direction direction;

		private final Map<Integer, Acc> nodes = new LinkedHashMap<>();

		private final Map<Integer, EdgeAcc> edges = new LinkedHashMap<>();

		private boolean truncated;

		/** For a data object start: the carrying connections by flow. */
		private final Map<Integer, List<Integer>> carrying = new HashMap<>();

		private final Set<Integer> originSeeds = new TreeSet<>();

		private final Set<Integer> sinkSeeds = new TreeSet<>();

		Context(Query query, EntityStore.Endpoint start, boolean dataObjectStart, LineageModels.Parts parts,
				Map<UUID, UUID> bases) {
			this.query = query;
			this.start = start;
			this.dataObjectStart = dataObjectStart;
			this.parts = parts;
			this.bases = bases;
			this.flowTypes = descendants(parts.dataFlow(), bases);
			this.carriesTypes = descendants(parts.carriesData(), bases);
			this.direction = (query.direction() != null) ? query.direction() : Direction.BOTH;
		}

		Result run(OeaPrincipal principal) {
			if (this.dataObjectStart) {
				walkDataObject();
			}
			else {
				walkSystem();
			}
			tasks();
			List<EdgeAcc> flows = this.edges.values().stream().filter((edge) -> "data-flow".equals(edge.kind())).toList();
			roles(flows);
			boolean cycle = hasCycle(flows);
			List<List<Integer>> paths = paths(flows);
			List<Edge> result = new ArrayList<>();
			Map<Integer, List<CarriedBy>> carried = new HashMap<>();
			Set<Integer> more = new HashSet<>();
			carriedBy(flows, carried, more);
			Transformations transformations = transformations(principal);
			for (EdgeAcc edge : this.edges.values()) {
				if ("data-flow".equals(edge.kind())) {
					result.add(new Edge(edge.id(), edge.kind(), edge.metatypeId(), edge.sourceId(), edge.targetId(),
							carried.getOrDefault(edge.id(), List.of()), more.contains(edge.id()), null, false));
				}
				else if ("data-output".equals(edge.kind())) {
					result.add(new Edge(edge.id(), edge.kind(), edge.metatypeId(), edge.sourceId(), edge.targetId(),
							List.of(), false, transformations.values().get(edge.id()),
							transformations.restricted().contains(edge.id())));
				}
				else {
					result.add(new Edge(edge.id(), edge.kind(), edge.metatypeId(), edge.sourceId(), edge.targetId(),
							List.of(), false, null, false));
				}
			}
			Set<UUID> filter = new HashSet<>(this.query.metatypeIds() != null ? this.query.metatypeIds() : List.of());
			Set<Integer> kept = new HashSet<>();
			List<Node> out = new ArrayList<>();
			for (Acc node : this.nodes.values()) {
				if (!filter.isEmpty() && chain(node.metatypeId, this.bases).stream().noneMatch(filter::contains)) {
					continue;
				}
				kept.add(node.id);
				out.add(new Node(node.id, node.name, node.metatypeId, List.copyOf(node.roles), node.depth,
						this.direction == Direction.IMPACT));
			}
			if (!filter.isEmpty()) {
				kept.add(this.start.id());
				paths = paths.stream()
					.map((path) -> path.stream().filter(kept::contains).toList())
					.filter((path) -> path.size() > 1)
					.toList();
			}
			return new Result(new Start(this.start.id(), this.start.name(), this.start.metatypeId()), this.direction,
					cycle, this.truncated, out, result, paths);
		}

		// ── walks ──────────────────────────────────────────────────────────────

		/** The walk from a system: one statement per hop. */
		private void walkSystem() {
			if (this.direction.down() && !walkSystem(true)) {
				return;
			}
			if (this.direction.up()) {
				walkSystem(false);
			}
		}

		private boolean walkSystem(boolean downstream) {
			Set<Integer> visited = new HashSet<>();
			visited.add(this.start.id());
			List<Integer> frontier = List.of(this.start.id());
			for (int depth = 0; depth < this.query.maxDepth() && !frontier.isEmpty(); depth++) {
				List<FlowRow> rows = Lineages.this.store.flows(frontier, downstream, this.flowTypes,
						this.query.dataObjectId(), this.carriesTypes);
				List<Integer> next = new ArrayList<>();
				for (FlowRow row : rows) {
					if (!reach(row, downstream, depth + 1, visited, next)) {
						return false;
					}
				}
				frontier = next;
			}
			return true;
		}

		/** The walk from a data object over the flows that carry it, in memory. */
		private void walkDataObject() {
			List<CarryingFlow> carrying = Lineages.this.store.carrying(this.start.id(), this.flowTypes,
					this.carriesTypes);
			Map<Integer, FlowRow> flows = new TreeMap<>();
			for (CarryingFlow found : carrying) {
				flows.putIfAbsent(found.flow().id(), found.flow());
				this.carrying.computeIfAbsent(found.flow().id(), (id) -> new ArrayList<>()).add(found.carriesId());
			}
			if (flows.isEmpty()) {
				return;
			}
			Map<Integer, List<FlowRow>> out = new TreeMap<>();
			Map<Integer, List<FlowRow>> in = new TreeMap<>();
			Map<Integer, Integer> parent = new HashMap<>();
			Map<Integer, String> names = new HashMap<>();
			Map<Integer, UUID> types = new HashMap<>();
			for (FlowRow flow : flows.values()) {
				out.computeIfAbsent(flow.sourceId(), (id) -> new ArrayList<>()).add(flow);
				in.computeIfAbsent(flow.targetId(), (id) -> new ArrayList<>()).add(flow);
				out.computeIfAbsent(flow.targetId(), (id) -> new ArrayList<>());
				in.computeIfAbsent(flow.sourceId(), (id) -> new ArrayList<>());
				names.put(flow.sourceId(), flow.sourceName());
				types.put(flow.sourceId(), flow.sourceMetatypeId());
				names.put(flow.targetId(), flow.targetName());
				types.put(flow.targetId(), flow.targetMetatypeId());
				union(parent, flow.sourceId(), flow.targetId());
			}
			Map<Integer, TreeSet<Integer>> components = new TreeMap<>();
			for (Integer node : out.keySet()) {
				components.computeIfAbsent(find(parent, node), (root) -> new TreeSet<>()).add(node);
			}
			for (TreeSet<Integer> component : components.values()) {
				List<Integer> origins = component.stream().filter((node) -> in.get(node).isEmpty()).toList();
				List<Integer> sinks = component.stream().filter((node) -> out.get(node).isEmpty()).toList();
				this.originSeeds.addAll(origins.isEmpty() ? List.of(component.first()) : origins);
				this.sinkSeeds.addAll(sinks.isEmpty() ? List.of(component.first()) : sinks);
			}
			if (this.direction.down() && !walkSeeds(this.originSeeds, true, out, in, names, types)) {
				return;
			}
			if (this.direction.up()) {
				walkSeeds(this.sinkSeeds, false, out, in, names, types);
			}
		}

		private boolean walkSeeds(Set<Integer> seeds, boolean downstream, Map<Integer, List<FlowRow>> out,
				Map<Integer, List<FlowRow>> in, Map<Integer, String> names, Map<Integer, UUID> types) {
			Set<Integer> visited = new HashSet<>();
			List<Integer> frontier = new ArrayList<>();
			for (Integer seed : seeds) {
				if (!addNode(seed, names.get(seed), types.get(seed), 0)) {
					return false;
				}
				visited.add(seed);
				frontier.add(seed);
			}
			for (int depth = 0; depth < this.query.maxDepth() && !frontier.isEmpty(); depth++) {
				List<Integer> next = new ArrayList<>();
				for (Integer node : frontier) {
					for (FlowRow row : (downstream ? out : in).get(node)) {
						if (!reach(row, downstream, depth + 1, visited, next)) {
							return false;
						}
					}
				}
				frontier = next;
			}
			return true;
		}

		/** Takes one flow of the walk: its edge, and its far end as a node; {@code false} when a limit stops the walk. */
		private boolean reach(FlowRow row, boolean downstream, int depth, Set<Integer> visited, List<Integer> next) {
			int other = downstream ? row.targetId() : row.sourceId();
			String name = downstream ? row.targetName() : row.sourceName();
			UUID type = downstream ? row.targetMetatypeId() : row.sourceMetatypeId();
			boolean asNode = other != this.start.id();
			if (this.edges.size() >= MAX_EDGES && !this.edges.containsKey(row.id())) {
				this.truncated = true;
				return false;
			}
			if (asNode && !addNode(other, name, type, depth)) {
				return false;
			}
			this.edges.putIfAbsent(row.id(), new EdgeAcc(row.id(), "data-flow", row.metatypeId(), row.sourceId(),
					row.targetId()));
			if (visited.add(other)) {
				next.add(other);
			}
			return true;
		}

		/** Adds a node or lowers its depth; {@code false} when the node limit stops the walk. */
		private boolean addNode(int id, String name, UUID type, int depth) {
			Acc known = this.nodes.get(id);
			if (known != null) {
				known.depth = Math.min(known.depth, depth);
				return true;
			}
			if (this.nodes.size() >= MAX_NODES) {
				this.truncated = true;
				return false;
			}
			this.nodes.put(id, new Acc(id, name, type, depth));
			return true;
		}

		/** The readers and writers of the data associations at the start, at depth 1. */
		private void tasks() {
			if (this.direction.down() && this.parts.dataInput() != null) {
				for (LineageStore.Association found : Lineages.this.store.associations(this.start.id(), true,
						descendants(this.parts.dataInput(), this.bases))) {
					if (!task(found, "data-input", "reader", this.start.id(), found.taskId())) {
						return;
					}
				}
			}
			if (this.direction.up() && this.parts.dataOutput() != null) {
				for (LineageStore.Association found : Lineages.this.store.associations(this.start.id(), false,
						descendants(this.parts.dataOutput(), this.bases))) {
					if (!task(found, "data-output", "writer", found.taskId(), this.start.id())) {
						return;
					}
				}
			}
		}

		private boolean task(LineageStore.Association found, String kind, String role, int source, int target) {
			if (this.edges.size() >= MAX_EDGES && !this.edges.containsKey(found.id())) {
				this.truncated = true;
				return false;
			}
			if (!addNode(found.taskId(), found.taskName(), found.taskMetatypeId(), 1)) {
				return false;
			}
			this.nodes.get(found.taskId()).roles.add(role);
			this.edges.putIfAbsent(found.id(), new EdgeAcc(found.id(), kind, found.metatypeId(), source, target));
			return true;
		}

		// ── result ─────────────────────────────────────────────────────────────

		/** Origin without an incoming flow, sink without an outgoing, intermediate with both. */
		private void roles(List<EdgeAcc> flows) {
			Set<Integer> hasIn = new HashSet<>();
			Set<Integer> hasOut = new HashSet<>();
			for (EdgeAcc edge : flows) {
				hasOut.add(edge.sourceId());
				hasIn.add(edge.targetId());
			}
			for (Acc node : this.nodes.values()) {
				boolean in = hasIn.contains(node.id);
				boolean out = hasOut.contains(node.id);
				if (!in && !out) {
					continue;
				}
				Set<String> task = new LinkedHashSet<>(node.roles);
				node.roles.clear();
				if (!in) {
					node.roles.add("origin");
				}
				if (in && out) {
					node.roles.add("intermediate");
				}
				if (!out) {
					node.roles.add("sink");
				}
				node.roles.addAll(task);
			}
		}

		private boolean hasCycle(List<EdgeAcc> flows) {
			Map<Integer, List<Integer>> next = new HashMap<>();
			for (EdgeAcc edge : flows) {
				next.computeIfAbsent(edge.sourceId(), (id) -> new ArrayList<>()).add(edge.targetId());
			}
			Map<Integer, Integer> state = new HashMap<>();
			for (Integer root : next.keySet()) {
				if (state.containsKey(root)) {
					continue;
				}
				Deque<Object[]> stack = new ArrayDeque<>();
				stack.push(new Object[] { root, 0 });
				state.put(root, 1);
				while (!stack.isEmpty()) {
					Object[] top = stack.peek();
					int node = (Integer) top[0];
					int index = (Integer) top[1];
					List<Integer> successors = next.getOrDefault(node, List.of());
					if (index >= successors.size()) {
						state.put(node, 2);
						stack.pop();
						continue;
					}
					top[1] = index + 1;
					int successor = successors.get(index);
					Integer seen = state.get(successor);
					if (seen == null) {
						state.put(successor, 1);
						stack.push(new Object[] { successor, 0 });
					}
					else if (seen == 1) {
						return true;
					}
				}
			}
			return false;
		}

		/** The simple paths along the returned flows, in breadth order, sink first for upstream. */
		private List<List<Integer>> paths(List<EdgeAcc> flows) {
			Map<Integer, TreeSet<Integer>> next = new TreeMap<>();
			Set<Integer> hasIn = new HashSet<>();
			Set<Integer> hasOut = new HashSet<>();
			for (EdgeAcc edge : flows) {
				next.computeIfAbsent(edge.sourceId(), (id) -> new TreeSet<>()).add(edge.targetId());
				hasOut.add(edge.sourceId());
				hasIn.add(edge.targetId());
			}
			Set<Integer> all = new TreeSet<>(hasIn);
			all.addAll(hasOut);
			Set<Integer> starts = new TreeSet<>();
			Set<Integer> ends = new TreeSet<>();
			for (Integer node : all) {
				if (!hasIn.contains(node)) {
					starts.add(node);
				}
				if (!hasOut.contains(node)) {
					ends.add(node);
				}
			}
			if (this.dataObjectStart) {
				starts.addAll(this.originSeeds);
				ends.addAll(this.sinkSeeds);
			}
			else {
				if (this.direction.down()) {
					starts.add(this.start.id());
				}
				if (this.direction.up()) {
					ends.add(this.start.id());
				}
			}
			List<List<Integer>> found = new ArrayList<>();
			int[] steps = { 0 };
			for (Integer root : starts) {
				if (!all.contains(root)) {
					continue;
				}
				List<Integer> path = new ArrayList<>();
				path.add(root);
				search(path, new HashSet<>(Set.of(root)), next, ends, found, steps);
				if (found.size() >= MAX_PATHS || steps[0] > PATH_STEPS) {
					break;
				}
			}
			found.sort(Comparator.comparingInt(List::size));
			List<List<Integer>> limited = new ArrayList<>(found.subList(0, Math.min(found.size(), MAX_PATHS)));
			if (this.direction == Direction.UPSTREAM) {
				limited = limited.stream().map((path) -> {
					List<Integer> reversed = new ArrayList<>(path);
					java.util.Collections.reverse(reversed);
					return (List<Integer>) reversed;
				}).toList();
			}
			return limited;
		}

		private void search(List<Integer> path, Set<Integer> onPath, Map<Integer, TreeSet<Integer>> next,
				Set<Integer> ends, List<List<Integer>> found, int[] steps) {
			if (found.size() >= MAX_PATHS || steps[0]++ > PATH_STEPS) {
				return;
			}
			int last = path.get(path.size() - 1);
			if (path.size() >= 2 && ends.contains(last)) {
				found.add(List.copyOf(path));
			}
			for (Integer successor : next.getOrDefault(last, new TreeSet<>())) {
				if (onPath.add(successor)) {
					path.add(successor);
					search(path, onPath, next, ends, found, steps);
					path.remove(path.size() - 1);
					onPath.remove(successor);
				}
			}
		}

		/** The data objects each flow carries: the start for a data object start, at most 50 otherwise. */
		private void carriedBy(List<EdgeAcc> flows, Map<Integer, List<CarriedBy>> carried, Set<Integer> more) {
			if (this.dataObjectStart) {
				for (EdgeAcc edge : flows) {
					List<Integer> connections = this.carrying.getOrDefault(edge.id(), List.of());
					List<CarriedBy> list = new ArrayList<>();
					for (int i = 0; i < Math.min(connections.size(), MAX_CARRIED); i++) {
						list.add(new CarriedBy(connections.get(i), this.start.id(), this.start.name()));
					}
					carried.put(edge.id(), list);
					if (connections.size() > MAX_CARRIED) {
						more.add(edge.id());
					}
				}
				return;
			}
			List<Integer> ids = flows.stream().map(EdgeAcc::id).toList();
			for (Carried found : Lineages.this.store.carried(ids, this.carriesTypes, MAX_CARRIED + 1)) {
				List<CarriedBy> list = carried.computeIfAbsent(found.flowId(), (id) -> new ArrayList<>());
				if (list.size() < MAX_CARRIED) {
					list.add(new CarriedBy(found.carriesId(), found.dataObjectId(), found.dataObjectName()));
				}
				else {
					more.add(found.flowId());
				}
			}
		}

		/** The values of "Transformation type" a caller may read, and the data outputs that are restricted for the caller. */
		private Transformations transformations(OeaPrincipal principal) {
			Map<Integer, String> values = new HashMap<>();
			Set<Integer> restricted = new HashSet<>();
			List<EdgeAcc> outputs = this.edges.values().stream().filter((edge) -> "data-output".equals(edge.kind())).toList();
			if (outputs.isEmpty()) {
				return new Transformations(values, restricted);
			}
			Map<UUID, Map<UUID, String>> mapped = Lineages.this.mappings.active(LocalDate.now(Lineages.this.clock));
			List<Integer> candidates = outputs.stream()
				.filter((edge) -> chain(edge.metatypeId(), this.bases)
					.stream()
					.anyMatch((type) -> mapped.getOrDefault(type, Map.of()).containsKey(TRANSFORMATION_TYPE)))
				.map(EdgeAcc::id)
				.toList();
			if (candidates.isEmpty()) {
				return new Transformations(values, restricted);
			}
			PropertyAccess.Snapshot snapshot = Lineages.this.access.of(principal);
			Map<Integer, String> stored = Lineages.this.store.enumValues(candidates, TRANSFORMATION_TYPE);
			List<Integer> overridden = new ArrayList<>();
			for (Integer id : candidates) {
				if (!snapshot.readableFor(TRANSFORMATION_TYPE, id)) {
					restricted.add(id);
				}
				else if (stored.containsKey(id)) {
					values.put(id, stored.get(id));
					if (snapshot.throughOverrideFor(TRANSFORMATION_TYPE, id)) {
						overridden.add(id);
					}
				}
			}
			if (!overridden.isEmpty()) {
				Lineages.this.overrideAudit.append(principal, "lineage", 0, "entity", String.valueOf(this.start.id()),
						Map.of(TRANSFORMATION_TYPE, "Transformation type"), Map.of(TRANSFORMATION_TYPE, overridden));
			}
			return new Transformations(values, restricted);
		}

	}

	private record Transformations(Map<Integer, String> values, Set<Integer> restricted) {
	}

	private static void union(Map<Integer, Integer> parent, int a, int b) {
		int rootA = find(parent, a);
		int rootB = find(parent, b);
		if (rootA != rootB) {
			parent.put(Math.max(rootA, rootB), Math.min(rootA, rootB));
		}
	}

	private static int find(Map<Integer, Integer> parent, int node) {
		int root = node;
		while (parent.containsKey(root) && parent.get(root) != root) {
			root = parent.get(root);
		}
		return root;
	}

}
