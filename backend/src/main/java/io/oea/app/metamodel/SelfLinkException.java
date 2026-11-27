package io.oea.app.metamodel;

/**
 * SoD-1 (ADR-032) for the account mapping: the change would give the own account of the acting
 * principal a link to an element of the person metatype (design D5 of us-075-lane-assignments). The
 * request is rolled back as a whole; the denial is audited afterwards, outside the rolled-back
 * transaction.
 */
public class SelfLinkException extends RuntimeException {

	private final long personId;

	public SelfLinkException(long personId) {
		super("self-administration");
		this.personId = personId;
	}

	/** The own account. */
	public long getPersonId() {
		return this.personId;
	}

}
