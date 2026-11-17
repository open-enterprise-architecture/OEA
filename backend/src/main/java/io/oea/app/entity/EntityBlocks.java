package io.oea.app.entity;

import io.oea.app.access.LanguagePreferences;
import io.oea.app.metamodel.MetamodelLabels;
import io.oea.app.security.OeaPrincipal;
import io.oea.core.entity.EntityStore;
import io.oea.core.entity.EntityStore.StoredEntity;
import io.oea.core.entity.MetatypeRuleStore;
import io.oea.core.entity.TypedValue;
import io.oea.core.solution.OpenIssueStore;
import io.oea.core.solution.OpenIssueStore.IssueRow;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Entities blocked by open issues of solutions (design D3 of us-153-open-issues; US-153 AC1,
 * AC4): an unresolved issue blocks its entity while its violation still holds against the current
 * metamodel and values. A violation that no longer holds counts as resolved {@code automatic};
 * nothing is written for it. An issue {@code not-in-metamodel} always holds (US-154 AC4). Lives in
 * {@code app/entity} so that the entity writes can use it without a package cycle.
 */
@Component
public class EntityBlocks {

	public static final String MANDATORY_MISSING = "mandatory-missing";

	public static final String INVALID_VALUE = "invalid-value";

	public static final String NOT_IN_METAMODEL = "not-in-metamodel";

	private final OpenIssueStore issues;

	private final EntityStore entities;

	private final MetatypeRuleStore rules;

	private final MetamodelLabels labels;

	private final LanguagePreferences languages;

	private final Clock clock;

	EntityBlocks(OpenIssueStore issues, EntityStore entities, MetatypeRuleStore rules, MetamodelLabels labels,
			LanguagePreferences languages, Clock clock) {
		this.issues = issues;
		this.entities = entities;
		this.rules = rules;
		this.labels = labels;
		this.languages = languages;
		this.clock = clock;
	}

	/** An open issue of a blocked entity with the label of its field. */
	public record BlockingIssue(UUID id, String field, String label, String kind, UUID solutionId,
			String solutionName) {
	}

	/** A blocked entity with its open issues. */
	public record Block(int entityId, String entityName, List<BlockingIssue> issues) {
	}

	/** The open issues per entity among these, labelled in the language of the principal; entities without are left out. */
	public Map<Integer, Block> open(OeaPrincipal principal, Collection<Integer> entityIds) {
		List<IssueRow> rows = this.issues.unresolved(new HashSet<>(entityIds));
		if (rows.isEmpty()) {
			return Map.of();
		}
		Set<UUID> holding = holding(rows);
		Set<UUID> properties = new HashSet<>();
		rows.stream().map(IssueRow::propertyDefId).filter(Objects::nonNull).forEach(properties::add);
		Map<UUID, String> propertyLabels = this.labels.propertyLabels(this.languages.language(principal), properties);
		Map<Integer, Block> blocks = new LinkedHashMap<>();
		for (IssueRow row : rows) {
			if (!holding.contains(row.id())) {
				continue;
			}
			Block block = blocks.computeIfAbsent(row.entityId(),
					(id) -> new Block(id, row.entityName(), new ArrayList<>()));
			String label = (row.propertyDefId() != null) ? propertyLabels.getOrDefault(row.propertyDefId(), row.field())
					: row.field();
			block.issues()
				.add(new BlockingIssue(row.id(), row.field(), label, row.kind(), row.solutionId(), row.solutionName()));
		}
		return blocks;
	}

	/** The entities among these that have an open issue. */
	public Set<Integer> blocked(Collection<Integer> entityIds) {
		List<IssueRow> rows = this.issues.unresolved(new HashSet<>(entityIds));
		if (rows.isEmpty()) {
			return Set.of();
		}
		Set<UUID> holding = holding(rows);
		Set<Integer> blocked = new HashSet<>();
		rows.stream().filter((row) -> holding.contains(row.id())).forEach((row) -> blocked.add(row.entityId()));
		return blocked;
	}

	/**
	 * Rejects a write of these entities while one has an open issue.
	 *
	 * @throws EntityBlockedException naming the blocked entities and their issues (409)
	 */
	public void check(OeaPrincipal principal, Collection<Integer> entityIds) {
		Map<Integer, Block> blocks = open(principal, entityIds);
		if (!blocks.isEmpty()) {
			throw new EntityBlockedException(List.copyOf(blocks.values()));
		}
	}

	/**
	 * The IDs of the unresolved issues among these whose violation still holds; a stored
	 * resolution is never evaluated.
	 */
	public Set<UUID> holding(List<IssueRow> rows) {
		List<IssueRow> unresolved = rows.stream().filter((row) -> !row.resolved()).toList();
		if (unresolved.isEmpty()) {
			return Set.of();
		}
		MetatypeRules current = MetatypeRules.load(this.rules, LocalDate.now(this.clock));
		Map<Integer, StoredEntity> stored = new HashMap<>();
		Map<Integer, Map<UUID, TypedValue>> values = new HashMap<>();
		Set<UUID> holding = new HashSet<>();
		for (IssueRow row : unresolved) {
			if (NOT_IN_METAMODEL.equals(row.kind())) {
				holding.add(row.id());
				continue;
			}
			StoredEntity entity = stored.computeIfAbsent(row.entityId(), (id) -> this.entities.find(id).orElse(null));
			if (entity == null || entity.deleted() || row.propertyDefId() == null) {
				continue;
			}
			MetatypeRules.Metatype metatype = current.metatype(entity.metatypeId());
			MetatypeRules.Mapping mapping = (metatype != null) ? metatype.mappings().get(row.propertyDefId()) : null;
			TypedValue value = values.computeIfAbsent(entity.id(), this.entities::values).get(row.propertyDefId());
			if (holds(row.kind(), mapping, value)) {
				holding.add(row.id());
			}
		}
		return holding;
	}

	private boolean holds(String kind, MetatypeRules.Mapping mapping, TypedValue value) {
		if (mapping == null) {
			// the property left the metatype: its values were removed, nothing is missing or invalid
			return false;
		}
		if (MANDATORY_MISSING.equals(kind)) {
			return "mandatory".equals(mapping.necessity()) && value == null;
		}
		return value != null && !EntityValidator.accepts(mapping.definition(), value.asText(), this.entities::isActive);
	}

}
