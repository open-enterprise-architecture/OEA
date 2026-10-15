package io.oea.config.oidc;

/** The provider rejected a token request, e.g. {@code invalid_grant} for an expired refresh token. */
public class OidcTokenRejectedException extends RuntimeException {

	private final String errorCode;

	public OidcTokenRejectedException(String errorCode, Throwable cause) {
		super("token request rejected: " + errorCode, cause);
		this.errorCode = errorCode;
	}

	public String getErrorCode() {
		return this.errorCode;
	}

}
