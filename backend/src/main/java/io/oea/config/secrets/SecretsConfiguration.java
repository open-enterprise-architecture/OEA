package io.oea.config.secrets;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(KekProperties.class)
public class SecretsConfiguration {

	@Bean
	KeyRing keyRing(KekProperties properties) {
		return KeyRing.load(properties);
	}

	@Bean
	SecretCipher secretCipher(KeyRing keyRing) {
		return new SecretCipher(keyRing);
	}

}
