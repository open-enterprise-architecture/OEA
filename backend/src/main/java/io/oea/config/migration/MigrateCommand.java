package io.oea.config.migration;

import io.oea.config.startup.ConfigurationGuards;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one-shot migration step {@code oea migrate} (design D1): a non-web context without
 * auto-configuration that runs Flyway for all migration sets and returns an exit code.
 */
public final class MigrateCommand {

	private static final Logger logger = LoggerFactory.getLogger(MigrateCommand.class);

	private MigrateCommand() {
	}

	/** Returns 0 if all migration sets were applied, 1 otherwise. */
	public static int run(String... args) {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(MigrationConfiguration.class)
			.web(WebApplicationType.NONE)
			.properties(ConfigurationGuards.MODE_PROPERTY + "=migrate")
			.bannerMode(Banner.Mode.OFF)
			.logStartupInfo(false)
			.run(args)) {
			context.getBean(Migrator.class).migrateAll();
			logger.info("Migration step completed");
			return 0;
		}
		catch (RuntimeException ex) {
			logger.error("Migration step failed: {}", ex.getMessage());
			return 1;
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(MigrationProperties.class)
	static class MigrationConfiguration {

		@Bean
		Migrator migrator(MigrationProperties properties) {
			return new Migrator(properties);
		}

	}

}
