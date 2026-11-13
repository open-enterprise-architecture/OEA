package io.oea.config.web;

import io.oea.infrastructure.guards.StartupGuardException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.catalina.Valve;
import org.apache.catalina.valves.RemoteIpValve;
import org.springframework.boot.tomcat.TomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.core.Ordered;

/**
 * Trusted reverse proxies of the {@link RemoteIpValve} (design D2 of s11-review-hardening, AA-01).
 * A blank {@code oea.web.trusted-proxies} keeps Tomcat's default list (loopback and the private
 * networks); a non-blank one replaces it. An invalid list stops the start.
 */
public class TrustedProxies implements WebServerFactoryCustomizer<TomcatWebServerFactory>, Ordered {

	/** Environment variable through which the operator sets the list. */
	static final String VARIABLE = "OEA_TRUSTED_PROXIES";

	private final Optional<String> proxies;

	public TrustedProxies(String configured) {
		this.proxies = parse(configured);
	}

	/** Runs after Boot's customizer, which installs the valve. */
	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE;
	}

	@Override
	public void customize(TomcatWebServerFactory factory) {
		if (this.proxies.isEmpty()) {
			return;
		}
		for (Valve valve : factory.getEngineValves()) {
			if (valve instanceof RemoteIpValve remoteIp) {
				remoteIp.setInternalProxies(this.proxies.get());
			}
		}
	}

	/** The normalized CIDR list, or empty for a blank value (Tomcat's default). */
	Optional<String> proxies() {
		return this.proxies;
	}

	static Optional<String> parse(String configured) {
		if (configured == null || configured.isBlank()) {
			return Optional.empty();
		}
		List<String> ranges = new ArrayList<>();
		List<String> invalid = new ArrayList<>();
		for (String entry : configured.split(",", -1)) {
			String range = entry.strip();
			if (valid(range)) {
				ranges.add(range);
			}
			else {
				invalid.add(range);
			}
		}
		if (!invalid.isEmpty()) {
			throw new StartupGuardException(List.of(VARIABLE
					+ " must be a comma-separated list of IP addresses or CIDR ranges; invalid: " + invalid));
		}
		return Optional.of(String.join(", ", ranges));
	}

	private static boolean valid(String range) {
		int slash = range.indexOf('/');
		String address = (slash < 0) ? range : range.substring(0, slash);
		InetAddress literal;
		try {
			// a literal only: a host name would be resolved through DNS
			literal = InetAddress.ofLiteral(address);
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
		if (slash < 0) {
			return true;
		}
		String prefix = range.substring(slash + 1);
		if (prefix.isEmpty() || prefix.length() > 3 || !prefix.chars().allMatch(Character::isDigit)) {
			return false;
		}
		int bits = Integer.parseInt(prefix);
		return bits <= ((literal instanceof Inet4Address) ? 32 : 128);
	}

}
