package io.oea.app.solution;

import io.oea.app.entity.EntityBlocks;
import io.oea.app.entity.EntityInput;
import io.oea.app.entity.EntityValidator;
import io.oea.app.entity.EntityWriter;
import io.oea.app.entity.MetatypeRules;
import io.oea.app.entity.PropertyWriteAccess;
import io.oea.app.metamodel.MetamodelLabels;
import io.oea.app.metamodel.PropertyAccess;
import io.oea.app.access.LanguagePreferences;
import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.PermissionResolver;
import io.oea.app.security.Sessions;
import io.oea.app.entity.Issue;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.EntityStore.Fields;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.core.entity.EntityVersionStore;
import io.oea.core.entity.MetatypeRuleStore;
import io.oea.core.entity.TypedValue;
import io.oea.core.solution.GoLiveStore;
import io.oea.core.solution.OpenIssueStore;
import io.oea.core.solution.OpenIssueStore.IssueRow;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The open issues of a solution (design D4 of us-153-open-issues; US-153, US-154 AC4): their
 * status, with the automatic resolution evaluated on every read, and their resolution with a
 * value or by removing the value. With {@code solution:write} the values are taken without a
 * check of write roles, as in the go-live; with only {@code entity:write} the write roles and the
 * scope of a catalog write apply (REQ-162). SQL runs only in the stores of core (ADR-051).
 */
@Service
public class OpenIssues {

	static final int MAX_RESOLUTIONS = 200;

	private static final String SOLUTION_WRITE = "solution:write";

	private static final String ENTITY_WRITE = "entity:write";

	private final OpenIssueStore store;

	private final GoLiveStore solutions;

	private final EntityBlocks blocks;

	private final EntityStore entities;

	private final EntityVersionStore versions;

	private final MetatypeRuleStore ruleStore;

	private final EntityWriter writer;

	private final PropertyAccess access;

	private final PropertyWriteAccess writeAccess;

	private final PermissionResolver permissions;

	private final MetamodelLabels labels;

	private final LanguagePreferences languages;

	private final AuditWriter audit;

	private final Clock clock;

	OpenIssues(OpenIssueStore store, GoLiveStore solutions, EntityBlocks blocks, EntityStore entities,
			EntityVersionStore versions, MetatypeRuleStore ruleStore, EntityWriter writer, PropertyAccess access,
			PropertyWriteAccess writeAccess, PermissionResolver permissions, MetamodelLabels labels,
			LanguagePreferences languages, AuditWriter audit, Clock clock) {
		this.store = store;
		this.solutions = solutions;
		this.blocks = blocks;
		this.entities = entities;
		this.versions = versions;
		this.ruleStore = ruleStore;
		this.writer = writer;
		this.access = access;
		this.writeAccess = writeAccess;
		this.permissions = permissions;
		this.labels = labels;
		this.languages = languages;
		this.audit = audit;
		this.clock = clock;
	}

	/** The person who resolved an issue. */
	public record Person(long id, String name) {
	}

	/**
	 * An open issue with its status; {@code resolution} is {@code value}, {@code removed} or
	 * {@code automatic} for a resolved issue. Values are text and {@code null} when redacted.
	 */
	public record OpenIssue(UUID id, int entityId, String entityName, String field, String label, String dataType,
			String kind, Instant createdAt, String status, String resolution, Instant resolvedAt, Person resolvedBy,
			String currentValue, String keptValue, boolean removable, boolean redacted) {
	}

	/** The resolution of one issue: a value, or the removal of the value. */
	public record Resolution(UUID issueId, String value, boolean remove) {
	}

	/**
	 * All issues of the solution with their status.
	 *
	 * @throws NoSuchElementException for an unknown solution (404)
	 */
	@Transactional(readOnly = true)
	public List<OpenIssue> list(OeaPrincipal principal, UUID solutionId) {
		this.solutions.find(solutionId).orElseThrow(() -> new NoSuchElementException("solution " + solutionId));
		return shown(principal, this.store.bySolution(solutionId));
	}

	/** The number of issues of the solution that are not resolved. */
	@Transactional(readOnly = true)
	public int openCount(UUID solutionId) {
		return this.blocks.holding(this.store.bySolution(solutionId)).size();
	}

