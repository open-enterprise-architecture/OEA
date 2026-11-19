package io.oea.app.navigation;

import io.oea.app.security.OeaPrincipal;
import io.oea.core.catalog.CatalogStore;
import io.oea.core.diagram.DiagramStore;
import io.oea.core.entity.EntityStore;
import io.oea.core.solution.PlateauStore;
import io.oea.core.solution.PlateauStore.PlateauRow;
import io.oea.core.solution.SolutionBrowseStore;
import io.oea.core.solution.SolutionBrowseStore.ActiveSolution;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The solution browser of the sidebar (US-132, US-133, REQ-138 to REQ-140; design D3 of
 * us-132-solution-browser): the top level with "Current state", the plateaus and the projects, and
 * the pages of the categories. Everything is read only and computed from the stores; nothing is
 * stored or audited. Reading needs {@code entity:read}, which the contract enforces.
 */
@Service
public class Browser {

	/** Scope of the entities and catalogs of the repository. */
	public static final String CURRENT_STATE = "current-state";

	/** Scope of the deltas of one solution. */
	public static final String SOLUTION = "solution";

	public static final String COMPONENTS = "components";

	public static final String CONNECTIONS = "connections";

	public static final String CATALOGS = "catalogs";

	public static final String DIAGRAMS = "diagrams";

	private static final long NO_PERSON = -1;

	private static final Set<String> PLATEAU_STATUS = Set.of("transition", "target");

	private final EntityStore entities;

	private final CatalogStore catalogs;

	private final DiagramStore diagrams;

	private final PlateauStore plateaus;

	private final SolutionBrowseStore solutions;

	Browser(EntityStore entities, CatalogStore catalogs, DiagramStore diagrams, PlateauStore plateaus,
			SolutionBrowseStore solutions) {
		this.diagrams = diagrams;
		this.entities = entities;
		this.catalogs = catalogs;
		this.plateaus = plateaus;
		this.solutions = solutions;
	}

	/** A solution with the number of its deltas. */
	public record SolutionNode(UUID id, String name, String status, int components) {
	}

	/** A plateau of the transition or target stage with its active solutions. */
	public record PlateauNode(UUID id, String name, String status, List<SolutionNode> solutions) {
	}

	/** The counts of "Current state". */
	public record CurrentState(int components, int connections, int catalogs, int diagrams) {
	}

	/** The top level of the browser. */
	public record Top(CurrentState currentState, List<PlateauNode> plateaus, List<SolutionNode> projects) {
	}

	/**
	 * An object of a category; {@code kind} is {@code entity}, {@code catalog}, {@code diagram} or {@code delta}. An
	 * entity carries the number of its children, whether it is a structuring aid and its version
	 * (US-135, US-136); catalogs and deltas have none of them.
	 */
	public record Item(String kind, String id, String name, String metatypeAbbreviation, Integer entityId,
			UUID solutionId, String deltaType, String notation, int childCount, boolean structuringAid,
			int version) {

		static Item catalog(String id, String name) {
			return new Item("catalog", id, name, null, null, null, null, null, 0, false, 0);
		}

		static Item diagram(DiagramStore.Row row) {
			return new Item("diagram", row.id().toString(), row.name(), null, null, null, null,
					row.viewpointNotation(), 0, false, 0);
		}

		static Item delta(String id, String name, String abbreviation, Integer entityId, UUID solutionId,
				String deltaType) {
			return new Item("delta", id, name, abbreviation, entityId, solutionId, deltaType, null, 0, false, 0);
		}

		static Item entity(EntityStore.BrowseRow row) {
			return new Item("entity", String.valueOf(row.id()), row.name(), row.abbreviation(), null, null, null,
					null, row.childCount(), row.structuringAid(), row.version());
		}

	}

	/** One page of a category; {@code page} counts from 0. */
	public record ItemPage(List<Item> items, int page, int pageSize, long totalElements, int totalPages) {
	}

	/** The top level: counts of the current state, the plateaus in the order of the overview, the projects. */
	@Transactional(readOnly = true)
	public Top top(OeaPrincipal principal) {
		long personId = person(principal);
		CurrentState current = new CurrentState(this.entities.countActive(false), this.entities.countActive(true),
				this.catalogs.countVisible(personId), this.diagrams.countActive());
		List<ActiveSolution> active = this.solutions.activeSolutions();
		List<PlateauNode> nodes = new ArrayList<>();
		for (PlateauRow plateau : this.plateaus.list()) {
			if (PLATEAU_STATUS.contains(plateau.status())) {
				nodes.add(new PlateauNode(plateau.id(), plateau.name(), plateau.status(), active.stream()
					.filter((solution) -> plateau.id().equals(solution.toPlateauId()))
					.map(Browser::node)
					.toList()));
			}
		}
		List<SolutionNode> projects = active.stream()
			.filter((solution) -> solution.toPlateauId() == null)
			.map(Browser::node)
			.toList();
		return new Top(current, nodes, projects);
	}

