package io.oea.architecture.fixtures.testonlyauth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Violation: a security bean that exists only in one profile. */
@Configuration(proxyBeanMethods = false)
public class ProfileConditionalSecurityConfiguration {

	@Bean
	@Profile("dev")
	JwtDecoder devOnlyDecoder() {
		return new JwtDecoder() {
		};
	}

}
