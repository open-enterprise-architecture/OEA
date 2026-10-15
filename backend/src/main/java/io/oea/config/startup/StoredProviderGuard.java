package io.oea.config.startup;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Issuer guards of ADR-036 §6 on the providers stored through the admin API, the only source
 * of the OIDC login (change us-001-oidc-login, design D4). Runs while the context starts,
 * after the schema checks and before the web server accepts connections. Outside the
 * profile {@code dev}: enabled providers need an {@code https} issuer that is not a loopback
 * address (G2, G3), and {@code oea.web.public-url} must be set. With {@code dev}: every stored
 * issuer must be a loopback address (REQ-183 AC5). G1 holds by construction: signature
 * verification is not configurable.
 */
@Component
@DependsOn("databaseStartupChecks")
public class StoredProviderGuard implements InitializingBean {

	static final String PUBLIC_URL = "oea.web.public-url";

	private final DataSource dataSource;

	private final Environment environment;

	public StoredProviderGuard(DataSource dataSource, Environment environment) {
		this.dataSource = dataSource;
		this.environment = environment;
	}

	record StoredProvider(String id, String name, String issuer, boolean active) {

		String label() {
			return "auth provider '" + this.name + "' (" + this.id + ")";
		}

	}

	@Override
	public void afterPropertiesSet() {
		List<StoredProvider> providers = new JdbcTemplate(this.dataSource).query(
				"SELECT id::text, name, issuer, active FROM auth_providers ORDER BY name, id",
				(rs, n) -> new StoredProvider(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)));
		List<String> violations = check(providers, this.environment.acceptsProfiles(Profiles.of("dev")),
				this.environment.getProperty(PUBLIC_URL));
		if (!violations.isEmpty()) {
			throw new StartupGuardException(violations);
		}
	}

	static List<String> check(List<StoredProvider> providers, boolean development, String publicUrl) {
		List<String> violations = new ArrayList<>();
		for (StoredProvider provider : providers) {
			if (development) {
				if (!LoopbackAddresses.isLoopbackUrl(provider.issuer())) {
					violations.add(provider.label() + ": issuer " + provider.issuer()
							+ " is not a loopback address, which the profile 'dev' requires (REQ-183 AC5)");
				}
			}
			else if (provider.active()) {
				if (!"https".equals(scheme(provider.issuer()))) {
					violations.add("G3: " + provider.label() + ": issuer " + provider.issuer() + " does not use https");
				}
				if (LoopbackAddresses.isLoopbackUrl(provider.issuer())) {
					violations.add("G2: " + provider.label() + ": issuer " + provider.issuer()
							+ " is a loopback address");
				}
			}
		}
		boolean anyEnabled = providers.stream().anyMatch(StoredProvider::active);
		if (anyEnabled && (publicUrl == null || publicUrl.isBlank())) {
			violations.add(PUBLIC_URL + " is not set; the OIDC login needs it for the redirect URI");
		}
		return violations;
	}

	private static String scheme(String url) {
		try {
			String scheme = URI.create(url.strip()).getScheme();
			return (scheme != null) ? scheme.toLowerCase(Locale.ROOT) : "";
		}
		catch (IllegalArgumentException ex) {
			return "";
		}
	}

}
