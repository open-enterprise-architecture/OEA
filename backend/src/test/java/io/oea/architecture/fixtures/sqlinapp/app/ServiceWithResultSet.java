package io.oea.architecture.fixtures.sqlinapp.app;

import java.sql.ResultSet;
import java.sql.SQLException;

/** Fixture: an application service that maps JDBC rows itself (forbidden by ADR-051). */
public class ServiceWithResultSet {

	public String name(ResultSet rs) throws SQLException {
		return rs.getString(1);
	}

}
