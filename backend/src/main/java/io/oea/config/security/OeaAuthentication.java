package io.oea.config.security;

import io.oea.app.security.OeaPrincipal;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;

/** Spring Security view of an OEA session; carries no authorities (permissions per request). */
public class OeaAuthentication extends AbstractAuthenticationToken {

	private final OeaPrincipal principal;

	private final String sessionToken;

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
