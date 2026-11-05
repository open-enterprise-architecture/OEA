package io.oea.app.catalog;

import io.oea.app.metamodel.PackageImporter;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.catalog.CatalogQueryStore;
import io.oea.core.catalog.CatalogStore;
import io.oea.core.catalog.CatalogStore.StoredCatalog;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Creating and reading catalogs (US-046, REQ-043; design D3–D6 of us-046-create-catalog). A
 * catalog with scope {@code instance} needs {@code catalog:manage}; a personal catalog is visible
 * only to its creator and answered as not existing for everybody else.
 */
@Service
public class Catalogs {

	/** Permission for shared catalogs, the contract condition of {@code createCatalog}. */
	public static final String MANAGE_PERMISSION = "catalog:manage";

	static final int NAME_MAX_LENGTH = 255;

	static final int DESCRIPTION_MAX_LENGTH = 1000;

	static final int LABEL_MAX_LENGTH = 100;

	static final int MAX_COLUMNS = 100;

	private final CatalogStore store;

	private final CatalogQueryStore queries;

	private final PermissionResolver permissions;

	private final AuditWriter audit;

	private final Clock clock;

	public Catalogs(CatalogStore store, CatalogQueryStore queries, PermissionResolver permissions, AuditWriter audit,
			Clock clock) {
		this.store = store;
		this.queries = queries;
		this.permissions = permissions;
		this.audit = audit;
		this.clock = clock;
	}

	/** Visibility of a catalog. */
	public enum Scope {

		INSTANCE, PERSONAL;

		String dbValue() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	/** Join mode of a catalog. */
	public enum JoinMode {

		AGGREGATE, ROWS;

		String dbValue() {
			return name().toLowerCase(Locale.ROOT);
		}

	}

	/** A catalog with the current name of its primary metatype, its creator and its columns. */
	public record Catalog(UUID id, String name, String description, UUID primaryMetatypeId, String primaryMetatypeName,
			String defaultJoinMode, String scope, long createdById, String createdByName, Instant createdAt,
			Instant lastModifiedAt, List<Column> columns) {
	}

	/**
	 * A configured column (US-047, design D1 of us-047-configure-columns): exactly one of
	 * {@code field} and {@code propertyDefId}; {@code defaultSortDirection} is {@code asc},
	 * {@code desc} or {@code null}; {@code available} is false for a property the catalog can no
	 * longer show.
	 */
	public record Column(SystemField field, UUID propertyDefId, String displayLabel, boolean visible, boolean sortable,
			String defaultSortDirection, boolean available) {
	}

	/** The system fields of a column with their keys in the contract and the query. */
	public enum SystemField {

		NAME("name", "Name"), DESCRIPTION("description", "Description"), EXTERNAL_ID("externalId", "External ID"),
		CREATED_AT("createdAt", "Created"), UPDATED_AT("updatedAt", "Updated"),
		ENTITY_VERSION("entityVersion", "Version");

		private final String key;

		private final String label;

		SystemField(String key, String label) {
			this.key = key;
			this.label = label;
		}

		public String key() {
			return this.key;
		}

		public String label() {
			return this.label;
		}

		public static SystemField ofKey(String key) {
			for (SystemField field : values()) {
				if (field.key.equals(key)) {
					return field;
				}
			}
			return null;
		}

	}

	/** Input of a new catalog; a {@code null} join mode means {@code aggregate}. */
	public record NewCatalog(String name, String description, UUID primaryMetatypeId, Scope scope,
			JoinMode defaultJoinMode) {
	}

