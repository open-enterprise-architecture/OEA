package io.oea.app.access;

import io.oea.core.access.AdministrationPathStore;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Usable paths to {@code system-admin} (ADR-032 SoD-7; design D3 of us-019-admin-safeguards, D6 of
 * us-144-local-role-assignments): the local system admin account unless deactivated, IdP group
 * mappings that have granted the role in a login, and open-ended assignments of active persons.
 * Every change that can remove a path checks the remaining paths under one advisory lock, so
 * concurrent changes cannot both remove the last two paths.
 */
@Service
public class AdministrationPaths {

	/** Key of the transaction-scoped advisory lock that serializes SoD-7 checks. */
	private static final long LOCK_KEY = 0x0EA5_0D07L;

	private final AdministrationPathStore store;

	public AdministrationPaths(AdministrationPathStore store) {
		this.store = store;
	}

	/** The path a change removes; {@code null} fields remove nothing of that kind. */
	public record Removal(boolean removesLocalAccount, UUID mappingId, UUID assignmentId, Long personId) {

		public static Removal localAccount() {
			return new Removal(true, null, null, null);
		}

		public static Removal mapping(UUID mappingId) {
			return new Removal(false, mappingId, null, null);
		}

		public static Removal assignment(UUID assignmentId) {
			return new Removal(false, null, assignmentId, null);
		}

		/** All assignments of a person who leaves {@code active} (us-148-person-lifecycle). */
		public static Removal person(long personId) {
			return new Removal(false, null, null, personId);
		}

	}

	/**
	 * Locks the SoD-7 check for the current transaction and rejects the change if no usable path
	 * would remain.
	 * @throws LastAdminPathException if the removal leaves no usable path
	 */
	public void requireRemainingPath(Removal removal) {
		this.store.lockPaths(LOCK_KEY);
		if (remainingPaths(removal) == 0) {
			throw new LastAdminPathException();
		}
	}

	/** Whether the mapping currently counts as a usable path. */
	public boolean isUsableMapping(UUID mappingId) {
		return this.store.isUsableMapping(mappingId);
	}

	/** Whether the person holds a usable path: an open-ended, valid {@code system-admin} assignment while active. */
	public boolean isPersonPath(long personId) {
		return this.store.isPersonPath(personId);
	}

	private int remainingPaths(Removal removal) {
		int local = removal.removesLocalAccount() ? 0 : this.store.localAccountPaths();
		int mappings = this.store.mappingPaths(removal.mappingId());
		int persons = this.store.personPaths(removal.assignmentId(), removal.personId());
		return local + mappings + persons;
	}

}
