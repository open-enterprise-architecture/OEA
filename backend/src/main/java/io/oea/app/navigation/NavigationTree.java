package io.oea.app.navigation;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.catalog.CatalogStore;
import io.oea.core.navigation.NavigationTreeStore;
import io.oea.core.navigation.NavigationTreeStore.StoredNode;
import io.oea.core.navigation.NavigationTreeStore.StoredVisibleItem;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The navigation tree (US-054, US-095 to US-097, REQ-050, REQ-094 to REQ-097; design D3 of
 * us-054-catalog-navigation-tree and of us-095-navigation-tree-items). Every reader with
 * {@code entity:read} reads the tree in its manual order; an entry on another person's personal
 * catalog is left out, an entry of a deleted entity is marked. Folders and entries are changed
 * only with {@code viewpoint:manage} and {@code entity:read}; every write runs under the lock of
 * the tree, answers with the tree as the caller sees it and is audited.
 */
@Service
public class NavigationTree {

	/** Permission for all changes of the tree. */
	public static final String MANAGE_PERMISSION = "viewpoint:manage";

	/**
	 * Permission every change needs in addition, because it answers with the tree and the names
	 * of its targets (design D7 of us-095-navigation-tree-items).
	 */
	public static final String READ_PERMISSION = "entity:read";

	static final int NAME_MAX_LENGTH = 255;

	static final int LABEL_MAX_LENGTH = 255;

	/** Levels below the root. */
	static final int MAX_DEPTH = 10;

	static final int MAX_FOLDERS = 500;

	static final int MAX_ITEMS_PER_FOLDER = 200;

	private static final String TARGET = "tree-node";

	/** Folders in their manual order, then by name regardless of case. */
	private static final Comparator<StoredNode> NODE_ORDER = Comparator.comparingInt(StoredNode::sortOrder)
		.thenComparing((StoredNode node) -> node.name().toLowerCase(Locale.ROOT))
		.thenComparing(StoredNode::id);

	/** Entries in their manual order, then by the shown label regardless of case. */
	private static final Comparator<Entry> ENTRY_ORDER = Comparator.comparingInt(Entry::sortOrder)
		.thenComparing((Entry entry) -> entry.shownLabel().toLowerCase(Locale.ROOT))
		.thenComparing(Entry::id);

	private final NavigationTreeStore store;

	private final CatalogStore catalogs;

	private final PermissionResolver permissions;

	private final AuditWriter audit;

	private final Clock clock;

	NavigationTree(NavigationTreeStore store, CatalogStore catalogs, PermissionResolver permissions, AuditWriter audit,
			Clock clock) {
		this.store = store;
		this.catalogs = catalogs;
		this.permissions = permissions;
		this.audit = audit;
		this.clock = clock;
	}

	/** A folder with its entries and subfolders; {@code parentId} is {@code null} for the root. */
	public record Folder(UUID id, String name, UUID parentId, int sortOrder, List<Entry> items, List<Folder> children) {
	}

	/**
	 * An entry of a catalog, an element entity or a solution, with the current {@code name} of its
	 * target. {@code referenceId} is set for a catalog or solution, {@code entityId} for an entity;
	 * {@code scope} ({@code instance} or {@code personal}) only for a catalog; {@code deleted} is
	 * true for an entry of a deleted entity. {@code displayLabel} may be {@code null}.
	 */
	public record Entry(UUID id, String itemType, UUID referenceId, Integer entityId, String displayLabel,
			int sortOrder, String name, String scope, boolean deleted) {

		/** The text the tree shows. */
		public String shownLabel() {
			return (this.displayLabel != null) ? this.displayLabel : this.name;
		}

	}

	/**
	 * A target to place: {@code itemType} {@code catalog} or {@code solution} with
	 * {@code referenceId}, or {@code entity} with {@code entityId}; {@code displayLabel} may be
	 * {@code null}.
	 */
	public record Placement(String itemType, UUID referenceId, Integer entityId, String displayLabel) {
	}

	/** The tree from the root as the person sees it, in the manual order (REQ-096). */
	@Transactional(readOnly = true)
	public Folder read(OeaPrincipal principal) {
		return tree(person(principal));
	}

