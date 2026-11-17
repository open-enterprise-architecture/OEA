package io.oea.app.solution;

import io.oea.app.security.OeaPrincipal;
import io.oea.app.security.PermissionDeniedException;
import io.oea.app.security.Sessions;
import io.oea.audit.AuditEvent;
import io.oea.audit.AuditWriter;
import io.oea.core.solution.PlateauStore;
import io.oea.core.solution.PlateauStore.PlateauRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * Creating, reading and editing plateaus (US-084, US-085, US-089, US-155; design D2 of
 * us-084-plateaus). The first baseline turns plateau mode on; a target or transition plateau has
 * a predecessor that is not realized and a basis; the status changes only through the plateau
 * go-live; a realized plateau is read-only. The permissions are checked from the contract.
 */
@Service
public class Plateaus {

	public static final String BASELINE = "baseline";

	public static final String REALIZED = "realized";

	private static final Set<String> CREATED = Set.of(BASELINE, "target", "transition");

	private static final Set<String> BASES = Set.of("catalog", "predecessor");

	private final PlateauStore store;

	private final AuditWriter audit;

	private final Clock clock;

	Plateaus(PlateauStore store, AuditWriter audit, Clock clock) {
		this.store = store;
		this.audit = audit;
		this.clock = clock;
	}

	/** A plateau with its creator and the number of solutions that target it. */
	public record Plateau(UUID id, String name, String description, String status, UUID succeeds, String basis,
			LocalDate validFrom, LocalDate validTo, Instant createdAt, long createdById, String createdByName,
			Instant updatedAt, int solutionCount) {

		static Plateau of(PlateauRow row) {
			return new Plateau(row.id(), row.name(), row.description(), row.status(), row.succeeds(), row.basis(),
					row.validFrom(), row.validTo(), row.createdAt(), row.createdById(), row.createdByName(),
					row.updatedAt(), row.solutionCount());
		}

	}

	/** The input of a creation; the contract checks the lengths. */
	public record NewPlateau(String name, String description, String status, UUID succeeds, String basis,
			LocalDate validFrom) {
	}

	/** The input of an edit; {@code status} is {@code null} when not sent. */
	public record PlateauChange(String name, String description, String status, UUID succeeds, String basis,
			LocalDate validFrom) {
	}

	/**
	 * Creates the plateau and audits {@code plateau.created}.
	 *
	 * @throws PermissionDeniedException for an account that is not a person
	 * @throws InvalidPlateauException for a rule the input violates
	 */
	@Transactional
	public Plateau create(OeaPrincipal principal, NewPlateau input) {
		long person = person(principal);
		if (!CREATED.contains(input.status())) {
			throw new InvalidPlateauException("status", "A plateau is created as baseline, target or transition.");
		}
		String name = name(input.name(), null);
		boolean baseline = BASELINE.equals(input.status());
		if (baseline) {
			shapeOfBaseline(input.succeeds(), input.basis());
			if (this.store.baselineExists()) {
				throw new InvalidPlateauException("status", "A baseline plateau already exists");
			}
		}
		else {
			if (!this.store.baselineExists()) {
				throw new InvalidPlateauException("status",
						"No baseline plateau exists. Create an initial baseline first.");
			}
			predecessor(null, input.succeeds());
			basis(input.basis());
		}
		UUID id = UUID.randomUUID();
		boolean inserted = this.store.insert(id, input.status(), new PlateauStore.Fields(name, input.description(),
				input.succeeds(), input.basis(), input.validFrom()), person, this.clock.instant());
		if (!inserted) {
			// a parallel creation took the name or the baseline
			throw baseline ? new InvalidPlateauException("status", "A baseline plateau already exists")
					: taken(name);
		}
		this.audit.append(new AuditEvent("plateau.created", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "plateau", id.toString(),
				Map.of("name", name, "status", input.status())));
		return Plateau.of(this.store.find(id).orElseThrow());
	}

