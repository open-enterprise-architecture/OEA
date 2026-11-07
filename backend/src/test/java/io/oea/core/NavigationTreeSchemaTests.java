package io.oea.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.oea.testsupport.containers.OeaDatabase;
import io.oea.testsupport.containers.OeaDatabases;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Migration db/migration/V21__navigation_tree.sql (us-054-catalog-navigation-tree D1): the single
 * root, the folder names unique among siblings regardless of case, one entry per target and
 * folder, the cascade on deleting a folder and the protected root.
 */
class NavigationTreeSchemaTests {

	private static OeaDatabase postgres;

	private static long personId;

	private static String rootId;

	@BeforeAll
	static void start() throws SQLException {
		postgres = OeaDatabases.freshMigrated();
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement()) {
			try (ResultSet rs = statement.executeQuery(
					"INSERT INTO persons (name, email) VALUES ('Kurt', 'kurt@example.org') RETURNING id")) {
				rs.next();
				personId = rs.getLong(1);
			}
		}
		rootId = single("SELECT id::text FROM tree_nodes WHERE parent_id IS NULL");
	}

	@AfterAll
	static void stop() {
		postgres.stop();
	}

	@Test
	void theMigrationCreatesExactlyOneRoot() throws SQLException {
		assertThat(single("SELECT count(*)::text FROM tree_nodes WHERE parent_id IS NULL")).isEqualTo("1");
		assertThat(single("SELECT name || ':' || (created_by IS NULL) FROM tree_nodes WHERE parent_id IS NULL"))
			.isEqualTo("Repository:true");
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("INSERT INTO tree_nodes (name) VALUES ('Second Root')"))
				.withMessageContaining("tree_nodes_single_root");
		}
	}

	@Test
	void aFolderBelowTheRootNeedsACreator() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate(
						"INSERT INTO tree_nodes (name, parent_id) VALUES ('Anonymous', '" + rootId + "')"))
				.withMessageContaining("tree_nodes_creator");
		}
	}

	@Test
	void folderNamesAreUniqueAmongSiblingsRegardlessOfCase() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String landscape = folder(statement, "IT Landscape", rootId);
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> folder(statement, "it landscape", rootId))
				.withMessageContaining("tree_nodes_sibling_name");
			// the same name under another parent is allowed
			folder(statement, "IT Landscape", landscape);
		}
	}

	@Test
	void namesAreStoredTrimmedAndNotBlank() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> folder(statement, " Padded ", rootId))
				.withMessageContaining("tree_nodes_name_check");
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> folder(statement, "", rootId))
				.withMessageContaining("tree_nodes_name_check");
		}
	}

	@Test
	void aTargetIsPlacedOncePerFolder() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String compliance = folder(statement, "Compliance", rootId);
			String audit = folder(statement, "Audit", rootId);
			UUID catalog = UUID.randomUUID();
			item(statement, compliance, catalog);
			item(statement, audit, catalog);
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> item(statement, compliance, catalog))
				.withMessageContaining("tree_node_items_once_per_folder");
		}
	}

	@Test
	void deletingAFolderDeletesItsSubtreeWithTheEntries() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			String parent = folder(statement, "Deleted Parent", rootId);
			String child = folder(statement, "Deleted Child", parent);
			item(statement, child, UUID.randomUUID());
			statement.executeUpdate("DELETE FROM tree_nodes WHERE id = '" + parent + "'");
			assertThat(single("SELECT count(*)::text FROM tree_nodes WHERE id IN ('" + parent + "', '" + child + "')"))
				.isEqualTo("0");
			assertThat(single("SELECT count(*)::text FROM tree_node_items WHERE tree_node_id = '" + child + "'"))
				.isEqualTo("0");
		}
	}

	@Test
	void theRootCannotBeDeleted() throws SQLException {
		try (Connection app = OeaDatabases.asRole(postgres, "oea_app"); Statement statement = app.createStatement()) {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> statement.executeUpdate("DELETE FROM tree_nodes WHERE id = '" + rootId + "'"))
				.withMessageContaining("the root folder cannot be deleted");
		}
	}

	private static String folder(Statement statement, String name, String parentId) throws SQLException {
		try (ResultSet rs = statement.executeQuery("INSERT INTO tree_nodes (name, parent_id, created_by) VALUES ('"
				+ name + "', '" + parentId + "', " + personId + ") RETURNING id::text")) {
			rs.next();
			return rs.getString(1);
		}
	}

	private static void item(Statement statement, String nodeId, UUID referenceId) throws SQLException {
		statement.executeUpdate("INSERT INTO tree_node_items (tree_node_id, item_type, reference_id, created_by) VALUES ('"
				+ nodeId + "', 'catalog', '" + referenceId + "', " + personId + ")");
	}

	private static String single(String sql) throws SQLException {
		try (Connection su = OeaDatabases.asSuperuser(postgres); Statement statement = su.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			rs.next();
			return rs.getString(1);
		}
	}

}
