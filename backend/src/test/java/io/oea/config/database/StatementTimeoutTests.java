package io.oea.config.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.zaxxer.hikari.HikariDataSource;
import io.oea.app.catalog.CatalogQueryProperties;
import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import io.oea.testsupport.containers.OeaPostgres;
import io.oea.testsupport.containers.OeaTestKeys;
import java.nio.file.Files;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Spec platform/request-limits, requirement "Statement time limit", and the configuration of the
 * catalog query time limit (design D4 of s9-catalog-query-performance).
 */
@SpringBootTest
class StatementTimeoutTests {

	private static final OeaDatabase postgres = OeaDatabases.freshMigrated();

	@DynamicPropertySource
	static void database(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", () -> "oea_app");
		registry.add("spring.datasource.password", () -> OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		registry.add("oea.bootstrap.setup-token-file", () -> tempDirectory() + "/setup-token");
		registry.add("oea.security.kek.current", OeaTestKeys::sharedKeyFile);
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Autowired
	private JdbcTemplate jdbc;

	private final ApplicationContextRunner limits = new ApplicationContextRunner()
		.withUserConfiguration(CatalogProperties.class, StatementTimeoutConfiguration.class);

	// Scenario: Default limit on every connection

	@Test
	void defaultLimitOnEveryConnection() {
		assertThat(this.jdbc.queryForObject("SHOW statement_timeout", String.class)).isEqualTo("1min");
	}

	// Scenario: Configured limit

	@Test
	void configuredLimit() {
		try (HikariDataSource pool = pool()) {
			StatementTimeoutConfiguration.StatementTimeoutPostProcessor processor = processor("30s");
			processor.postProcessBeforeInitialization(pool, "dataSource");
			assertThat(new JdbcTemplate(pool).queryForObject("SHOW statement_timeout", String.class)).isEqualTo("30s");
			assertThat(pool.getConnectionInitSql()).isEqualTo("SET statement_timeout = 30000");
		}
	}

	@Test
	void configuredInitSqlIsKept() {
		assertThat(StatementTimeoutConfiguration.StatementTimeoutPostProcessor.initSql("SET search_path = public",
				new DatabaseProperties(Duration.ofSeconds(5))))
			.isEqualTo("SET search_path = public; SET statement_timeout = 5000");
	}

	// Boundaries: 1 second to 1 hour

	@ParameterizedTest
	@ValueSource(strings = { "1s", "1000ms", "60s", "1h", "3600s" })
	void statementLimitWithinItsRangeStarts(String limit) {
		this.limits.withPropertyValues("oea.database.statement-timeout=" + limit, "oea.catalog.query-timeout=1s")
			.run((context) -> assertThat(context).hasNotFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = { "999ms", "3601s", "0s", "-1s", "soon" })
	void statementLimitOutsideItsRangeRefusesTheStart(String limit) {
		this.limits.withPropertyValues("oea.database.statement-timeout=" + limit, "oea.catalog.query-timeout=1s")
			.run((context) -> assertThat(context).hasFailed());
	}

	@Test
	void statementLimitRecord() {
		assertThat(new DatabaseProperties(Duration.ofSeconds(1)).statementTimeout()).hasSeconds(1);
		assertThat(new DatabaseProperties(Duration.ofHours(1)).statementTimeout()).hasHours(1);
		assertThatIllegalArgumentException().isThrownBy(() -> new DatabaseProperties(Duration.ofMillis(999)));
		assertThatIllegalArgumentException().isThrownBy(() -> new DatabaseProperties(Duration.ofSeconds(3601)));
	}

	// Boundaries of the catalog query limit: 1 second to the statement limit

	@ParameterizedTest
	@ValueSource(strings = { "1s", "30s", "60s" })
	void catalogLimitUpToTheStatementLimitStarts(String limit) {
		this.limits.withPropertyValues("oea.catalog.query-timeout=" + limit)
			.run((context) -> assertThat(context).hasNotFailed());
	}

	@ParameterizedTest
	@ValueSource(strings = { "999ms", "61s", "60001ms" })
	void catalogLimitOutsideItsRangeRefusesTheStart(String limit) {
		this.limits.withPropertyValues("oea.catalog.query-timeout=" + limit)
			.run((context) -> assertThat(context).hasFailed());
	}

	@Test
	void catalogLimitEqualToAConfiguredStatementLimitStarts() {
		this.limits.withPropertyValues("oea.database.statement-timeout=20s", "oea.catalog.query-timeout=20s")
			.run((context) -> assertThat(context).hasNotFailed());
		this.limits.withPropertyValues("oea.database.statement-timeout=20s", "oea.catalog.query-timeout=21s")
			.run((context) -> assertThat(context).hasFailed());
	}

	/** Registers the catalog limit for the context runner; not a configuration of the test context. */
	@EnableConfigurationProperties(CatalogQueryProperties.class)
	static class CatalogProperties {

	}

	private static StatementTimeoutConfiguration.StatementTimeoutPostProcessor processor(String limit) {
		StatementTimeoutConfiguration.StatementTimeoutPostProcessor processor = new StatementTimeoutConfiguration.StatementTimeoutPostProcessor();
		processor.setEnvironment(
				new org.springframework.mock.env.MockEnvironment().withProperty("oea.database.statement-timeout", limit));
		return processor;
	}

	private static HikariDataSource pool() {
		HikariDataSource pool = new HikariDataSource();
		pool.setJdbcUrl(postgres.getJdbcUrl());
		pool.setUsername("oea_app");
		pool.setPassword(OeaPostgres.ROLE_PASSWORDS.get("oea_app"));
		pool.setMaximumPoolSize(1);
		return pool;
	}

	private static String tempDirectory() {
		try {
			return Files.createTempDirectory("oea-statement-timeout").toString();
		}
		catch (java.io.IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
