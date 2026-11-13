package io.oea.config.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Client address behind a trusted reverse proxy (AA-01; design D1 and D2 of
 * s11-review-hardening): {@code server.forward-headers-strategy=native} installs Tomcat's
 * {@code RemoteIpValve}; this configuration applies the operator's list of trusted proxies.
 */
@Configuration(proxyBeanMethods = false)
public class TrustedProxiesConfiguration {

	@Bean
	TrustedProxies trustedProxies(@Value("${oea.web.trusted-proxies:}") String configured) {
		return new TrustedProxies(configured);
	}

}
