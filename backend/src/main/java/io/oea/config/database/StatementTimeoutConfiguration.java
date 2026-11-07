package io.oea.config.database;

import com.zaxxer.hikari.HikariDataSource;
import io.oea.app.catalog.CatalogQueryProperties;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * The statement time limits (design D4 of s9-catalog-query-performance): every connection of the
 * application's pool starts with {@code SET statement_timeout}, and the catalog query limit stays
 * within it. The migration step opens its own Flyway connections, so the limit does not apply to
 * it.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DatabaseProperties.class)
public class StatementTimeoutConfiguration {

	StatementTimeoutConfiguration(DatabaseProperties database, CatalogQueryProperties catalog) {
		if (catalog.queryTimeout().compareTo(database.statementTimeout()) > 0) {
			throw new IllegalStateException(
					"oea.catalog.query-timeout must not exceed oea.database.statement-timeout");
		}
	}

	@Bean
	static BeanPostProcessor statementTimeoutOnConnections() {
		return new StatementTimeoutPostProcessor();
	}

	/**
	 * Sets the limit as connection init SQL of the Hikari pool, before the pool opens its first
	 * connection. The value is validated and rendered as a number of milliseconds, never as the
	 * configured text.
	 */
	static final class StatementTimeoutPostProcessor implements BeanPostProcessor, EnvironmentAware {

		private Environment environment;

		@Override
		public void setEnvironment(Environment environment) {
			this.environment = environment;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			if (bean instanceof HikariDataSource pool) {
				DatabaseProperties properties = Binder.get(this.environment)
					.bindOrCreate("oea.database", DatabaseProperties.class);
				pool.setConnectionInitSql(initSql(pool.getConnectionInitSql(), properties));
			}
			return bean;
		}

		static String initSql(String configured, DatabaseProperties properties) {
			String limit = "SET statement_timeout = " + properties.statementTimeout().toMillis();
			return (configured == null || configured.isBlank()) ? limit : configured + "; " + limit;
		}

	}

}
