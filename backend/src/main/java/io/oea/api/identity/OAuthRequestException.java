package io.oea.api.identity;

import org.springframework.http.HttpStatus;

/**
 * A request to the token endpoint answered with an RFC 6749 §5.2 error instead of a problem
 * document (design D3 of s12-client-credentials-token).
 */
public class OAuthRequestException extends RuntimeException {

	private final HttpStatus status;

	private final String error;

	public OAuthRequestException(HttpStatus status, String error) {
		super(error);
		this.status = status;
		this.error = error;
	}

	public HttpStatus getStatus() {
		return this.status;
	}

	public String getError() {
		return this.error;
	}

}
