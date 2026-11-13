package io.oea.config.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oea.infrastructure.guards.StartupGuardException;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

/**
 * Requirement "Trusted proxies configurable" of platform/deployment (design D2 of
 * s11-review-hardening): parsing of {@code OEA_TRUSTED_PROXIES} and its effect on the valve.
 */
class TrustedProxiesTests {

	private static final String TOMCAT_DEFAULT = new RemoteIpValve().getInternalProxies();

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "", "   " })
	void blankKeepsTheDefault(String configured) {
		assertThat(TrustedProxies.parse(configured)).isEmpty();
		assertThat(applied(configured)).isEqualTo(TOMCAT_DEFAULT);
		assertThat(TOMCAT_DEFAULT).contains("172.16.0.0/12", "127.0.0.0/8", "10.0.0.0/8", "192.168.0.0/16");
	}

	@Test
	void oneValidRange() {
		assertThat(applied("192.0.2.0/24")).isEqualTo("192.0.2.0/24");
	}

	@Test
	void twoValidRanges() {
		assertThat(TrustedProxies.parse(" 192.0.2.0/24 , 2001:db8::/32 ")).contains("192.0.2.0/24, 2001:db8::/32");
		// Tomcat may reorder the ranges
		assertThat(applied(" 192.0.2.0/24 , 2001:db8::/32 ").split(", ")).containsExactlyInAnyOrder("192.0.2.0/24",
				"2001:db8::/32");
	}

	@ParameterizedTest
	@ValueSource(strings = { "192.0.2.0/32", "192.0.2.7", "2001:db8::1/128", "0.0.0.0/0" })
	void lastValidPrefixLengthsAndSingleAddresses(String configured) {
		assertThat(applied(configured)).isEqualTo(configured);
	}

	@ParameterizedTest
	@ValueSource(strings = { "192.0.2.0/33", "2001:db8::1/129", "not-a-range", "proxy.example.org", "192.0.2.0/",
			"192.0.2.0/-1", "192.0.2.0/24,", "192.0.2.300/24" })
	void invalidValuesStopTheStart(String configured) {
		assertThatThrownBy(() -> new TrustedProxies(configured)).isInstanceOf(StartupGuardException.class)
			.hasMessageContaining("OEA_TRUSTED_PROXIES");
	}

	@Test
	void violationNamesOnlyTheInvalidEntries() {
		assertThatThrownBy(() -> new TrustedProxies("192.0.2.0/24, not-a-range"))
			.isInstanceOfSatisfying(StartupGuardException.class,
					(ex) -> assertThat(ex.getViolations()).singleElement()
						.asString()
						.contains("[not-a-range]")
						.doesNotContain("192.0.2.0/24"));
	}

	private static String applied(String configured) {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		RemoteIpValve valve = new RemoteIpValve();
		factory.addEngineValves(valve);
		new TrustedProxies(configured).customize(factory);
		return valve.getInternalProxies();
	}

}
