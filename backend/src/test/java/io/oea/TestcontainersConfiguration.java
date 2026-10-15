package io.oea;

import io.oea.testsupport.containers.OeaTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/** Connects the application under test to the provisioned and migrated test database. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	DynamicPropertyRegistrar oeaDatabaseProperties() {
		return OeaTestDatabase::registerRuntimeDataSource;
	}

}
