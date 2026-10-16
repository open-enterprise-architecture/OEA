package io.oea.app.metamodel;

import java.util.List;

/** Elements of the package collide with content of others (409, REQ-114); nothing was stored. */
public class PackageConflictException extends RuntimeException {

	/** One conflicting element; reason {@code other-package}, {@code organization} or {@code built-in}. */
	public record Conflict(String element, String reason) {
	}

	private final List<Conflict> conflicts;

	public PackageConflictException(List<Conflict> conflicts) {
		super(conflicts.size() + " conflict(s) with existing metamodel content");
		this.conflicts = List.copyOf(conflicts);
	}

	public List<Conflict> getConflicts() {
		return this.conflicts;
	}

}
