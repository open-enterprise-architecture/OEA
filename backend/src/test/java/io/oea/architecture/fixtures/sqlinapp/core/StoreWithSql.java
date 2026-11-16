package io.oea.architecture.fixtures.sqlinapp.core;

import org.springframework.jdbc.core.JdbcTemplate;

/** Fixture: a core store may run SQL (ADR-051). */
public class StoreWithSql {

	private final JdbcTemplate jdbc;

	public StoreWithSql(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Integer count() {
		return this.jdbc.queryForObject("SELECT 1", Integer.class);
	}

}
