package io.oea.config.oidc;

/** The provider could not be reached or answered with a server error; a retry may succeed. */
public class OidcProviderUnavailableException extends RuntimeException {

	public OidcProviderUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
