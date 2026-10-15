package io.oea;

import io.oea.testsupport.containers.OeaApplications;
import io.oea.testsupport.containers.OeaTestDatabase;
import io.oea.testsupport.containers.OeaTestKeys;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/** Connects the application under test to the provisioned and migrated test database. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	DynamicPropertyRegistrar oeaDatabaseProperties() {
		return (registry) -> {
			OeaTestDatabase.registerRuntimeDataSource(registry);
			registry.add("oea.bootstrap.setup-token-file", OeaApplications::sharedSetupTokenFile);
			registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
		};
	}

}
