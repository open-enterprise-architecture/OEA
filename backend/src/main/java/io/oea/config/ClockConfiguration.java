package io.oea.config;

import java.time.Clock;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Injectable clock (tests replace it) and scanning of configuration property records. */
@Configuration(proxyBeanMethods = false)
@ConfigurationPropertiesScan("io.oea.app")
public class ClockConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
