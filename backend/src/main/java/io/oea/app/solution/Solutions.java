package io.oea.app.solution;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.solution.PlateauStore;
import io.oea.core.solution.SolutionStore;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creating and reading solutions (US-038, REQ-038; design D3 of us-038-create-solution). A
 * solution is created with status {@code draft} and the caller as owner and creator, in project
 * mode without plateau references, or in plateau mode with a target plateau and its predecessor
 * (US-038 AC3, AC4; design D3 of us-084-plateaus). The permission {@code solution:write} is
 * checked from the contract.
 */
@Service
public class Solutions {

	private final SolutionStore store;

	private final AuditWriter audit;

	private final PlateauStore plateaus;

	Solutions(SolutionStore store, AuditWriter audit, PlateauStore plateaus) {
		this.store = store;
		this.audit = audit;
		this.plateaus = plateaus;
	}

	/** The input of {@link #create}; the contract checks the lengths. */
	public record NewSolution(String name, String description, UUID fromPlateauId, UUID toPlateauId) {

		public NewSolution(String name, String description) {
			this(name, description, null, null);
		}

	}

	/** A solution with the names of its owner and creator. */
	public record Solution(UUID id, String name, String description, String status, UUID fromPlateauId,
			UUID toPlateauId, long ownerId, String ownerName, Instant createdAt, long createdById,
			String createdByName) {
	}

	/**
	 * Creates the solution and audits {@code solution.created}.
	 *
	 * @throws PermissionDeniedException for an account that is not a person
	 * @throws InvalidSolutionException naming {@code name} for a blank or taken name
	 */
	@Transactional
	public Solution create(OeaPrincipal principal, NewSolution input) {
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		String name = (input.name() != null) ? input.name().strip() : "";
		if (name.isEmpty()) {
			throw new InvalidSolutionException("name", "The name must not be blank.");
		}
		plateaus(input.fromPlateauId(), input.toPlateauId());
		UUID id = UUID.randomUUID();
		boolean inserted = this.store.insert(id, name, input.description(), person, input.fromPlateauId(),
				input.toPlateauId());
		if (!inserted) {
			throw new InvalidSolutionException("name", "Name '" + name + "' is already taken");
		}
		this.audit.append(new AuditEvent("solution.created", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "solution",
				id.toString(), Map.of("name", name)));
		return find(id).orElseThrow();
	}

	/**
	 * Both plateau references or none (BR-01); the target is a target or transition plateau that is
	 * not realized, locked against a change of its predecessor, and the start is its predecessor.
	 */
	private void plateaus(UUID from, UUID to) {
		if ((from == null) != (to == null)) {
			throw new InvalidPlateauException(null, "Both plateau references must be set or both empty");
		}
		if (to == null) {
			return;
		}
		PlateauStore.PlateauRow target = this.plateaus.lock(to, false)
			.filter((plateau) -> !Plateaus.BASELINE.equals(plateau.status())
					&& !Plateaus.REALIZED.equals(plateau.status()))
			.orElseThrow(() -> new InvalidPlateauException("toPlateauId",
					"The target plateau must be a target or transition plateau that is not realized."));
		if (!from.equals(target.succeeds())) {
			throw new InvalidPlateauException("fromPlateauId",
					"The start plateau must be the predecessor of the target plateau.");
		}
	}

	/** All solutions, ordered by name case-insensitively. */
	@Transactional(readOnly = true)
	public List<Solution> list() {
		return this.store.list().stream().map(Solutions::solution).toList();
	}

	/**
	 * One solution.
	 *
	 * @throws NoSuchElementException for an unknown ID (404)
	 */
	@Transactional(readOnly = true)
	public Solution get(UUID id) {
		return find(id).orElseThrow(() -> new NoSuchElementException("solution " + id));
	}

	private java.util.Optional<Solution> find(UUID id) {
		return this.store.find(id).map(Solutions::solution);
	}

	private static Solution solution(SolutionStore.SolutionRow row) {
		return new Solution(row.id(), row.name(), row.description(), row.status(), row.fromPlateauId(),
				row.toPlateauId(), row.ownerId(), row.ownerName(), row.createdAt(), row.createdById(),
				row.createdByName());
	}

}
