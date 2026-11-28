package io.oea.app.security;

import java.util.Optional;

/**
 * The principal of the request that runs the current code, for rules that must see the actor of a
 * change that happens deep in a transaction (SoD-1 of the account mapping, design D5 of
 * us-075-lane-assignments). Implemented by the security configuration; empty outside a request
 * and during a login.
 */
@FunctionalInterface
public interface ActingPrincipal {

	/** The authenticated principal of the current request, if any. */
	Optional<OeaPrincipal> current();

}