	/**
	 * Edits the plateau and audits {@code plateau.updated} with the changed fields.
	 *
	 * @throws NoSuchElementException for an unknown plateau (404)
	 * @throws InvalidPlateauException for a rule the input violates, or a realized plateau
	 * @throws PlateauInUseException for a new predecessor while solutions target the plateau
	 */
	@Transactional
	public Plateau update(OeaPrincipal principal, UUID id, PlateauChange input) {
		long person = person(principal);
		PlateauRow current = this.store.lock(id, true).orElseThrow(() -> new NoSuchElementException("plateau " + id));
		if (input.status() != null && !input.status().equals(current.status())) {
			throw new InvalidPlateauException("status", "The status of a plateau changes only through its go-live.");
		}
		if (REALIZED.equals(current.status())) {
			throw new InvalidPlateauException(null, "Realized plateau is read-only");
		}
		String name = name(input.name(), id);
		if (BASELINE.equals(current.status())) {
			shapeOfBaseline(input.succeeds(), input.basis());
		}
		else {
			predecessor(id, input.succeeds());
			basis(input.basis());
			if (!Objects.equals(input.succeeds(), current.succeeds()) && current.solutionCount() > 0) {
				throw new PlateauInUseException(current.name());
			}
		}
		List<String> changed = new ArrayList<>();
		Map<String, Object> before = new LinkedHashMap<>();
		before.put("name", current.name());
		before.put("description", current.description());
		before.put("validFrom", current.validFrom());
		before.put("succeeds", current.succeeds());
		before.put("basis", current.basis());
		Map<String, Object> after = new LinkedHashMap<>();
		after.put("name", name);
		after.put("description", input.description());
		after.put("validFrom", input.validFrom());
		after.put("succeeds", input.succeeds());
		after.put("basis", input.basis());
		before.forEach((field, value) -> {
			if (!Objects.equals(value, after.get(field))) {
				changed.add(field);
			}
		});
		Instant now = this.clock.instant();
		this.store.update(id, new PlateauStore.Fields(name, input.description(), input.succeeds(), input.basis(),
				input.validFrom()), person, now);
		this.audit.append(new AuditEvent("plateau.updated", AuditEvent.Category.CHANGE, AuditEvent.Severity.INFO,
				AuditEvent.Outcome.SUCCESS, AuditEvent.Source.API, Sessions.actor(principal), "plateau", id.toString(),
				Map.of("name", name, "fields", changed)));
		return Plateau.of(this.store.find(id).orElseThrow());
	}

	/** All plateaus in the order of the overview. */
	@Transactional(readOnly = true)
	public List<Plateau> list() {
		return this.store.list().stream().map(Plateau::of).toList();
	}

	/**
	 * One plateau.
	 *
	 * @throws NoSuchElementException for an unknown ID (404)
	 */
	@Transactional(readOnly = true)
	public Plateau get(UUID id) {
		return this.store.find(id).map(Plateau::of).orElseThrow(() -> new NoSuchElementException("plateau " + id));
	}

	private static long person(OeaPrincipal principal) {
		Long person = principal.personId();
		if (person == null) {
			throw new PermissionDeniedException();
		}
		return person;
	}

	private String name(String input, UUID except) {
		String name = (input != null) ? input.strip() : "";
		if (name.isEmpty()) {
			throw new InvalidPlateauException("name", "The name must not be blank.");
		}
		if (this.store.nameTaken(name, except)) {
			throw taken(name);
		}
		return name;
	}

	private static InvalidPlateauException taken(String name) {
		return new InvalidPlateauException("name", "Name '" + name + "' is already taken");
	}

	private static void shapeOfBaseline(UUID succeeds, String basis) {
		if (succeeds != null) {
			throw new InvalidPlateauException("succeeds", "A baseline plateau has no predecessor.");
		}
		if (basis != null) {
			throw new InvalidPlateauException("basis", "A baseline plateau has no basis.");
		}
	}

	/** The predecessor exists, is not realized, and does not lead back to the plateau. */
	private void predecessor(UUID self, UUID succeeds) {
		if (succeeds == null) {
			throw new InvalidPlateauException("succeeds", "A target or transition plateau needs a predecessor.");
		}
		PlateauRow predecessor = this.store.find(succeeds)
			.orElseThrow(() -> new InvalidPlateauException("succeeds", "The predecessor does not exist."));
		if (REALIZED.equals(predecessor.status())) {
			throw new InvalidPlateauException("succeeds", "A realized plateau cannot be a predecessor.");
		}
		if (self != null && this.store.chain(succeeds).stream().anyMatch((plateau) -> plateau.id().equals(self))) {
			throw new InvalidPlateauException("succeeds", "The predecessor would lead back to this plateau.");
		}
	}

	private static void basis(String basis) {
		if (basis == null || !BASES.contains(basis)) {
			throw new InvalidPlateauException("basis", "A target or transition plateau needs the basis catalog or predecessor.");
		}
	}

}
