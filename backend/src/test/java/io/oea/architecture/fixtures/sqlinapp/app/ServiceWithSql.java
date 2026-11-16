package io.oea.architecture.fixtures.sqlinapp.app;

import org.springframework.jdbc.core.JdbcTemplate;

/** Fixture: an application service that runs SQL itself (forbidden by ADR-051). */
public class ServiceWithSql {

	private final JdbcTemplate jdbc;

	public ServiceWithSql(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Integer count() {
		return this.jdbc.queryForObject("SELECT 1", Integer.class);
	}

}