	/**
	 * One page of a category. {@code page} counts from 0 and {@code pageSize} is 1 to 200; the
	 * contract checks both and that {@code parentEntityId} is at least 1. {@code parentEntityId}
	 * lists the children of an element entity instead of the root level of the components of the
	 * current state (US-135).
	 */
	@Transactional(readOnly = true)
	public ItemPage items(OeaPrincipal principal, String scope, UUID solutionId, String category,
			Integer parentEntityId, int page, int pageSize) {
		long personId = person(principal);
		if (!CURRENT_STATE.equals(scope) && !SOLUTION.equals(scope)) {
			throw InvalidBrowserQueryException.unknownValue("scope", scope);
		}
		if (!Set.of(COMPONENTS, CONNECTIONS, CATALOGS, DIAGRAMS).contains(category)) {
			throw InvalidBrowserQueryException.unknownValue("category", category);
		}
		boolean solution = SOLUTION.equals(scope);
		if (solution && solutionId == null) {
			throw new InvalidBrowserQueryException("solutionId", "A solution needs its solutionId.");
		}
		if (!solution && solutionId != null) {
			throw new InvalidBrowserQueryException("solutionId", "The current state takes no solutionId.");
		}
		if (solution && !COMPONENTS.equals(category)) {
			throw new InvalidBrowserQueryException("category", "A solution has the category components only.");
		}
		if (parentEntityId != null && (solution || !COMPONENTS.equals(category))) {
			throw new InvalidBrowserQueryException("parentEntityId",
					"Only the components of the current state have a parent.");
		}
		long offset = (long) page * pageSize;
		if (solution) {
			if (!this.solutions.exists(solutionId)) {
				throw new NoSuchElementException("solution " + solutionId);
			}
			return page(this.solutions.pageDeltas(solutionId, pageSize, offset)
				.stream()
				.map((delta) -> Item.delta(delta.id().toString(), delta.name(), delta.abbreviation(),
						delta.entityId(), solutionId, delta.deltaType()))
				.toList(), page, pageSize, this.solutions.countDeltas(solutionId));
		}
		return switch (category) {
			case CATALOGS -> page(this.catalogs.pageVisible(personId, pageSize, offset)
				.stream()
				.map((row) -> Item.catalog(row.id().toString(), row.name()))
				.toList(), page, pageSize, this.catalogs.countVisible(personId));
			case DIAGRAMS -> page(this.diagrams
				.page(new DiagramStore.Query(false, null, null, null, pageSize, offset))
				.stream()
				.map(Item::diagram)
				.toList(), page, pageSize, this.diagrams.countActive());
			case CONNECTIONS -> page(this.entities.pageActive(true, pageSize, offset)
				.stream()
				.map(Item::entity)
				.toList(), page, pageSize, this.entities.countActive(true));
			default -> components(parentEntityId, page, pageSize, offset);
		};
	}

	/** The root level of the components, or the children of an element entity. */
	private ItemPage components(Integer parentEntityId, int page, int pageSize, long offset) {
		if (parentEntityId == null) {
			return page(this.entities.pageRoot(pageSize, offset).stream().map(Item::entity).toList(), page, pageSize,
					this.entities.countRoot());
		}
		if (!this.entities.isActiveElement(parentEntityId)) {
			throw new NoSuchElementException("entity " + parentEntityId);
		}
		return page(this.entities.pageChildren(parentEntityId, pageSize, offset).stream().map(Item::entity).toList(),
				page, pageSize, this.entities.countChildren(parentEntityId));
	}

	private static ItemPage page(List<Item> items, int page, int pageSize, long total) {
		return new ItemPage(items, page, pageSize, total, (int) ((total + pageSize - 1) / pageSize));
	}

	private static SolutionNode node(ActiveSolution solution) {
		return new SolutionNode(solution.id(), solution.name(), solution.status(), solution.deltaCount());
	}

	/** The person whose personal catalogs count; a caller that is no person sees instance catalogs only. */
	private static long person(OeaPrincipal principal) {
		return (principal.type() == OeaPrincipal.Type.PERSON && principal.personId() != null) ? principal.personId()
				: NO_PERSON;
	}

}
