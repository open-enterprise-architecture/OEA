package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Migration db/migration/V22__catalog_query_indexes.sql (design D7 of
 * s9-catalog-query-performance): the index of the default order and the plans of the default
 * first page and of a page reached through the cursor.
 */
class CatalogQueryIndexSchemaTests {

	private static final String PAGE = """
			SELECT e.id, e.name FROM entities e
			WHERE e.metatype_id = '%s' AND e.deleted_at IS NULL%s
			ORDER BY lower(e.name) ASC, e.id ASC LIMIT 50""";

	private static OeaDatabase postgres;

	private static String metatypeId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			long personId;
			try (ResultSet rs = statement.executeQuery(
					"INSERT INTO persons (name, email) VALUES ('Kurt', 'kurt@example.org') RETURNING id")) {
				rs.next();
				personId = rs.getLong(1);
			}
			for (String name : List.of("Application", "Interface", "Node")) {
				try (ResultSet rs = statement.executeQuery("""
						INSERT INTO metatypes (config_id, name) VALUES ('00000000-0000-0000-0000-000000000001', '%s')
						RETURNING id::text""".formatted(name))) {
					rs.next();
					if (metatypeId == null) {
						metatypeId = rs.getString(1);
					}
				}
			}
			statement.execute("""
					INSERT INTO entities (metatype_id, name, created_by, deleted_at)
					SELECT m.id, m.name || ' ' || g, %d, CASE WHEN g %% 10 = 0 THEN now() END
					FROM metatypes m CROSS JOIN generate_series(1, 5000) g
					WHERE m.config_id = '00000000-0000-0000-0000-000000000001'""".formatted(personId));
			statement.execute("ANALYZE entities");
		}
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void indexOfTheDefaultOrder() throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(
						"SELECT indexdef FROM pg_indexes WHERE tablename = 'entities' AND indexname = 'entities_active_name'")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getString(1)).contains("(metatype_id, lower(name), id)").contains("WHERE (deleted_at IS NULL)");
		}
	}

	@Test
	void defaultFirstPageReadsTheIndex() throws SQLException {
		assertThat(plan(PAGE.formatted(metatypeId, ""))).contains("Index Scan using entities_active_name")
			.doesNotContain("Sort");
	}

	@Test
	void pageThroughTheCursorSeeksInTheIndex() throws SQLException {
		String plan = plan(
				PAGE.formatted(metatypeId, " AND (lower(e.name), e.id) > ('application 2500'::text, 2500::integer)"));
		assertThat(plan).contains("Index Scan using entities_active_name").doesNotContain("Sort");
		assertThat(plan).containsPattern("Index Cond: .*ROW\\(lower\\(name\\), id\\) >");
	}

	@Test
	void anotherOrderDoesNotUseIt() throws SQLException {
		// a sort that is not the default order still sorts: the index is no general speed-up
		String plan = plan("""
				SELECT e.id FROM entities e WHERE e.metatype_id = '%s' AND e.deleted_at IS NULL
				ORDER BY e.created_at DESC, e.id LIMIT 50""".formatted(metatypeId));
		assertThat(plan).contains("Sort");
	}

	/** The plan of the statement, one line per node. */
	private static String plan(String sql) throws SQLException {
		List<String> lines = new ArrayList<>();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery("EXPLAIN " + sql)) {
			while (rs.next()) {
				lines.add(rs.getString(1));
			}
		}
		return String.join("\n", lines);
	}

}
