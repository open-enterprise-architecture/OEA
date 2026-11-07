package io.oea.app.navigation;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.catalog.CatalogStore;
import io.oea.core.navigation.NavigationTreeStore;
import io.oea.core.navigation.NavigationTreeStore.StoredCatalogItem;
import io.oea.core.navigation.NavigationTreeStore.StoredNode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The navigation tree (US-054, REQ-050, REQ-094, REQ-095, REQ-097; design D3 of
 * us-054-catalog-navigation-tree). Every reader with {@code entity:read} reads the tree; an entry
 * on another person's personal catalog is left out. Folders and entries are changed only with
 * {@code viewpoint:manage}; every write answers with the tree as the caller sees it and is
 * audited.
 */
@Service
public class NavigationTree {

	/** Permission for all changes of the tree. */
	public static final String MANAGE_PERMISSION = "viewpoint:manage";

	static final int NAME_MAX_LENGTH = 255;

	static final int LABEL_MAX_LENGTH = 255;

	/** Levels below the root. */
	static final int MAX_DEPTH = 10;

	static final int MAX_FOLDERS = 500;

	static final int MAX_ITEMS_PER_FOLDER = 200;

	private static final String TARGET = "tree-node";

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
	 * An entry: so far always a catalog, with its current {@code name} and {@code scope}
	 * ({@code instance} or {@code personal}); {@code displayLabel} may be {@code null}.
	 */
	public record Entry(UUID id, String itemType, UUID referenceId, String displayLabel, int sortOrder, String name,
			String scope) {

		/** The text the tree shows. */
		public String shownLabel() {
			return (this.displayLabel != null) ? this.displayLabel : this.name;
		}

	}

	/** The tree from the root as the person sees it, sorted by name regardless of case. */
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
	 * Places a catalog the person can see in the folder (US-054 AC1, AC3, AC5; REQ-050, REQ-097;
	 * BR-04, BR-06). Another person's personal catalog is answered like an unknown one.
	 */
	@Transactional
	public Folder addCatalog(OeaPrincipal principal, UUID nodeId, UUID catalogId, String rawLabel) {
		long personId = manager(principal);
		this.store.lock();
		existing(nodeId);
		String label = label(rawLabel);
		if (catalogId == null || this.catalogs.findVisible(catalogId, personId).isEmpty()) {
			throw InvalidTreeException.invalid("referenceId", "The catalog does not exist.");
		}
		if (this.store.itemCount(nodeId) >= MAX_ITEMS_PER_FOLDER) {
			throw InvalidTreeException.invalid("referenceId",
					"A folder can have at most " + MAX_ITEMS_PER_FOLDER + " entries.");
		}
		UUID itemId;
		try {
			itemId = this.store.addItem(nodeId, NavigationTreeStore.CATALOG, catalogId, label, personId,
					this.clock.instant());
		}
		catch (DuplicateKeyException ex) {
			if (ex.getMessage() != null && ex.getMessage().contains(NavigationTreeStore.ITEM_INDEX)) {
				throw InvalidTreeException.invalid("referenceId", "The catalog is already in this folder.");
			}
			throw ex;
		}
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("itemId", itemId.toString());
		details.put("itemType", NavigationTreeStore.CATALOG);
		details.put("referenceId", catalogId.toString());
		audit(principal, "tree-node.item-added", nodeId, details);
		return tree(personId);
	}

	/** Removes only this entry; the catalog and its other entries stay (US-054 AC4, REQ-095). */
	@Transactional
	public Folder removeItem(OeaPrincipal principal, UUID nodeId, UUID itemId) {
		long personId = manager(principal);
		this.store.lock();
		existing(nodeId);
		NavigationTreeStore.StoredItem item = this.store.findItem(nodeId, itemId)
			.orElseThrow(() -> new NoSuchElementException("tree node item " + itemId));
		this.store.removeItem(itemId);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("itemId", itemId.toString());
		details.put("itemType", item.itemType());
		details.put("referenceId", item.referenceId().toString());
		audit(principal, "tree-node.item-removed", nodeId, details);
		return tree(personId);
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
		for (StoredCatalogItem item : this.store.visibleCatalogItems(personId)) {
			items.computeIfAbsent(item.nodeId(), (key) -> new ArrayList<>())
				.add(new Entry(item.id(), NavigationTreeStore.CATALOG, item.referenceId(), item.displayLabel(),
						item.sortOrder(), item.catalogName(), item.catalogScope()));
		}
		return folder(root, children, items);
	}

	private static Folder folder(StoredNode node, Map<UUID, List<StoredNode>> children,
			Map<UUID, List<Entry>> items) {
		List<Entry> entries = new ArrayList<>(items.getOrDefault(node.id(), List.of()));
		entries.sort(Comparator.comparing((Entry entry) -> entry.shownLabel().toLowerCase(Locale.ROOT))
			.thenComparing(Entry::id));
		List<Folder> subfolders = children.getOrDefault(node.id(), List.of())
			.stream()
			.sorted(Comparator.comparing((StoredNode child) -> child.name().toLowerCase(Locale.ROOT))
				.thenComparing(StoredNode::id))
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

	/** The person who may change the tree; any other caller is answered with 403. */
	private long manager(OeaPrincipal principal) {
		long personId = person(principal);
		if (!this.permissions.permissions(principal).contains(MANAGE_PERMISSION)) {
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
