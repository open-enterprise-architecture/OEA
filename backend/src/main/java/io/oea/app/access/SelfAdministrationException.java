package io.oea.app.access;

/** SoD-1 (ADR-032): a person may not map a group of the own current groups claim (US-147 AC5). */
public class SelfAdministrationException extends RuntimeException {

	public SelfAdministrationException() {
		super("self-administration");
	}

}
