package io.oea.config.oidc;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OIDC client settings (design D13 of us-001-oidc-login).
 *
 * @param discoveryLocations where the discovery document of an issuer is fetched when the
 * backend reaches the IdP under another address than browsers (split-horizon networks)
 */
@ConfigurationProperties("oea.security.oidc")
public record OidcProperties(List<DiscoveryLocation> discoveryLocations) {

	public OidcProperties {
		discoveryLocations = (discoveryLocations != null) ? List.copyOf(discoveryLocations) : List.of();
	}

	/**
	 * @param issuer stored issuer, as browsers and the {@code iss} claim see it
	 * @param location base URL of the issuer as reachable from the backend
	 */
	public record DiscoveryLocation(String issuer, String location) {
	}

}
