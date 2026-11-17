package io.oea.app.metamodel;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.solution.DeltaValueStore;
import io.oea.core.solution.SnapshotStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The question of a metamodel change per affected active solution (design D9 of
 * us-153-open-issues; US-154). A change that removes a property from metatypes, or an enumeration
 * value, affects an active solution with delta values of it or with extension metatypes among the
 * changed ones. Every affected solution needs a decision: {@code apply} removes the values from its
 * deltas (a modification left without a change is removed), {@code keep} stores a snapshot until
 * its go-live. Runs in the transaction of the change; SQL runs in the stores of core (ADR-051).
 */
@Component
public class SolutionImpact {

	public static final String APPLY = "apply";

	public static final String KEEP = "keep";

	private final DeltaValueStore deltas;

	private final SnapshotStore snapshots;

	private final AuditWriter audit;

	SolutionImpact(DeltaValueStore deltas, SnapshotStore snapshots, AuditWriter audit) {
		this.deltas = deltas;
		this.snapshots = snapshots;
		this.audit = audit;
	}

	/** A property, or one enumeration value of it, that a metatype loses. */
	public record Lost(UUID metatypeId, UUID propertyDefId, String enumValue) {
	}

	/**
	 * An affected solution: how many delta values and extension metatypes the change touches, and
	 * the properties it loses.
	 */
	public record Affected(UUID id, String name, String status, int deltaValues, int extensionMetatypes,
			List<Lost> lost, Map<UUID, String> labels) {
	}

	/**
	 * The decisions of a request; {@code null} for a change that is not asked (a package import),
	 * which leaves the solutions as they are.
	 */
	public record Decisions(Map<UUID, String> bySolution) {

		public static Decisions of(Map<UUID, String> bySolution) {
			return new Decisions(Map.copyOf(bySolution));
		}

	}

	/** The active solutions the lost properties affect. */
	public List<Affected> affected(List<Lost> lost) {
		Map<UUID, Integer> values = new LinkedHashMap<>();
		Map<UUID, Integer> extensions = new LinkedHashMap<>();
		Map<UUID, Set<Lost>> pairs = new LinkedHashMap<>();
		Map<String, List<Lost>> byProperty = new LinkedHashMap<>();
		for (Lost entry : lost) {
			byProperty.computeIfAbsent(entry.propertyDefId() + "|" + entry.enumValue(), (k) -> new ArrayList<>())
				.add(entry);
		}
		for (List<Lost> group : byProperty.values()) {
			Lost first = group.get(0);
			List<UUID> metatypes = group.stream().map(Lost::metatypeId).distinct().toList();
			for (DeltaValueStore.ValueHit hit : this.deltas.values(first.propertyDefId(), metatypes,
					first.enumValue())) {
				values.merge(hit.solutionId(), hit.count(), Integer::sum);
				pairs.computeIfAbsent(hit.solutionId(), (k) -> new LinkedHashSet<>())
					.add(new Lost(hit.metatypeId(), first.propertyDefId(), first.enumValue()));
			}
			for (DeltaValueStore.ExtensionHit hit : this.deltas.extensionMetatypes(metatypes)) {
				extensions.merge(hit.solutionId(), 1, Integer::sum);
				pairs.computeIfAbsent(hit.solutionId(), (k) -> new LinkedHashSet<>())
					.add(new Lost(hit.metatypeId(), first.propertyDefId(), first.enumValue()));
			}
		}
		List<Affected> affected = new ArrayList<>();
		Set<UUID> properties = new HashSet<>();
		lost.forEach((entry) -> properties.add(entry.propertyDefId()));
		Map<UUID, String> labels = this.deltas.propertyNames(properties);
		for (DeltaValueStore.SolutionRow solution : this.deltas.solutions(pairs.keySet())) {
			affected.add(new Affected(solution.id(), solution.name(), solution.status(),
					values.getOrDefault(solution.id(), 0), extensions.getOrDefault(solution.id(), 0),
					List.copyOf(pairs.get(solution.id())), labels));
		}
		return affected;
	}

	/**
	 * Applies the decisions to the affected solutions and adds them to the details of the audit
	 * event of the change.
	 *
	 * @throws SolutionDecisionRequiredException when an affected solution has no decision (409)
	 * @throws MetamodelChangeException naming {@code decision} for a decision on a solution that is
	 * not affected, or an unknown decision (422)
	 */
	public void apply(OeaPrincipal principal, List<Lost> lost, Decisions decisions, Map<String, Object> details,
			Instant now) {
		if (decisions == null) {
			return;
		}
		List<Affected> affected = affected(lost);
		Set<UUID> ids = new HashSet<>();
		affected.forEach((solution) -> ids.add(solution.id()));
		for (Map.Entry<UUID, String> decision : decisions.bySolution().entrySet()) {
			if (!ids.contains(decision.getKey())) {
				throw MetamodelChangeException.invalid("decision",
						"The solution " + decision.getKey() + " is not affected by this change.");
			}
			if (!APPLY.equals(decision.getValue()) && !KEEP.equals(decision.getValue())) {
				throw MetamodelChangeException.invalid("decision", "A decision is apply or keep.");
			}
		}
		if (!decisions.bySolution().keySet().containsAll(ids)) {
			throw new SolutionDecisionRequiredException(affected);
		}
		if (affected.isEmpty()) {
			return;
		}
		long person = MetamodelEffects.author(principal);
		List<Map<String, Object>> shown = new ArrayList<>();
		for (Affected solution : affected) {
			String decision = decisions.bySolution().get(solution.id());
			if (KEEP.equals(decision)) {
				solution.lost()
					.forEach((entry) -> this.snapshots.insert(solution.id(), entry.metatypeId(), entry.propertyDefId(),
							entry.enumValue(), person, now));
			}
			else {
				removeValues(principal, solution, now);
			}
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("solutionId", solution.id().toString());
			entry.put("decision", decision);
			shown.add(entry);
		}
		details.put("solutionDecisions", shown);
	}

	private void removeValues(OeaPrincipal principal, Affected solution, Instant now) {
		Set<UUID> touched = new LinkedHashSet<>();
		for (Lost entry : solution.lost()) {
			touched.addAll(this.deltas.removeValues(solution.id(), entry.propertyDefId(), List.of(entry.metatypeId()),
					entry.enumValue()));
		}
		for (UUID delta : this.deltas.emptyModifications(touched)) {
			Integer entity = this.deltas.removeDelta(delta);
			Map<String, Object> details = new LinkedHashMap<>();
			details.put("solutionId", solution.id().toString());
			details.put("deltaId", delta.toString());
			details.put("deltaType", "modified");
			details.put("entityId", entity);
			details.put("reason", "metamodel-change");
			this.audit.append(new AuditEvent("solution.delta-removed", AuditEvent.Category.CHANGE,
					AuditEvent.Severity.INFO, AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API,
					Sessions.actor(principal), "solution", solution.id().toString(), details));
		}
	}

}
