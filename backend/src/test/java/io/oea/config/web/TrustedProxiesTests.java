package io.oea.config.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.oea.infrastructure.guards.StartupGuardException;
import org.apache.catalina.valves.RemoteIpValve;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.tomcat.RemoteIp;
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

	@Test
	void listKeepsBootsHeadersTrustedProxiesAndUsage() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		RemoteIp.Header header = new RemoteIp.Header(null, null, "X-Forwarded-For", "X-Forwarded-Proto");
		factory.setRemoteIp(new RemoteIp(header, new RemoteIp.Proxies("10\\.0\\.0\\.1", "198\\.51\\.100\\.7"),
				RemoteIp.Usage.ALWAYS));
		new TrustedProxies("192.0.2.0/24").customize(factory);
		RemoteIp applied = factory.getRemoteIp();
		assertThat(applied.header()).isEqualTo(header);
		assertThat(applied.proxies().internal()).isEqualTo("192.0.2.0/24");
		assertThat(applied.proxies().trusted()).isEqualTo("198\\.51\\.100\\.7");
		assertThat(applied.usage()).isEqualTo(RemoteIp.Usage.ALWAYS);
	}

	@Test
	void listWithoutBootSettingsDoesNotInstallTheValveByItself() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		factory.setRemoteIp(null);
		new TrustedProxies("192.0.2.0/24").customize(factory);
		assertThat(factory.getRemoteIp().usage()).isEqualTo(RemoteIp.Usage.FOR_FORWARDED_REQUEST_HEADERS);
		assertThat(factory.getRemoteIp().proxies().internal()).isEqualTo("192.0.2.0/24");
	}

	@Test
	void blankLeavesBootsSettingsUntouched() {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		RemoteIp boot = new RemoteIp(null, null);
		factory.setRemoteIp(boot);
		new TrustedProxies(" ").customize(factory);
		assertThat(factory.getRemoteIp()).isSameAs(boot);
	}

	/** The internal proxies of the valve that Boot builds from the factory's settings. */
	private static String applied(String configured) {
		TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
		factory.setRemoteIp(RemoteIp.DEFAULTS);
		new TrustedProxies(configured).customize(factory);
		RemoteIpValve valve = new RemoteIpValve();
		RemoteIp remoteIp = factory.getRemoteIp();
		if (remoteIp.proxies() != null && remoteIp.proxies().internal() != null) {
			valve.setInternalProxies(remoteIp.proxies().internal());
		}
		return valve.getInternalProxies();
	}

}
