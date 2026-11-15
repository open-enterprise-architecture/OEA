package io.oea.config.security;

import io.oea.app.security.OeaPrincipal;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Spring Security view of an OEA session or access token; carries no authorities (permissions per
 * request). A bearer authentication has no session token and needs no CSRF token.
 */
public class OeaAuthentication extends AbstractAuthenticationToken {

	private final OeaPrincipal principal;

	private final String sessionToken;

	/** An authentication by an access token (design D5 of s12-client-credentials-token). */
	public static OeaAuthentication bearer(OeaPrincipal principal) {
		return new OeaAuthentication(principal, null);
	}

	/** True for an authentication by an access token instead of the session cookie. */
	public boolean bearer() {
		return this.sessionToken == null;
	}

	public OeaAuthentication(OeaPrincipal principal, String sessionToken) {
		super(List.of());
		this.principal = principal;
		this.sessionToken = sessionToken;
		setAuthenticated(true);
	}

	@Override
	public OeaPrincipal getPrincipal() {
		return this.principal;
	}

	@Override
	public Object getCredentials() {
		return null;
	}

	/** Raw cookie value, needed to derive the CSRF token; never logged. */
	public String sessionToken() {
		return this.sessionToken;
	}

	@Override
	public String toString() {
		return "OeaAuthentication[" + this.principal.type() + ", session " + this.principal.sessionId() + "]";
	}

}
