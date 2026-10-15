package io.oea.app.security;

import io.oea.core.domain.auth.LoginSession;

/** Re-evaluation of a session established through an IdP (design D7, REQ-165 AC5). */
public interface IdpSessionRefresh {

	/**
	 * Re-evaluates the group claims if the last evaluation is 15 minutes old.
	 *
	 * @return {@code false} if the IdP rejected the refresh and the session must end
	 */
	boolean refreshIfDue(LoginSession session);

}