	/**
	 * Resolves issues of the solution in one transaction.
	 *
	 * @throws NoSuchElementException for an unknown solution (404)
	 * @throws InvalidSolutionException naming an issue that cannot be resolved this way (422)
	 * @throws PermissionDeniedException without {@code solution:write} or {@code entity:write}, or
	 * for a value the person may not write (403)
	 */
	@Transactional
	public List<OpenIssue> resolve(OeaPrincipal principal, UUID solutionId, List<Resolution> resolutions) {
		Long person = principal.personId();
		Set<String> held = this.permissions.permissions(principal);
		boolean solutionWriter = held.contains(SOLUTION_WRITE);
		if (person == null || (!solutionWriter && !held.contains(ENTITY_WRITE))) {
			throw new PermissionDeniedException();
		}
		GoLiveStore.SolutionRow solution = this.solutions.find(solutionId)
			.orElseThrow(() -> new NoSuchElementException("solution " + solutionId));
		if (resolutions.isEmpty() || resolutions.size() > MAX_RESOLUTIONS) {
			throw new InvalidSolutionException("resolutions",
					"Resolve between 1 and " + MAX_RESOLUTIONS + " issues at a time.");
		}
		Set<UUID> ids = new HashSet<>();
		for (Resolution resolution : resolutions) {
			if (resolution.issueId() == null || !ids.add(resolution.issueId())) {
				throw new InvalidSolutionException("resolutions", "Name every issue once.");
			}
		}
		Map<UUID, IssueRow> rows = new HashMap<>();
		this.store.lock(solutionId, ids).forEach((row) -> rows.put(row.id(), row));
		MetatypeRules rules = MetatypeRules.load(this.ruleStore, LocalDate.now(this.clock));
		Map<Integer, StoredEntity> stored = new LinkedHashMap<>();
		Map<Integer, Map<UUID, TypedValue>> next = new LinkedHashMap<>();
		Map<Integer, List<EntityInput.PropertyInput>> written = new LinkedHashMap<>();
		Map<UUID, String> outcome = new LinkedHashMap<>();
		List<Issue> errors = new ArrayList<>();
		for (Resolution resolution : resolutions) {
			IssueRow row = rows.get(resolution.issueId());
			String key = resolution.issueId().toString();
			if (row == null || row.resolved()) {
				errors.add(new Issue(key, "INVALID_VALUE", "The issue is not open in this solution."));
				continue;
			}
			StoredEntity entity = stored.computeIfAbsent(row.entityId(), (id) -> this.entities.lock(id).orElse(null));
			if (entity == null || entity.deleted() || row.propertyDefId() == null) {
				errors.add(new Issue(key, "INVALID_VALUE", "The entity of the issue no longer exists."));
				continue;
			}
			MetatypeRules.Metatype metatype = rules.metatype(entity.metatypeId());
			MetatypeRules.Mapping mapping = (metatype != null) ? metatype.mappings().get(row.propertyDefId()) : null;
			Map<UUID, TypedValue> values = next.computeIfAbsent(entity.id(),
					(id) -> new LinkedHashMap<>(this.entities.values(id)));
			if (resolution.remove() == (resolution.value() != null)) {
				errors.add(new Issue(key, "INVALID_VALUE", "Give either a value or the removal."));
				continue;
			}
			if (resolution.remove()) {
				if (EntityBlocks.MANDATORY_MISSING.equals(row.kind())
						|| (EntityBlocks.INVALID_VALUE.equals(row.kind()) && mapping != null
								&& "mandatory".equals(mapping.necessity()))) {
					errors.add(new Issue(key, "INVALID_VALUE", "The value of a required property cannot be removed."));
					continue;
				}
				if (!EntityBlocks.NOT_IN_METAMODEL.equals(row.kind()) && values.remove(row.propertyDefId()) != null) {
					written.computeIfAbsent(entity.id(), (id) -> new ArrayList<>())
						.add(new EntityInput.PropertyInput(row.propertyDefId(), null));
				}
				outcome.put(row.id(), "removed");
				continue;
			}
			TypedValue typed = (mapping != null && !resolution.value().isEmpty())
					? EntityValidator.typed(mapping.definition(), resolution.value(), this.entities::isActive) : null;
			if (typed == null) {
				errors.add(new Issue(key, "INVALID_VALUE", (mapping == null)
						? "The property is not part of the metamodel of the entity." : "The value is not valid for the property."));
				continue;
			}
			values.put(row.propertyDefId(), typed);
			written.computeIfAbsent(entity.id(), (id) -> new ArrayList<>())
				.add(new EntityInput.PropertyInput(row.propertyDefId(), resolution.value()));
			outcome.put(row.id(), "value");
		}
		if (!errors.isEmpty()) {
			throw new InvalidSolutionException(errors);
		}
		if (!solutionWriter) {
			written.forEach((entityId, properties) -> this.writeAccess.require(principal, properties, entityId));
		}
		EntityWriter.WriteContext context = new EntityWriter.WriteContext(UUID.randomUUID(), AuditEvent.Agent.NONE);
		for (int entityId : written.keySet()) {
			StoredEntity entity = stored.get(entityId);
			MetatypeRules.Metatype metatype = rules.metatype(entity.metatypeId());
			Fields fields = new Fields(entity.metatypeId(), entity.name(),
					Objects.requireNonNullElse(entity.description(), ""), entity.logical(), entity.externalId());
			this.writer.write(principal, new EntityValidator.Checked(List.of(), List.of(), metatype, fields,
					next.get(entityId)), entity, this.entities.values(entityId), context);
		}
		Instant now = this.clock.instant();
		outcome.forEach((id, resolution) -> this.store.resolve(id, resolution, person, now));
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("solutionId", solutionId.toString());
		details.put("name", solution.name());
		details.put("issueIds", outcome.keySet().stream().map(UUID::toString).toList());
		details.put("entityIds", outcome.keySet().stream().map((id) -> rows.get(id).entityId()).distinct().toList());
		this.audit.append(new AuditEvent("solution.open-issues-resolved", AuditEvent.Category.CHANGE,
				AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal),
				"solution", solutionId.toString(), details));
		return shown(principal, this.store.bySolution(solutionId));
	}

	private List<OpenIssue> shown(OeaPrincipal principal, List<IssueRow> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		Set<UUID> holding = this.blocks.holding(rows);
		Set<UUID> properties = new HashSet<>();
		rows.stream().map(IssueRow::propertyDefId).filter(Objects::nonNull).forEach(properties::add);
		Map<UUID, String> propertyLabels = this.labels.propertyLabels(this.languages.language(principal), properties);
		Map<UUID, EntityVersionStore.Definition> definitions = this.versions.definitions(properties);
		PropertyAccess.Snapshot reader = this.access.of(principal);
		MetatypeRules rules = MetatypeRules.load(this.ruleStore, LocalDate.now(this.clock));
		Map<Integer, Map<UUID, TypedValue>> values = new HashMap<>();
		Map<Integer, StoredEntity> stored = new HashMap<>();
		Set<Integer> references = new HashSet<>();
		for (IssueRow row : rows) {
			TypedValue value = (row.propertyDefId() != null)
					? values.computeIfAbsent(row.entityId(), this.entities::values).get(row.propertyDefId()) : null;
			if (value != null && value.reference() != null) {
				references.add(value.reference());
			}
		}
		Map<Integer, String> names = this.versions.entityNames(references);
		List<OpenIssue> issues = new ArrayList<>();
		for (IssueRow row : rows) {
			boolean resolved = row.resolved() || !holding.contains(row.id());
			String resolution = row.resolved() ? row.resolution() : resolved ? "automatic" : null;
			boolean redacted = row.propertyDefId() != null && !reader.readableFor(row.propertyDefId(), row.entityId());
			TypedValue value = (row.propertyDefId() != null)
					? values.getOrDefault(row.entityId(), Map.of()).get(row.propertyDefId()) : null;
			String current = (redacted || value == null) ? null
					: (value.reference() != null) ? names.getOrDefault(value.reference(), value.asText()) : value.asText();
			EntityVersionStore.Definition definition = (row.propertyDefId() != null)
					? definitions.get(row.propertyDefId()) : null;
			StoredEntity entity = stored.computeIfAbsent(row.entityId(), (id) -> this.entities.find(id).orElse(null));
			MetatypeRules.Metatype metatype = (entity != null) ? rules.metatype(entity.metatypeId()) : null;
			MetatypeRules.Mapping mapping = (metatype != null && row.propertyDefId() != null)
					? metatype.mappings().get(row.propertyDefId()) : null;
			boolean removable = EntityBlocks.NOT_IN_METAMODEL.equals(row.kind())
					|| (EntityBlocks.INVALID_VALUE.equals(row.kind())
							&& (mapping == null || !"mandatory".equals(mapping.necessity())));
			Person by = (row.resolvedBy() != null) ? new Person(row.resolvedBy(), row.resolvedByName()) : null;
			issues.add(new OpenIssue(row.id(), row.entityId(), row.entityName(), row.field(),
					(row.propertyDefId() != null) ? propertyLabels.getOrDefault(row.propertyDefId(), row.field())
							: row.field(),
					(definition != null) ? definition.dataType() : "string", row.kind(), row.createdAt(),
					resolved ? "resolved" : "open", resolution, row.resolvedAt(), by, current,
					redacted ? null : row.keptValue(), removable && !resolved, redacted));
		}
		return List.copyOf(issues);
	}

}