	/** Creates a folder under an existing folder (REQ-094 AC1, AC2; BR-01). */
	@Transactional
	public Folder create(OeaPrincipal principal, UUID parentId, String rawName) {
		long personId = manager(principal);
		this.store.lock();
		String name = name(rawName);
		List<StoredNode> nodes = this.store.nodes();
		Map<UUID, StoredNode> byId = byId(nodes);
		if (parentId == null || !byId.containsKey(parentId)) {
			throw InvalidTreeException.invalid("parentId", "The parent folder does not exist.");
		}
		if (depth(byId, parentId) + 1 > MAX_DEPTH) {
			throw InvalidTreeException.invalid("parentId",
					"A folder can be at most " + MAX_DEPTH + " levels below the root.");
		}
		if (nodes.size() >= MAX_FOLDERS) {
			throw InvalidTreeException.invalid("parentId", "The tree can have at most " + MAX_FOLDERS + " folders.");
		}
		UUID id;
		try {
			id = this.store.createNode(name, parentId, personId, this.clock.instant());
		}
		catch (DuplicateKeyException ex) {
			throw nameTakenOr(ex);
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", name);
		details.put("parentId", parentId.toString());
		audit(principal, "tree-node.created", id, details);
		return tree(personId);
	}

	/** Renames a folder, the root included, under the rules of a new folder's name. */
	@Transactional
	public Folder rename(OeaPrincipal principal, UUID id, String rawName) {
		long personId = manager(principal);
		this.store.lock();
		StoredNode node = existing(id);
		String name = name(rawName);
		if (!name.equals(node.name())) {
			try {
				this.store.renameNode(id, name);
			}
			catch (DuplicateKeyException ex) {
				throw nameTakenOr(ex);
			}
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("name", name);
			details.put("previousName", node.name());
			audit(principal, "tree-node.renamed", id, details);
		}
		return tree(personId);
	}

	/**
	 * Deletes a folder with its subfolders and all their entries; no catalog changes (REQ-094
	 * AC3, AC4; BR-02, BR-05).
	 */
	@Transactional
	public Folder delete(OeaPrincipal principal, UUID id) {
		long personId = manager(principal);
		this.store.lock();
		StoredNode node = existing(id);
		if (node.parentId() == null) {
			throw InvalidTreeException.rootProtected();
		}
		NavigationTreeStore.SubtreeSize size = this.store.subtreeSize(id);
		this.store.deleteNode(id);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", node.name());
		details.put("folders", size.folders());
		details.put("items", size.items());
		audit(principal, "tree-node.deleted", id, details);
		return tree(personId);
	}

	/**
	 * Places a catalog, an element entity or a solution in the folder as its last entry (US-054,
	 * US-095, US-097; REQ-050, REQ-095, REQ-097; BR-04, BR-06). The target does not change. Another
	 * person's personal catalog is answered like an unknown one.
	 */
	@Transactional
	public Folder addItem(OeaPrincipal principal, UUID nodeId, Placement placement) {
		long personId = manager(principal);
		this.store.lock();
		existing(nodeId);
		String itemType = placement.itemType();
		String field = NavigationTreeStore.ENTITY.equals(itemType) ? "entityId" : "referenceId";
		String otherField = NavigationTreeStore.ENTITY.equals(itemType) ? "referenceId" : "entityId";
		boolean fittingMissing = NavigationTreeStore.ENTITY.equals(itemType) ? placement.entityId() == null
				: placement.referenceId() == null;
		boolean otherSet = NavigationTreeStore.ENTITY.equals(itemType) ? placement.referenceId() != null
				: placement.entityId() != null;
		if (!List.of(NavigationTreeStore.CATALOG, NavigationTreeStore.ENTITY, NavigationTreeStore.SOLUTION)
			.contains(itemType)) {
			throw InvalidTreeException.invalid("itemType", "The item type is not supported.");
		}
		if (fittingMissing) {
			throw InvalidTreeException.invalid(field, "The " + field + " is required for the item type.");
		}
		if (otherSet) {
			throw InvalidTreeException.invalid(otherField, "The " + otherField + " does not fit the item type.");
		}
		String label = label(placement.displayLabel());
		switch (itemType) {
			case NavigationTreeStore.CATALOG -> {
				if (this.catalogs.findVisible(placement.referenceId(), personId).isEmpty()) {
					throw InvalidTreeException.invalid(field, "The catalog does not exist.");
				}
			}
			case NavigationTreeStore.SOLUTION -> {
				if (!this.store.existsSolution(placement.referenceId())) {
					throw InvalidTreeException.invalid(field, "The solution does not exist.");
				}
			}
			default -> {
				if (!this.store.existsElementEntity(placement.entityId())) {
					throw InvalidTreeException.invalid(field,
							"The entity does not exist, is deleted or is a connection.");
				}
			}
		}
		if (this.store.itemCount(nodeId) >= MAX_ITEMS_PER_FOLDER) {
			throw InvalidTreeException.invalid(field,
					"A folder can have at most " + MAX_ITEMS_PER_FOLDER + " entries.");
		}
		UUID itemId;
		try {
			itemId = this.store.addItem(nodeId, itemType, placement.referenceId(), placement.entityId(), label,
					personId, this.clock.instant());
		}
		catch (DuplicateKeyException ex) {
			if (isItemIndex(ex)) {
				throw InvalidTreeException.invalid(field, "The target is already in this folder.");
			}
			throw ex;
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("itemId", itemId.toString());
		putTarget(details, itemType, placement.referenceId(), placement.entityId());
		audit(principal, "tree-node.item-added", nodeId, details);
		return tree(personId);
	}

	/**
	 * Removes only this entry; the target and its other entries stay (US-054 AC4, US-097 AC2,
	 * REQ-095). An entry the person cannot see is answered like an unknown one.
	 */
	@Transactional
	public Folder removeItem(OeaPrincipal principal, UUID nodeId, UUID itemId) {
		long personId = manager(principal);
		this.store.lock();
		existing(nodeId);
		StoredVisibleItem item = visibleItem(nodeId, itemId, personId);
		this.store.removeItem(nodeId, itemId);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("itemId", itemId.toString());
		putTarget(details, item.itemType(), item.referenceId(), item.entityId());
		audit(principal, "tree-node.item-removed", nodeId, details);
		return tree(personId);
	}

	/**
	 * Moves a folder with its subfolders and entries under a new parent at a position among the
	 * new siblings (US-096 AC1, REQ-096; BR-03). The root does not move; the new parent must be
	 * neither the folder nor one of its descendants; the moved subtree stays within
	 * {@link #MAX_DEPTH}. A move to the same place changes and audits nothing.
	 */
	@Transactional
	public Folder moveNode(OeaPrincipal principal, UUID id, UUID parentId, int position) {
		long personId = manager(principal);
		this.store.lock();
		List<StoredNode> nodes = this.store.nodes();
		Map<UUID, StoredNode> byId = byId(nodes);
		StoredNode node = byId.get(id);
		if (node == null) {
			throw new NoSuchElementException("tree node " + id);
		}
		if (node.parentId() == null) {
			throw InvalidTreeException.rootNotMovable();
		}
		if (parentId == null || !byId.containsKey(parentId)) {
			throw InvalidTreeException.invalid("parentId", "The parent folder does not exist.");
		}
		List<NavigationTreeStore.StoredDescendant> subtree = this.store.descendants(id, MAX_DEPTH + 1);
		if (subtree.stream().anyMatch((descendant) -> descendant.id().equals(parentId))) {
			throw InvalidTreeException.invalid("parentId",
					"A folder cannot be moved under itself or one of its subfolders.");
		}
		int height = subtree.stream().mapToInt(NavigationTreeStore.StoredDescendant::level).max().orElse(0);
		if (depth(byId, parentId) + 1 + height > MAX_DEPTH) {
			throw InvalidTreeException.invalid("parentId",
					"A folder can be at most " + MAX_DEPTH + " levels below the root.");
		}
		List<StoredNode> others = nodes.stream()
			.filter((sibling) -> parentId.equals(sibling.parentId()) && !sibling.id().equals(id))
			.sorted(NODE_ORDER)
			.toList();
		int target = Math.min(position, others.size());
		if (parentId.equals(node.parentId())) {
			List<StoredNode> siblings = nodes.stream()
				.filter((sibling) -> parentId.equals(sibling.parentId()))
				.sorted(NODE_ORDER)
				.toList();
			if (siblings.indexOf(node) == target) {
				return tree(personId);
			}
		}
		try {
			this.store.moveNode(id, node.parentId(), parentId, target);
		}
		catch (DuplicateKeyException ex) {
			if (ex.getMessage() != null && ex.getMessage().contains(NavigationTreeStore.NAME_INDEX)) {
				throw InvalidTreeException.invalid("parentId", "A folder with this name already exists there.");
			}
			throw ex;
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("fromNodeId", node.parentId().toString());
		details.put("toNodeId", parentId.toString());
		details.put("position", target);
		audit(principal, "tree-node.moved", id, details);
		return tree(personId);
	}

	/**
	 * Moves an entry within its folder or to another folder at a position among the entries the
	 * person sees (US-096 AC2, AC3, REQ-096): it goes before the visible entry at that position,
	 * or after the last visible one; hidden entries keep their order relative to the others. A
	 * move to the same place changes and audits nothing.
	 */
	@Transactional
	public Folder moveItem(OeaPrincipal principal, UUID nodeId, UUID itemId, UUID targetNodeId, int position) {
		long personId = manager(principal);
		this.store.lock();
		existing(nodeId);
		StoredVisibleItem item = visibleItem(nodeId, itemId, personId);
		if (targetNodeId == null || this.store.nodes().stream().noneMatch((node) -> node.id().equals(targetNodeId))) {
			throw InvalidTreeException.invalid("nodeId", "The target folder does not exist.");
		}
		boolean otherFolder = !targetNodeId.equals(nodeId);
		if (otherFolder && this.store.itemCount(targetNodeId) >= MAX_ITEMS_PER_FOLDER) {
			throw InvalidTreeException.invalid("nodeId",
					"A folder can have at most " + MAX_ITEMS_PER_FOLDER + " entries.");
		}
		List<UUID> all = this.store.itemOrder(targetNodeId);
		List<UUID> others = all.stream().filter((other) -> !other.equals(itemId)).toList();
		Set<UUID> visible = new java.util.HashSet<>();
		this.store.items(personId)
			.stream()
			.filter((entry) -> entry.nodeId().equals(targetNodeId))
			.forEach((entry) -> visible.add(entry.id()));
		List<Integer> visibleIndexes = new ArrayList<>();
		for (int i = 0; i < others.size(); i++) {
			if (visible.contains(others.get(i))) {
				visibleIndexes.add(i);
			}
		}
		int shown = Math.min(position, visibleIndexes.size());
		int target;
		if (shown < visibleIndexes.size()) {
			target = visibleIndexes.get(shown);
		}
		else {
			target = visibleIndexes.isEmpty() ? 0 : visibleIndexes.get(visibleIndexes.size() - 1) + 1;
		}
		if (!otherFolder && all.indexOf(itemId) == target) {
			return tree(personId);
		}
		try {
			this.store.moveItem(itemId, nodeId, targetNodeId, target);
		}
		catch (DuplicateKeyException ex) {
			if (isItemIndex(ex)) {
				throw InvalidTreeException.invalid("nodeId", "The target is already in this folder.");
			}
			throw ex;
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("itemId", itemId.toString());
		details.put("itemType", item.itemType());
		details.put("fromNodeId", nodeId.toString());
		details.put("toNodeId", targetNodeId.toString());
		details.put("position", shown);
		audit(principal, "tree-node.item-moved", nodeId, details);
		return tree(personId);
	}

	private StoredVisibleItem visibleItem(UUID nodeId, UUID itemId, long personId) {
		return this.store.findVisibleItem(nodeId, itemId, personId)
			.orElseThrow(() -> new NoSuchElementException("tree node item " + itemId));
	}

	private static boolean isItemIndex(DuplicateKeyException ex) {
		String message = ex.getMessage();
		return message != null && (message.contains(NavigationTreeStore.ITEM_INDEX)
				|| message.contains(NavigationTreeStore.ENTITY_ITEM_INDEX));
	}

	/** The item type and the target ID, without display label or name (design D7). */
	private static void putTarget(Map<String, Object> details, String itemType, UUID referenceId, Integer entityId) {
		details.put("itemType", itemType);
		if (entityId != null) {
			details.put("entityId", entityId);
		}
		else {
			details.put("referenceId", referenceId.toString());
		}
	}

	private Folder tree(long personId) {
		List<StoredNode> nodes = this.store.nodes();
		Map<UUID, List<StoredNode>> children = new HashMap<>();
		StoredNode root = null;
		for (StoredNode node : nodes) {
			if (node.parentId() == null) {
				root = node;
			}
			else {
				children.computeIfAbsent(node.parentId(), (key) -> new ArrayList<>()).add(node);
			}
		}
		if (root == null) {
			throw new IllegalStateException("the navigation tree has no root");
		}
		Map<UUID, List<Entry>> items = new HashMap<>();
		for (StoredVisibleItem item : this.store.items(personId)) {
			items.computeIfAbsent(item.nodeId(), (key) -> new ArrayList<>())
				.add(new Entry(item.id(), item.itemType(), item.referenceId(), item.entityId(), item.displayLabel(),
						item.sortOrder(), item.name(), item.scope(), item.deleted()));
		}
		return folder(root, children, items);
	}

	private static Folder folder(StoredNode node, Map<UUID, List<StoredNode>> children,
			Map<UUID, List<Entry>> items) {
		List<Entry> entries = new ArrayList<>(items.getOrDefault(node.id(), List.of()));
		entries.sort(ENTRY_ORDER);
		List<Folder> subfolders = children.getOrDefault(node.id(), List.of())
			.stream()
			.sorted(NODE_ORDER)
			.map((child) -> folder(child, children, items))
			.toList();
		return new Folder(node.id(), node.name(), node.parentId(), node.sortOrder(), List.copyOf(entries),
				subfolders);
	}

	private StoredNode existing(UUID id) {
		return this.store.nodes()
			.stream()
			.filter((node) -> node.id().equals(id))
			.findFirst()
			.orElseThrow(() -> new NoSuchElementException("tree node " + id));
	}

	private static Map<UUID, StoredNode> byId(List<StoredNode> nodes) {
		Map<UUID, StoredNode> byId = new HashMap<>();
		nodes.forEach((node) -> byId.put(node.id(), node));
		return byId;
	}

	/** Levels of the folder below the root; the root has 0. */
	private static int depth(Map<UUID, StoredNode> byId, UUID id) {
		int depth = 0;
		StoredNode node = byId.get(id);
		while (node != null && node.parentId() != null && depth <= MAX_DEPTH) {
			depth++;
			node = byId.get(node.parentId());
		}
		return depth;
	}

	private static String name(String raw) {
		String name = (raw != null) ? raw.strip() : "";
		if (name.isEmpty() || name.length() > NAME_MAX_LENGTH || hasControlCharacter(name)) {
			throw InvalidTreeException.invalid("name",
					"The name must have 1 to " + NAME_MAX_LENGTH + " characters without control characters.");
		}
		return name;
	}

	private static String label(String raw) {
		String label = (raw != null) ? raw.strip() : "";
		if (label.isEmpty()) {
			return null;
		}
		if (label.length() > LABEL_MAX_LENGTH || hasControlCharacter(label)) {
			throw InvalidTreeException.invalid("displayLabel",
					"The display label must have at most " + LABEL_MAX_LENGTH + " characters without control "
							+ "characters.");
		}
		return label;
	}

	private static boolean hasControlCharacter(String text) {
		return text.chars().anyMatch(Character::isISOControl);
	}

	private static RuntimeException nameTakenOr(DuplicateKeyException ex) {
		if (ex.getMessage() != null && ex.getMessage().contains(NavigationTreeStore.NAME_INDEX)) {
			return InvalidTreeException.invalid("name", "A folder with this name already exists.");
		}
		return ex;
	}

	private void audit(OeaPrincipal principal, String eventType, UUID nodeId, Map<String, Object> details) {
		this.audit.append(new AuditEvent(eventType, AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), TARGET, nodeId.toString(),
				details));
	}

	/**
	 * The person who may change the tree, holding {@code viewpoint:manage} and {@code entity:read};
	 * any other caller is answered with 403.
	 */
	private long manager(OeaPrincipal principal) {
		long personId = person(principal);
		Set<String> granted = this.permissions.permissions(principal);
		if (!granted.contains(MANAGE_PERMISSION) || !granted.contains(READ_PERMISSION)) {
			throw new PermissionDeniedException();
		}
		return personId;
	}

	private static long person(OeaPrincipal principal) {
		if (principal.type() != OeaPrincipal.Type.PERSON || principal.personId() == null) {
			throw new PermissionDeniedException();
		}
		return principal.personId();
	}

}
