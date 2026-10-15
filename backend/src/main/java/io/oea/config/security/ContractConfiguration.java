package io.oea.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The authorization rules of the packaged contract, for every application context. */
@Configuration(proxyBeanMethods = false)
public class ContractConfiguration {

	@Bean
	ContractAuthorizationRules contractAuthorizationRules() {
		return ContractAuthorizationRules.load();
	}

}