	/**
	 * Creates the catalog and audits it. Order: scope permission, fields, primary metatype, name
	 * (by the unique index, so concurrent creations cannot both succeed).
	 */
	@Transactional
	public Catalog create(OeaPrincipal principal, NewCatalog input) {
		long personId = person(principal);
		if (input.scope() == Scope.INSTANCE && !this.permissions.permissions(principal).contains(MANAGE_PERMISSION)) {
			throw new PermissionDeniedException();
		}
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
			throw InvalidCatalogException.invalid("name", "The name must have 1 to 255 characters.");
		}
		String description = (input.description() != null) ? input.description() : "";
		if (description.length() > DESCRIPTION_MAX_LENGTH) {
			throw InvalidCatalogException.invalid("description", "The description must have at most 1000 characters.");
		}
		if (input.primaryMetatypeId() == null
				|| !this.store.metatypeExists(input.primaryMetatypeId(), PackageImporter.INSTANCE_CONFIGURATION)) {
			throw InvalidCatalogException.invalid("primaryMetatypeId",
					"The primary entity type is not defined in the metamodel.");
		}
		JoinMode joinMode = (input.defaultJoinMode() != null) ? input.defaultJoinMode() : JoinMode.AGGREGATE;
		UUID id;
		try {
			id = this.store.create(new CatalogStore.NewCatalog(name, description, input.primaryMetatypeId(),
					joinMode.dbValue(), input.scope().dbValue()), personId, this.clock.instant());
		}
		catch (DuplicateKeyException ex) {
			if (ex.getMessage() != null && ex.getMessage().contains(CatalogStore.NAME_INDEX)) {
				throw InvalidCatalogException.nameTaken();
			}
			throw ex;
		}
		this.audit.append(new AuditEvent("catalog.created", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "catalog", id.toString(),
				Map.of("name", name, "primaryMetatypeId", input.primaryMetatypeId().toString(), "scope",
						input.scope().dbValue())));
		return catalog(this.store.findVisible(id, personId).orElseThrow());
	}

	/** Instance catalogs and the caller's personal catalogs, ordered by name. */
	@Transactional(readOnly = true)
	public List<Catalog> list(OeaPrincipal principal) {
		return this.store.visibleTo(person(principal)).stream().map(this::catalog).toList();
	}

	/** The catalog; not existing and another person's personal catalog are answered alike. */
	@Transactional(readOnly = true)
	public Catalog get(OeaPrincipal principal, UUID id) {
		return this.store.findVisible(id, person(principal))
			.map(this::catalog)
			.orElseThrow(() -> new NoSuchElementException("catalog " + id));
	}

	/**
	 * Replaces the columns of the catalog (design D2, D3 of us-047-configure-columns). Without
	 * {@code catalog:manage}, only one's own personal catalog can be changed and every other ID is
	 * answered with 403, so that other personal catalogs stay hidden; with it, an unknown ID and
	 * another person's personal catalog are answered with 404.
	 */
	@Transactional
	public Catalog updateColumns(OeaPrincipal principal, UUID id, List<Column> columns) {
		long personId = person(principal);
		boolean manager = this.permissions.permissions(principal).contains(MANAGE_PERMISSION);
		StoredCatalog stored = this.store.find(id).orElse(null);
		boolean own = stored != null && "personal".equals(stored.scope()) && stored.createdById() == personId;
		boolean visible = stored != null && ("instance".equals(stored.scope()) || own);
		if (!manager && !own) {
			throw new PermissionDeniedException();
		}
		if (!visible) {
			throw new NoSuchElementException("catalog " + id);
		}
		List<Column> checked = validate(stored.primaryMetatypeId(), (columns != null) ? columns : List.of());
		ArrayNode json = JsonMapper.shared().createArrayNode();
		for (Column column : checked) {
			ObjectNode node = json.addObject();
			if (column.field() != null) {
				node.put("field", column.field().key());
			}
			else {
				node.put("propertyDefId", column.propertyDefId().toString());
			}
			node.put("displayLabel", column.displayLabel());
			node.put("visible", column.visible());
			node.put("sortable", column.sortable());
			node.put("defaultSortDirection", column.defaultSortDirection());
		}
		this.store.updateColumns(id, json.toString(), personId, this.clock.instant());
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("columns", checked.size());
		details.put("visibleColumns", checked.stream().filter(Column::visible).count());
		this.audit.append(new AuditEvent("catalog.columns-changed", AuditEvent.Category.CHANGE,
				AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"catalog", id.toString(), details));
		return catalog(this.store.find(id).orElseThrow());
	}

