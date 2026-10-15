package io.oea.app.bootstrapping;

/**
 * Input of the local bootstrapping. {@link #toString()} never reveals token or password.
 *
 * @param setupToken one-time setup token from the container file
 * @param loginName login name of the system admin account
 * @param password password, 12–128 characters (REQ-169)
 * @param instanceSlug instance slug for external URNs (ADR-001)
 */
public record BootstrapCommand(String setupToken, String loginName, String password, String instanceSlug) {

	@Override
	public String toString() {
		return "BootstrapCommand[loginName=" + this.loginName + ", instanceSlug=" + this.instanceSlug + "]";
	}

}
