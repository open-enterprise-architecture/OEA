package io.oea.config.oidc;

import java.util.Set;
import java.util.UUID;

/**
 * Settings of one stored provider as needed by the client.
 *
 * @param providerId stored provider
 * @param issuer exact issuer; discovery and the {@code iss} claim must match it
 * @param clientId client ID; the {@code aud} claim must contain it
 * @param clientSecret decrypted client secret, or {@code null} for a public client
 * @param scopes requested scopes, including {@code openid}
 */
public record OidcClientSettings(UUID providerId, String issuer, String clientId, String clientSecret,
		Set<String> scopes) {

	@Override
	public String toString() {
		// never print the client secret
		return "OidcClientSettings[providerId=" + this.providerId + ", issuer=" + this.issuer + ", clientId="
				+ this.clientId + "]";
	}

}
