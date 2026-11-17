package io.oea.app.solution;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.solution.GoLiveStore;
import io.oea.core.solution.PlateauStore;
import io.oea.core.solution.PlateauStore.PlateauRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The go-live of a plateau (US-086, US-087, US-088, US-157; design D3 of us-086-plateau-go-live).
 * Only the target or transition plateau that succeeds the baseline goes live. In one transaction,
 * its active solutions go live in the order of the last change of their deltas, each as a single
 * solution goes live; then the plateau becomes the baseline, the old baseline is realized, and the
 * plateaus that succeeded it succeed the new one. The permission {@code plateau:go-live} is
 * checked from the contract; the write roles of the values are not checked (owner decision
 * 2026-11-16).
 */
@Service
public class PlateauGoLive {

	private static final Set<String> NOT_APPROVED = Set.of("draft", "proposed");

	private final PlateauStore plateaus;

	private final GoLiveStore solutions;

	private final SolutionGoLive goLive;

	private final AuditWriter audit;

	private final Clock clock;

	PlateauGoLive(PlateauStore plateaus, GoLiveStore solutions, SolutionGoLive goLive, AuditWriter audit, Clock clock) {
		this.plateaus = plateaus;
		this.solutions = solutions;
		this.goLive = goLive;
		this.audit = audit;
		this.clock = clock;
	}

	/** A solution that goes live with the plateau, with its preview at its turn. */
	public record SolutionPlan(UUID id, String name, String status, SolutionGoLive.Preview preview) {
	}

	/** What the go-live of the plateau would do. */
	public record Preview(UUID plateauId, UUID baselineId, SolutionGoLive.Counts counts, List<SolutionPlan> solutions,
			List<SolutionGoLive.Violation> violations, List<String> notApproved, int openIssues) {
	}

	/** The outcome: the new baseline, the realized plateau and the solutions taken live. */
	public record Result(Plateaus.Plateau baseline, Plateaus.Plateau realized, List<SolutionGoLive.Result> solutions) {
	}

	/**
	 * The preview of the go-live; changes nothing.
	 *
	 * @throws NoSuchElementException for an unknown plateau (404)
	 * @throws PlateauNotNextException for a plateau that does not succeed the baseline (409)
	 */
	@Transactional(readOnly = true)
	public Preview preview(OeaPrincipal principal, UUID plateauId) {
		PlateauRow plateau = this.plateaus.find(plateauId)
			.orElseThrow(() -> new NoSuchElementException("plateau " + plateauId));
		PlateauRow baseline = next(plateau);
		List<SolutionPlan> plans = plans(principal, plateauId);
		int created = 0;
		int modified = 0;
		int retired = 0;
		int connections = 0;
		int kept = 0;
		List<SolutionGoLive.Violation> violations = new ArrayList<>();
		List<String> notApproved = new ArrayList<>();
		for (SolutionPlan plan : plans) {
			SolutionGoLive.Counts counts = plan.preview().counts();
			created += counts.created();
			modified += counts.modified();
			retired += counts.retired();
			connections += counts.retiredConnections();
			kept += counts.notInMetamodel();
			violations.addAll(plan.preview().violations());
			if (NOT_APPROVED.contains(plan.status())) {
				notApproved.add(plan.name());
			}
		}
		return new Preview(plateauId, baseline.id(),
				new SolutionGoLive.Counts(created, modified, retired, connections, kept), plans, violations,
				notApproved, violations.size() + kept);
	}

	/**
	 * Takes the plateau live in one transaction.
	 *
	 * @throws NoSuchElementException for an unknown plateau (404)
	 * @throws PlateauNotNextException for a plateau that does not succeed the baseline (409)
	 * @throws InvalidPlateauException naming {@code name} for another name than the plateau's (422)
	 * @throws MergeRequiredException for unresolved field conflicts of any of its solutions (409)
	 */
	@Transactional
	public Result goLive(OeaPrincipal principal, UUID plateauId, String name) {
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		PlateauRow plateau = this.plateaus.lock(plateauId, true)
			.orElseThrow(() -> new NoSuchElementException("plateau " + plateauId));
		PlateauRow baseline = next(plateau);
		this.plateaus.lock(baseline.id(), true);
		if (!plateau.name().equals(name)) {
			throw new InvalidPlateauException("name", "Enter the exact name of the plateau to confirm the go-live.");
		}
		// every conflict of every solution at its turn, before anything is written
		List<SolutionGoLive.Conflict> conflicts = new ArrayList<>();
		List<SolutionPlan> plans = plans(principal, plateauId);
		plans.forEach((plan) -> conflicts.addAll(plan.preview().conflicts()));
		if (!conflicts.isEmpty()) {
			throw new MergeRequiredException(conflicts);
		}
		List<SolutionGoLive.Result> results = new ArrayList<>();
		for (SolutionPlan plan : plans) {
			results.add(this.goLive.goLiveOne(principal, plan.id()));
		}
		Instant now = this.clock.instant();
		LocalDate day = LocalDate.ofInstant(now, ZoneOffset.UTC);
		List<UUID> followers = this.plateaus.transition(plateauId, baseline.id(), day, person, now);
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("name", plateau.name());
		details.put("realizedId", baseline.id().toString());
		details.put("realizedName", baseline.name());
		details.put("solutionIds", plans.stream().map((plan) -> plan.id().toString()).toList());
		details.put("created", results.stream().mapToInt((result) -> result.counts().created()).sum());
		details.put("modified", results.stream().mapToInt((result) -> result.counts().modified()).sum());
		details.put("retired", results.stream().mapToInt((result) -> result.counts().retired()).sum());
		details.put("openIssues", results.stream().mapToInt((result) -> result.openIssues().size()).sum());
		this.audit.append(new AuditEvent("plateau.went-live", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "plateau",
				plateauId.toString(), details));
		for (UUID follower : followers) {
			PlateauRow row = this.plateaus.find(follower).orElseThrow();
			this.audit.append(new AuditEvent("plateau.updated", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
					AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "plateau",
					follower.toString(), Map.of("name", row.name(), "fields", List.of("succeeds"))));
		}
		return new Result(Plateaus.Plateau.of(this.plateaus.find(plateauId).orElseThrow()),
				Plateaus.Plateau.of(this.plateaus.find(baseline.id()).orElseThrow()), results);
	}

	/** The baseline the plateau succeeds; the plateau must be a target or transition plateau after it. */
	private PlateauRow next(PlateauRow plateau) {
		PlateauRow baseline = this.plateaus.baseline().orElse(null);
		boolean planned = "target".equals(plateau.status()) || "transition".equals(plateau.status());
		if (!planned || baseline == null || !baseline.id().equals(plateau.succeeds())) {
			throw new PlateauNotNextException(plateau.name());
		}
		return baseline;
	}

	/** The active solutions in the order of the go-live, each with its preview at its turn. */
	private List<SolutionPlan> plans(OeaPrincipal principal, UUID plateauId) {
		List<SolutionPlan> plans = new ArrayList<>();
		for (UUID id : this.plateaus.goLiveOrder(plateauId)) {
			GoLiveStore.SolutionRow row = this.solutions.find(id).orElseThrow();
			plans.add(new SolutionPlan(id, row.name(), row.status(), this.goLive.preview(principal, id)));
		}
		return plans;
	}

}
