package io.oea.app.login;

/**
 * A refused OIDC login (design D10). The reason is audited only; the browser sees one
 * generic failure (REQ-006).
 */
public class OidcLoginFailedException extends RuntimeException {

	private final String reason;

	public OidcLoginFailedException(String reason) {
		super("OIDC login refused: " + reason);
		this.reason = reason;
	}

	public String getReason() {
		return this.reason;
	}

}