	/** Checks the column list in the order of design D2; returns it with trimmed labels. */
	private List<Column> validate(UUID metatypeId, List<Column> columns) {
		if (columns.size() > MAX_COLUMNS) {
			throw InvalidCatalogException.invalid("columns", "A catalog has at most 100 columns.");
		}
		Set<UUID> available = this.queries.availableProperties(metatypeId, LocalDate.now(this.clock)).keySet();
		Set<String> seen = new HashSet<>();
		List<Column> checked = new ArrayList<>();
		int defaults = 0;
		for (int i = 0; i < columns.size(); i++) {
			Column column = columns.get(i);
			String at = "columns[" + i + "]";
			if ((column.field() == null) == (column.propertyDefId() == null)) {
				throw InvalidCatalogException.invalid(at, "A column names either a field or a property.");
			}
			if (column.propertyDefId() != null && !available.contains(column.propertyDefId())) {
				throw InvalidCatalogException.invalid(at + ".propertyDefId",
						"The property is not a public property of the primary entity type.");
			}
			String attribute = (column.field() != null) ? column.field().key() : column.propertyDefId().toString();
			if (!seen.add(attribute)) {
				throw InvalidCatalogException.invalid(at, "The attribute is listed twice.");
			}
			String label = (column.displayLabel() != null && !column.displayLabel().isBlank())
					? column.displayLabel().strip() : null;
			if (label != null && label.length() > LABEL_MAX_LENGTH) {
				throw InvalidCatalogException.invalid(at + ".displayLabel", "A label has at most 100 characters.");
			}
			String direction = column.defaultSortDirection();
			if (direction != null && !"asc".equals(direction) && !"desc".equals(direction)) {
				throw InvalidCatalogException.invalid(at + ".defaultSortDirection", "The direction is asc or desc.");
			}
			if (direction != null) {
				defaults++;
			}
			checked.add(new Column(column.field(), column.propertyDefId(), label, column.visible(), column.sortable(),
					direction, true));
		}
		if (!checked.isEmpty() && checked.stream().noneMatch(Column::visible)) {
			throw InvalidCatalogException.invalid("columns", "At least one column must be visible.");
		}
		if (defaults > 1) {
			throw InvalidCatalogException.invalid("columns", "At most one column has a default sort.");
		}
		return checked;
	}

	private Catalog catalog(StoredCatalog stored) {
		List<Column> columns = new ArrayList<>();
		JsonNode json = (stored.columnsJson() != null) ? JsonMapper.shared().readTree(stored.columnsJson()) : null;
		Set<UUID> available = null;
		if (json != null && json.isArray()) {
			for (JsonNode node : json) {
				UUID property = node.hasNonNull("propertyDefId") ? UUID.fromString(node.get("propertyDefId").asString())
						: null;
				if (property != null && available == null) {
					available = this.queries.availableProperties(stored.primaryMetatypeId(), LocalDate.now(this.clock))
						.keySet();
				}
				columns.add(new Column(SystemField.ofKey(node.path("field").asString(null)), property,
						node.hasNonNull("displayLabel") ? node.get("displayLabel").asString() : null,
						node.path("visible").asBoolean(true), node.path("sortable").asBoolean(true),
						node.hasNonNull("defaultSortDirection") ? node.get("defaultSortDirection").asString() : null,
						property == null || available.contains(property)));
			}
		}
		return new Catalog(stored.id(), stored.name(), stored.description(), stored.primaryMetatypeId(),
				stored.primaryMetatypeName(), stored.defaultJoinMode(), stored.scope(), stored.createdById(),
				stored.createdByName(), stored.createdAt(), stored.lastModifiedAt(), List.copyOf(columns));
	}

	/** Catalogs belong to persons; other principals hold no permission for them (ADR-032). */
	private static long person(OeaPrincipal principal) {
		if (principal.type() != OeaPrincipal.Type.PERSON || principal.personId() == null) {
			throw new PermissionDeniedException();
		}
		return principal.personId();
	}

}
